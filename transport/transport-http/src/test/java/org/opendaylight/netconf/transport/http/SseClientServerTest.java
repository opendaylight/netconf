/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static io.netty.handler.codec.http.HttpHeaderNames.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderValues.KEEP_ALIVE;
import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.opendaylight.netconf.transport.http.ConfigUtils.clientTransportTcp;
import static org.opendaylight.netconf.transport.http.ConfigUtils.clientTransportTls;
import static org.opendaylight.netconf.transport.http.ConfigUtils.serverTransportTcp;
import static org.opendaylight.netconf.transport.http.ConfigUtils.serverTransportTls;
import static org.opendaylight.netconf.transport.http.TestUtils.freePort;
import static org.opendaylight.netconf.transport.http.TestUtils.generateX509CertData;
import static org.opendaylight.netconf.transport.http.TestUtils.invoke;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.netconf.transport.api.TransportChannel;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.http.EventStreamService.StartCallback;
import org.opendaylight.netconf.transport.tcp.BootstrapFactory;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208.HttpClientStackGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208.HttpServerStackGrouping;

@ExtendWith(MockitoExtension.class)
class SseClientServerTest {
    private static final String USERNAME = "username";
    private static final String PASSWORD = "pa$$W0rd";
    private static final Map<String, String> USER_HASHES_MAP = Map.of(USERNAME, "$0$" + PASSWORD);
    private static final String DATA_URI = "/data";
    private static final String STREAM_URI = "/stream";
    private static final ByteBuf OK_CONTENT = Unpooled.wrappedBuffer("OK".getBytes(StandardCharsets.UTF_8));
    private static final String DECLINE_MESSAGE = "decline-message";
    private static final String DATA = "data";
    private static final List<String> DATA_VALUES = IntStream.rangeClosed(1, 10)
        .mapToObj(num -> "value " + num).toList();

    private static BootstrapFactory bootstrapFactory;
    private static String localAddress;

    @Mock
    private HttpServerStackGrouping serverConfig;
    @Mock
    private HttpClientStackGrouping clientConfig;
    @Mock
    private EventStreamListener eventStreamListener;
    @Mock
    private StartCallback startCallback;
    @Captor
    private ArgumentCaptor<Exception> exceptionCaptor;

    private EventStreamService clientEventStreamService;
    private TestStreamService serverEventStreamService;
    private TestTransportListener serverTransportListener;
    private TestTransportListener clientTransportListener;

    @BeforeAll
    static void beforeAll() {
        bootstrapFactory = new BootstrapFactory("IntegrationTest", 0);
        localAddress = InetAddress.getLoopbackAddress().getHostAddress();
    }

    @AfterAll
    static void afterAll() {
        bootstrapFactory.close();
    }

    @BeforeEach
    void beforeEach() {
        clientEventStreamService = null;
        serverEventStreamService = new TestStreamService();
        // init SSE layer on top of HTTP layer using Transport channel listeners
        serverTransportListener = new TestTransportListener(channel -> {
            channel.pipeline().addLast(
                new ServerSseHandler(serverEventStreamService, 0, 0),
                new SimpleChannelInboundHandler<>(FullHttpRequest.class) {
                    @Override
                    protected void channelRead0(final ChannelHandlerContext ctx, final FullHttpRequest msg) {
                        final var response = DATA_URI.equals(msg.uri())
                            ? new DefaultFullHttpResponse(msg.protocolVersion(), OK, OK_CONTENT.copy())
                            : new DefaultFullHttpResponse(msg.protocolVersion(), NOT_FOUND, Unpooled.EMPTY_BUFFER);
                        response.headers().set(CONTENT_LENGTH, response.content().readableBytes());
                        Http2Utils.copyStreamId(msg, response);
                        ctx.writeAndFlush(response);
                    }
                });
        });
        clientTransportListener = new TestTransportListener(channel ->
            clientEventStreamService = SseUtils.enableClientSse(channel));
    }

    @ParameterizedTest(name = "TCP with no authorization, HTTP/2: {0}")
    @ValueSource(booleans = {false, true})
    void noAuthTcp(final boolean http2) throws Exception {
        final var localPort = freePort();
        doReturn(serverTransportTcp(localAddress, localPort)).when(serverConfig).getTransport();
        doReturn(clientTransportTcp(localAddress, localPort)).when(clientConfig).getTransport();
        integrationTest(http2);
    }

