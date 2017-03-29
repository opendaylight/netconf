/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.communicator.impl.sender;

import static com.ning.http.client.AsyncHttpClientConfigDefaults.defaultConnectTimeout;
import static com.ning.http.client.AsyncHttpClientConfigDefaults.defaultFollowRedirect;
import static com.ning.http.client.AsyncHttpClientConfigDefaults.defaultPooledConnectionIdleTimeout;

import com.google.common.base.Preconditions;
import com.google.common.net.HttpHeaders;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.ListenableFuture;
import com.ning.http.client.Response;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import org.opendaylight.controller.config.util.xml.XmlElement;
import org.opendaylight.controller.config.util.xml.XmlUtil;
import org.opendaylight.restconfsb.communicator.api.http.Sender;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.node.rev160511.RestconfNode;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 *
 */
public class SenderFactory {

    private static final Logger LOG = LoggerFactory.getLogger(SenderFactory.class);
    private static final String DEFAULT_ROOT_RESOURCE = "/restconf";
    private final HttpClientProvider clientProvider;
    private final long reconnectInterval;

    public SenderFactory(final HttpClientProvider clientProvider, final long reconnectInterval) {
        this.clientProvider = clientProvider;
        this.reconnectInterval = reconnectInterval;
    }

    /**
     * Creates new {@link Sender} instance. First, it tries to discover restconf root. Then, restconf root is appended to server address and port.
     * All other requests via this sender are prepended with <i>server:port/restconf-root/</i>.
     *
     * @return sender
     * @throws NodeConnectionException
     */
    public Sender createSender(final Node node, final ScheduledExecutorService reconnectExecutor) throws NodeConnectionException {
        final RestconfNode restconfNode = node.getAugmentation(RestconfNode.class);
        Preconditions.checkNotNull(restconfNode, "Missing Restconf node");
        Preconditions.checkNotNull(restconfNode.getAddress(), "Missing node address");
        Preconditions.checkNotNull(restconfNode.getPort(), "Missing node port");
        try {
            final int requestTimeout = restconfNode.getRequestTimeout();
            final String user = restconfNode.getUsername();
            final String password = restconfNode.getPassword();
            final AsyncHttpClient client = createClient(user, password, requestTimeout);
            final String address = String.valueOf(restconfNode.getAddress().getValue());
            final int port = restconfNode.getPort().getValue();
            final String wellKnown = getProtocol(restconfNode) + address + ":" + port + "/.well-known/host-meta";
            final ListenableFuture<Response> execute = client.prepareGet(wellKnown)
                    .addHeader(HttpHeaders.ACCEPT, "application/xrd+xml")
                    .execute();
            final String rootResource;
            final Response response = execute.get();
            if (response.getStatusCode() == HttpURLConnection.HTTP_OK) {
                rootResource = getRestconfLinkFromResponse(response.getResponseBody());
            } else {
                LOG.warn("Restconf root resource from /.well-known/host-meta not found defaulting to '/restconf'");
                rootResource = DEFAULT_ROOT_RESOURCE;
            }

            final String endpoint = getEndpoint(restconfNode, rootResource);
            final SseClient sseClient = new SseClient(createSseClient(user, password), reconnectExecutor, reconnectInterval);
            return new SenderImpl(client, sseClient, endpoint);
        } catch (InterruptedException | ExecutionException | IOException | SAXException e) {
            throw new NodeConnectionException(e);
        }
    }

    private static String getRestconfLinkFromResponse(final String response) throws IOException, SAXException {
        final Document document = XmlUtil.readXmlToDocument(response);
        final List<XmlElement> links = XmlElement.fromDomDocument(document).getChildElementsWithinNamespace("Link", "http://docs.oasis-open.org/ns/xri/xrd-1.0");
        for (final XmlElement link : links) {
            if ("restconf".equals(link.getAttribute("rel"))) {
                return link.getAttribute("href");
            }
        }
        throw new SAXException("Restconf link not found");
    }

    private String getProtocol(final RestconfNode node) {
        return node.isHttps() ? "https://" : "http://";
    }

    private String getEndpoint(final RestconfNode node, final String rootResource) {
        return getProtocol(node) + String.valueOf(node.getAddress().getValue()) + ":" + node.getPort().getValue() + rootResource;
    }

    private AsyncHttpClient createSseClient(final String user, final String password) {
        return clientProvider.createHttpClient(user, password, -1, -1, defaultConnectTimeout(), defaultFollowRedirect());
    }

    private AsyncHttpClient createClient(final String user, final String password, final int requestTimeout) {
        return clientProvider.createHttpClient(user, password, defaultPooledConnectionIdleTimeout(), requestTimeout, requestTimeout, true);
    }

}
