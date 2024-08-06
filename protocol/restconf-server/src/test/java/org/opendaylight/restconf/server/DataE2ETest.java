/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static io.netty.buffer.Unpooled.wrappedBuffer;
import static io.netty.handler.codec.http.HttpHeaderNames.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaderValues.KEEP_ALIVE;
import static io.netty.handler.codec.http.HttpHeaderValues.TEXT_PLAIN;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import com.google.common.util.concurrent.FutureCallback;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.shiro.mgt.SecurityManager;
import org.eclipse.jdt.annotation.NonNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opendaylight.netconf.transport.api.TransportChannel;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.http.ConfigUtils;
import org.opendaylight.netconf.transport.http.HTTPClient;
import org.opendaylight.netconf.transport.tcp.BootstrapFactory;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.server.api.RestconfServer;
import org.opendaylight.restconf.server.spi.ErrorTagMapping;
import org.opendaylight.restconf.server.spi.RestconfStream;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208.HttpClientStackGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208.HttpServerStackGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208.http.server.stack.grouping.Transport;
import org.opendaylight.yangtools.binding.DataContainer;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.opendaylight.yangtools.yang.common.Uint32;

class DataE2ETest {
    private static NettyEndpoint endpoint;
    private static HTTPClient client;
    private static BootstrapFactory bootstrapFactory;
    private static ServerSocket socket;

    private static String serverAddress;
    private static int serverPort;

    @BeforeAll
    static void setup() throws Exception {
        serverAddress = InetAddress.getLoopbackAddress().getHostAddress();
        socket = new ServerSocket(0);
        serverPort = socket.getLocalPort();

        final var server = mock(RestconfServer.class);
        final var securityManager = mock(SecurityManager.class);
        final var principalService = new AAAShiroPrincipalService(securityManager);
        final var registry = mock(RestconfStream.Registry.class);
        final var serverTransport = ConfigUtils.serverTransportTcp(serverAddress, 8181);
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
            "rests", "restconf-server-e2e", 0, "json", serverStackGrouping
        );
        endpoint = new NettyEndpoint(server, principalService, registry, configuration);

        final var clientTransportListener = new TestTransportListener(channel -> { });
        bootstrapFactory = new BootstrapFactory("restconf-client-e2e", 0);
        final var clientransport = ConfigUtils.clientTransportTcp(serverAddress, 8181);
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
        client =  HTTPClient.connect(clientTransportListener, bootstrapFactory.newBootstrap(),
            clientStackGrouping, true).get(2, TimeUnit.SECONDS);
    }

    @AfterAll
    static void tearDown() throws Exception {
        socket.close();
        bootstrapFactory.close();
        endpoint.close();
        client.shutdown().get(2, TimeUnit.SECONDS);
    }

    @Test
    void userAuthenticationTest() {
        // TODO
    }

    @Test
    void crudOperationsTest() {
        final var uri = serverAddress + ":" + serverPort + "/rests/data";
        final var request = new DefaultFullHttpRequest(HTTP_1_1, HttpMethod.GET, uri, wrappedBuffer(new byte[] {}));
        request.headers().set(CONTENT_TYPE, TEXT_PLAIN)
            .setInt(CONTENT_LENGTH, request.content().readableBytes())
            // allow multiple requests on same connections
            .set(CONNECTION, KEEP_ALIVE);

        client.invoke(request, new FutureCallback<>() {
            @Override
            public void onSuccess(final FullHttpResponse result) {
                assertNotNull(result);
            }

            @Override
            public void onFailure(final Throwable throwable) {
                assertNotNull(throwable);
            }
        });
    }

    @Test
    void errorHandlingTest() {
        // TODO
    }

    private static class TestTransportListener implements TransportChannelListener {
        private final Consumer<Channel> initializer;
        private volatile boolean initialized;

        TestTransportListener(final Consumer<Channel> initializer) {
            this.initializer = initializer;
        }

        @Override
        public void onTransportChannelEstablished(final TransportChannel channel) {
            initialized = true;
            initializer.accept(channel.channel());
        }

        @Override
        public void onTransportChannelFailed(final Throwable cause) {
            throw new IllegalStateException("HTTP connection failure", cause);
        }
    }
}
