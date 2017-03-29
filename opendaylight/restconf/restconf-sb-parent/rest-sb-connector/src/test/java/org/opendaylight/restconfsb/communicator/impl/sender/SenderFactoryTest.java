/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.communicator.impl.sender;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import com.google.common.net.HttpHeaders;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.ListenableFuture;
import com.ning.http.client.Response;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.restconfsb.communicator.api.http.Sender;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.DomainName;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.PortNumber;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.node.rev160511.RestconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.node.rev160511.RestconfNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;

public class SenderFactoryTest {

    @Mock
    private HttpClientProvider clientProvider;
    @Mock
    private AsyncHttpClient client;
    @Mock
    private AsyncHttpClient.BoundRequestBuilder request;
    @Mock
    private ListenableFuture<Response> future;
    @Mock
    private Response notFoundResponse;
    @Mock
    private Response foundResponse;
    private ScheduledExecutorService reconnectExecutor;
    private SenderFactory senderFactory;
    private Node node;

    private static final RestconfNode NODE = new RestconfNodeBuilder()
            .setAddress(new Host(new DomainName("localhost")))
            .setPort(new PortNumber(9999))
            .setRequestTimeout(5000)
            .setHttps(false)
            .build();

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        node = new NodeBuilder()
                .setNodeId(new NodeId("node1"))
                .addAugmentation(RestconfNode.class, NODE)
                .build();
        doReturn(client).when(clientProvider).createHttpClient(any(String.class), any(String.class), any(Integer.class),
                                                            any(Integer.class), any(Integer.class), any(Boolean.class));
        doReturn(request).when(client).prepareGet(any(String.class));
        doReturn(request).when(request).addHeader(any(String.class), any(String.class));
        doReturn(future).when(request).execute();
        reconnectExecutor = Executors.newSingleThreadScheduledExecutor();
        senderFactory = new SenderFactory(clientProvider, 5);
    }

    @Test
    public void testCreateSenderHostMetaFound() throws Exception {
        doReturn(foundResponse).when(future).get();
        doReturn(200).when(foundResponse).getStatusCode();
        doReturn("<XRD xmlns='http://docs.oasis-open.org/ns/xri/xrd-1.0'>\n" +
                "           <Link rel='restconf' href='/api/restconf'/>\n" +
                "       </XRD>").when(foundResponse).getResponseBody();
        final Sender sender = senderFactory.createSender(node, reconnectExecutor);
        verify(client).prepareGet("http://localhost:9999/.well-known/host-meta");
        verify(request).addHeader(HttpHeaders.ACCEPT, "application/xrd+xml");
        Assert.assertEquals("http://localhost:9999/api/restconf", sender.getEndpoint());
    }

    @Test
    public void testCreateSenderHostMetaNotFound() throws Exception {
        doReturn(notFoundResponse).when(future).get();
        doReturn(404).when(notFoundResponse).getStatusCode();
        final Sender sender = senderFactory.createSender(node, reconnectExecutor);
        verify(client).prepareGet("http://localhost:9999/.well-known/host-meta");
        verify(request).addHeader(HttpHeaders.ACCEPT, "application/xrd+xml");
        Assert.assertEquals("http://localhost:9999/restconf", sender.getEndpoint());
    }

    @Test(expected = NodeConnectionException.class)
    public void testCreateSenderHostMetaParseError() throws Exception {
        doReturn(foundResponse).when(future).get();
        doReturn(200).when(foundResponse).getStatusCode();
        doReturn("ERROR xmlns='http://docs.oasis-open.org/ns/xri/xrd-1.0'>\n" +
                "           <Link rel='restconf' href='/api/restconf'/>\n" +
                "       </ERROR>").when(foundResponse).getResponseBody();
        final Sender sender = senderFactory.createSender(node, reconnectExecutor);
        verify(client).prepareGet("http://localhost:9999/.well-known/host-meta");
        verify(request).addHeader(HttpHeaders.ACCEPT, "application/xrd+xml");
        Assert.assertEquals("http://localhost:9999/api/restconf", sender.getEndpoint());
    }

    @Test(expected = NodeConnectionException.class)
    public void testCreateSenderHostMetaParseError2() throws Exception {
        doReturn(foundResponse).when(future).get();
        doReturn(200).when(foundResponse).getStatusCode();
        doReturn("<XRD xmlns='http://docs.oasis-open.org/ns/xri/xrd-1.0'>\n" +
                "           <Linkk rel='restconf' href='/api/restconf'/>\n" +
                "       </XRD>").when(foundResponse).getResponseBody();
        final Sender sender = senderFactory.createSender(node, reconnectExecutor);
        verify(client).prepareGet("http://localhost:9999/.well-known/host-meta");
        verify(request).addHeader(HttpHeaders.ACCEPT, "application/xrd+xml");
        Assert.assertEquals("http://localhost:9999/api/restconf", sender.getEndpoint());
    }
}