    @ParameterizedTest(name = "TCP with Basic authorization, HTTP/2: {0}")
    @ValueSource(booleans = {false, true})
    void basicAuthTcp(final boolean http2) throws Exception {
        final var localPort = freePort();
        doReturn(serverTransportTcp(localAddress, localPort, USER_HASHES_MAP))
            .when(serverConfig).getTransport();
        doReturn(clientTransportTcp(localAddress, localPort, USERNAME, PASSWORD))
            .when(clientConfig).getTransport();
        integrationTest(http2);
    }

    @ParameterizedTest(name = "TLS with no authorization, HTTP/2: {0}")
    @ValueSource(booleans = {false, true})
    void noAuthTls(final boolean http2) throws Exception {
        final var certData = generateX509CertData("RSA");
        final var localPort = freePort();
        doReturn(serverTransportTls(localAddress, localPort, certData.certificate(), certData.privateKey()))
            .when(serverConfig).getTransport();
        doReturn(clientTransportTls(localAddress, localPort, certData.certificate())).when(clientConfig).getTransport();
        integrationTest(http2);
    }

    @ParameterizedTest(name = "TLS with Basic authorization, HTTP/2: {0}")
    @ValueSource(booleans = {false, true})
    void basicAuthTls(final boolean http2) throws Exception {
        final var certData = generateX509CertData("RSA");
        final var localPort = freePort();
        doReturn(serverTransportTls(localAddress, localPort, certData.certificate(), certData.privateKey(),
            USER_HASHES_MAP)).when(serverConfig).getTransport();
        doReturn(clientTransportTls(localAddress, localPort, certData.certificate(), USERNAME, PASSWORD))
            .when(clientConfig).getTransport();
        integrationTest(http2);
    }

    private void integrationTest(final boolean http2) throws Exception {
        final var server = HTTPServer.listen(serverTransportListener, bootstrapFactory.newServerBootstrap(),
            serverConfig).get(2, TimeUnit.SECONDS);
        try {
            final var client = HTTPClient.connect(clientTransportListener, bootstrapFactory.newBootstrap(),
                clientConfig, http2).get(2, TimeUnit.SECONDS);
            try {
                await().atMost(Duration.ofSeconds(2)).until(() -> serverTransportListener.initialized);
                await().atMost(Duration.ofSeconds(2)).until(() -> clientTransportListener.initialized);
                assertNotNull(clientEventStreamService);

                // verify HTTP request/response works over current connection
                assertGetRequest(client);

                // request SSE with invalid URI
                clientEventStreamService.startEventStream(DATA_URI, eventStreamListener, startCallback);
                verify(startCallback, timeout(1000)).onStartFailure(exceptionCaptor.capture());
                final var exception = exceptionCaptor.getValue();
                assertNotNull(exception);
                assertEquals(DECLINE_MESSAGE, exception.getMessage());

                // start SSE stream with proper URI
                clientEventStreamService.startEventStream(STREAM_URI, eventStreamListener, startCallback);
                verify(startCallback, timeout(1000)).onStreamStarted(any());
                verify(eventStreamListener).onStreamStart();

                // send series of event fields (name:value pairs)
                assertNotNull(serverEventStreamService.listener);
                for (var value : DATA_VALUES) {
                    serverEventStreamService.listener.onEventField(DATA, value);
                    verify(eventStreamListener, timeout(1000)).onEventField(DATA, value);
                }

                // end stream while keeping connection alive
                serverEventStreamService.listener.onStreamEnd();
                verify(eventStreamListener, timeout(1000)).onStreamEnd();

                // verify HTTP request/response works on same connection
                assertGetRequest(client);

            } finally {
                client.shutdown().get(2, TimeUnit.SECONDS);
            }
        } finally {
            server.shutdown().get(2, TimeUnit.SECONDS);
        }
    }

    private static void assertGetRequest(final HTTPClient client) throws Exception {
        final var request = new DefaultFullHttpRequest(HTTP_1_1, GET, DATA_URI);
        request.headers().set(CONNECTION, KEEP_ALIVE);
        final var response = invoke(client, request).get(2, TimeUnit.SECONDS);
        assertNotNull(response);
        assertEquals(OK, response.status());
    }

    private static final class TestStreamService implements EventStreamService {
        private EventStreamListener listener;

        @Override
        public void startEventStream(final String requestUri, final EventStreamListener eventListener,
                final StartCallback callback) {
            if (STREAM_URI.equals(requestUri)) {
                // accept stream request
                listener = eventListener;
                callback.onStreamStarted(() -> {
                    // no-op
                });
            } else {
                // decline stream request
                callback.onStartFailure(new IllegalStateException(DECLINE_MESSAGE));
            }
        }
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
