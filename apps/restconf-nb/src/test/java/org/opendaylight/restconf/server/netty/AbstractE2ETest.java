/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.netty;

import static org.mockito.Mockito.mock;
import static org.opendaylight.restconf.server.NettyMediaTypes.APPLICATION_YANG;
import static org.opendaylight.restconf.server.NettyMediaTypes.APPLICATION_YIN_XML;

import com.google.common.util.concurrent.FutureCallback;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.AsciiString;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.apache.shiro.web.mgt.DefaultWebSecurityManager;
import org.eclipse.jdt.annotation.NonNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.opendaylight.mdsal.binding.dom.adapter.test.AbstractDataBrokerTest;
import org.opendaylight.mdsal.dom.broker.DOMMountPointServiceImpl;
import org.opendaylight.mdsal.dom.broker.DOMRpcRouter;
import org.opendaylight.mdsal.dom.broker.RouterDOMActionService;
import org.opendaylight.mdsal.dom.broker.RouterDOMRpcService;
import org.opendaylight.mdsal.dom.spi.FixedDOMSchemaService;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.http.ConfigUtils;
import org.opendaylight.netconf.transport.http.HTTPClient;
import org.opendaylight.netconf.transport.tcp.BootstrapFactory;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.server.AAAShiroPrincipalService;
import org.opendaylight.restconf.server.NettyEndpoint;
import org.opendaylight.restconf.server.NettyEndpointConfiguration;
import org.opendaylight.restconf.server.NettyMediaTypes;
import org.opendaylight.restconf.server.jaxrs.JaxRsLocationProvider;
import org.opendaylight.restconf.server.mdsal.MdsalDatabindProvider;
import org.opendaylight.restconf.server.mdsal.MdsalRestconfServer;
import org.opendaylight.restconf.server.mdsal.MdsalRestconfStreamRegistry;
import org.opendaylight.restconf.server.spi.ErrorTagMapping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208.HttpClientStackGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208.HttpServerStackGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208.http.server.stack.grouping.Transport;
import org.opendaylight.yangtools.binding.DataContainer;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.opendaylight.yangtools.yang.common.Uint32;

abstract class AbstractE2ETest extends AbstractDataBrokerTest {
    private NettyEndpoint endpoint;
    private HTTPClient client;
    private BootstrapFactory bootstrapFactory;

    @BeforeEach
    void setUp() throws Exception {
        final var localAddress = InetAddress.getLoopbackAddress().getHostAddress();

        final var domDataBroker = createDataBrokerTestCustomizer().getDOMDataBroker();

        final var securityManager = new DefaultWebSecurityManager();
        final var principalService = new AAAShiroPrincipalService(securityManager);
        final var serverTransport = ConfigUtils.serverTransportTcp(localAddress, 8182);
        final var serverStackGrouping = new HttpServerStackGrouping() {
            @Override
            public Class<? extends HttpServerStackGrouping> implementedInterface() {
                return HttpServerStackGrouping.class;
            }

            @Override
            public Transport getTransport() {
                return serverTransport;
            }
        };
        final var configuration = new NettyEndpointConfiguration(
            ErrorTagMapping.RFC8040, PrettyPrintParam.FALSE, Uint16.ZERO, Uint32.valueOf(1000),
            new URI("rests"), "restconf-server-e2e", 0, NettyEndpointConfiguration.Encoding.JSON, serverStackGrouping);

        final var dataBindProvider = new MdsalDatabindProvider(
            new FixedDOMSchemaService(getRuntimeContext().modelContext()));
        final var rpcService = new RouterDOMRpcService(new DOMRpcRouter());
        final var actionService = new RouterDOMActionService(new DOMRpcRouter());
        final var mountPointService = new DOMMountPointServiceImpl();

        final var server = new MdsalRestconfServer(dataBindProvider, domDataBroker, rpcService, actionService,
            mountPointService);
        final var registry = new MdsalRestconfStreamRegistry(new JaxRsLocationProvider(), domDataBroker);
        endpoint = new NettyEndpoint(server, principalService, registry, configuration);

        bootstrapFactory = new BootstrapFactory("restconf-client-e2e", 0);
        final var clientransport = ConfigUtils.clientTransportTcp(localAddress, 8182);
        final var clientStackGrouping = new HttpClientStackGrouping() {
            @Override
            public @NonNull Class<? extends DataContainer> implementedInterface() {
                return HttpClientStackGrouping.class;
            }

            @Override
            public org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208
                .http.client.stack.grouping.Transport getTransport() {
                return clientransport;
            }
        };
        // FIXME initialize channel
        final var transportChannelListener = mock(TransportChannelListener.class);
        client =  HTTPClient.connect(transportChannelListener, bootstrapFactory.newBootstrap(),
            clientStackGrouping, true).get(10, TimeUnit.SECONDS);
    }

    @AfterEach
    void tearDown() {
        endpoint.deactivate();
        client.shutdown();
        bootstrapFactory.close();
    }

    void invokeRequest(final FullHttpRequest request, final FutureCallback<FullHttpResponse> callback) {
        client.invoke(request, callback);
    }

    static FullHttpRequest buildRequest(final HttpMethod method, final String uri, final TestEncoding encoding,
            final String content) {
        final var contentBuf = content == null ? Unpooled.EMPTY_BUFFER
            : Unpooled.wrappedBuffer(content.getBytes(StandardCharsets.UTF_8));
        final var request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, method, uri, contentBuf);
        if (method != HttpMethod.GET) {
            request.headers().set(HttpHeaderNames.CONTENT_TYPE, encoding.requestType);
        }
        request.headers().set(HttpHeaderNames.ACCEPT, encoding.responseType);
        return request;
    }

    enum TestEncoding {
        XML(NettyMediaTypes.APPLICATION_XML, NettyMediaTypes.APPLICATION_YANG_DATA_XML),
        JSON(NettyMediaTypes.APPLICATION_JSON, NettyMediaTypes.APPLICATION_YANG_DATA_JSON),
        YANG_PATCH_XML(NettyMediaTypes.APPLICATION_YANG_PATCH_XML, NettyMediaTypes.APPLICATION_YANG_DATA_XML),
        YANG_PATCH_JSON(NettyMediaTypes.APPLICATION_YANG_PATCH_JSON, NettyMediaTypes.APPLICATION_YANG_DATA_JSON),
        YANG(null, APPLICATION_YANG),
        YIN(null, APPLICATION_YIN_XML);

        AsciiString requestType;
        AsciiString responseType;

        TestEncoding(AsciiString requestType, AsciiString responseType) {
            this.requestType = requestType;
            this.responseType = responseType;
        }
    }
}
