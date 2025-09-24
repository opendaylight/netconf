/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static io.netty.buffer.Unpooled.wrappedBuffer;
import static io.netty.handler.codec.http.HttpHeaderNames.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static io.netty.handler.codec.http.HttpHeaderValues.KEEP_ALIVE;
import static io.netty.handler.codec.http.HttpHeaderValues.TEXT_PLAIN;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
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

import com.google.common.base.MoreObjects.ToStringHelper;
import com.google.common.util.concurrent.Uninterruptibles;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Flags;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.codec.http2.Http2Stream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Authenticator;
import java.net.InetAddress;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.netconf.transport.api.TransportChannel;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.tcp.BootstrapFactory;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.client.rev240208.HttpClientStackGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev240208.HttpServerStackGrouping;

@ExtendWith(MockitoExtension.class)
class HttpClientServerTest {
    private static final class TestRequest extends PendingRequest<Object> {
        private final ImplementedMethod method;
        private final URI targetUri;

        TestRequest(final ImplementedMethod method, final URI targetUri) {
            this.method = requireNonNull(method);
            this.targetUri = requireNonNull(targetUri);
        }

        @Override
        public void execute(final PendingRequestListener listener, final InputStream body) {
            // we should be executing on a virtual thread
            assertTrue(Thread.currentThread().isVirtual());
            assertThat(Thread.currentThread().getName()).contains("-http-server-req-");

            // return 200 response with a content built from request parameters
            final String payload;
            if (body != null) {
                try {
                    payload = new String(body.readAllBytes(), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    listener.requestFailed(this, e);
                    return;
                }
            } else {
                payload = "";
            }

            // emulate delay in server processing
            Uninterruptibles.sleepUninterruptibly(Duration.ofMillis(100));

            final var content = ByteBufUtil.writeUtf8(UnpooledByteBufAllocator.DEFAULT,
                "Method: %s URI: %s Payload: %s".formatted(method, targetUri.getPath(), payload));

            listener.requestComplete(this, ByteBufResponse.ok(content, HttpHeaderValues.TEXT_PLAIN));
        }

        @Override
        protected ToStringHelper addToStringAttributes(final ToStringHelper helper) {
            return helper;
        }
    }

    private static final String USERNAME = "username";
    private static final String PASSWORD = "pa$$W0rd";
    private static final Map<String, String> USER_HASHES_MAP = Map.of(USERNAME, "$0$" + PASSWORD);
    private static final AtomicInteger COUNTER = new AtomicInteger(0);
    private static final String[] METHODS = {"GET", "POST", "PUT", "PATCH", "DELETE"};

    private static final AuthHandlerFactory CUSTOM_AUTH_HANDLER_FACTORY =
        () -> new AbstractBasicAuthHandler<String>() {
            @Override
            protected String authenticate(final String username, final String password) {
                return USERNAME.equals(username) && PASSWORD.equals(password) ? username : null;
            }
        };

    private static BootstrapFactory bootstrapFactory;
    private static String localAddress;

    @Mock
    private HttpServerStackGrouping serverConfig;
    @Mock
    private HttpClientStackGrouping clientConfig;
    @Mock
    private TransportChannelListener<TransportChannel> serverTransportListener;
    @Mock
    private TransportChannelListener<TransportChannel> clientTransportListener;

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
        // TODO: this looks like a spy() on a real implementation
        doAnswer(inv -> {
            final var channel = inv.<HTTPTransportChannel>getArgument(0);
            channel.channel().pipeline().addLast(new HTTPServerSessionBootstrap(channel.scheme()) {
                @Override
                protected PipelinedHTTPServerSession configureHttp1(final ChannelHandlerContext ctx) {
                    return new PipelinedHTTPServerSession(scheme) {
                        @Override
                        protected TestRequest prepareRequest(final ImplementedMethod method, final URI targetUri,
                                final HttpHeaders headers) {
                            return new TestRequest(method, targetUri);
                        }
                    };
                }

                @Override
                protected ConcurrentHTTPServerSession configureHttp2(final ChannelHandlerContext ctx) {
                    return new ConcurrentHTTPServerSession(scheme) {
                        @Override
                        public void onStreamAdded(final Http2Stream stream) {

                        }

                        @Override
                        public void onStreamActive(final Http2Stream stream) {

                        }

                        @Override
                        public void onStreamHalfClosed(final Http2Stream stream) {

                        }

                        @Override
                        public void onStreamClosed(final Http2Stream stream) {

                        }

                        @Override
                        public void onStreamRemoved(final Http2Stream stream) {

                        }

                        @Override
                        public void onGoAwaySent(final int lastStreamId, final long errorCode, final ByteBuf debugData) {

                        }

                        @Override
                        public void onGoAwayReceived(final int lastStreamId, final long errorCode, final ByteBuf debugData) {

                        }

                        @Override
                        public int onDataRead(final ChannelHandlerContext ctx, final int streamId, final ByteBuf data, final int padding, final boolean endOfStream) throws Http2Exception {
                            return 0;
                        }

                        @Override
                        public void onHeadersRead(final ChannelHandlerContext ctx, final int streamId, final Http2Headers headers, final int padding, final boolean endOfStream) throws Http2Exception {

                        }

                        @Override
                        public void onHeadersRead(final ChannelHandlerContext ctx, final int streamId, final Http2Headers headers, final int streamDependency, final short weight, final boolean exclusive, final int padding, final boolean endOfStream) throws Http2Exception {

                        }

                        @Override
                        public void onPriorityRead(final ChannelHandlerContext ctx, final int streamId, final int streamDependency, final short weight, final boolean exclusive) throws Http2Exception {

                        }

                        @Override
                        public void onRstStreamRead(final ChannelHandlerContext ctx, final int streamId, final long errorCode) throws Http2Exception {

                        }

                        @Override
                        public void onSettingsAckRead(final ChannelHandlerContext ctx) throws Http2Exception {

                        }

                        @Override
                        public void onSettingsRead(final ChannelHandlerContext ctx, final Http2Settings settings) throws Http2Exception {

                        }

                        @Override
                        public void onPingRead(final ChannelHandlerContext ctx, final long data) throws Http2Exception {

                        }

                        @Override
                        public void onPingAckRead(final ChannelHandlerContext ctx, final long data) throws Http2Exception {

                        }

                        @Override
                        public void onPushPromiseRead(final ChannelHandlerContext ctx, final int streamId, final int promisedStreamId, final Http2Headers headers, final int padding) throws Http2Exception {

                        }

                        @Override
                        public void onGoAwayRead(final ChannelHandlerContext ctx, final int lastStreamId, final long errorCode, final ByteBuf debugData) throws Http2Exception {

                        }

                        @Override
                        public void onWindowUpdateRead(final ChannelHandlerContext ctx, final int streamId, final int windowSizeIncrement) throws Http2Exception {

                        }

                        @Override
                        public void onUnknownFrame(final ChannelHandlerContext ctx, final byte frameType, final int streamId, final Http2Flags flags, final ByteBuf payload) throws Http2Exception {

                        }

                        @Override
                        protected TestRequest prepareRequest(final ImplementedMethod method, final URI targetUri,
                                final HttpHeaders headers) {
                            return new TestRequest(method, targetUri);
                        }
                    };
                }
            });
            return null;
        }).when(serverTransportListener).onTransportChannelEstablished(any());
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

    @ParameterizedTest(name = "TCP with custom auth handler factory, HTTP/2: {0}")
    @ValueSource(booleans = {false, true})
    void customAuthTcp(final boolean http2) throws Exception {
        final var localPort = freePort();
        doReturn(serverTransportTcp(localAddress, localPort)).when(serverConfig).getTransport();
        doReturn(clientTransportTcp(localAddress, localPort, USERNAME, PASSWORD))
            .when(clientConfig).getTransport();
        integrationTest(http2, CUSTOM_AUTH_HANDLER_FACTORY);
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
        final var certData = generateX509CertData("EC");
        final var localPort = freePort();
        doReturn(serverTransportTls(localAddress, localPort, certData.certificate(), certData.privateKey(),
            USER_HASHES_MAP)).when(serverConfig).getTransport();
        doReturn(clientTransportTls(localAddress, localPort, certData.certificate(), USERNAME, PASSWORD))
            .when(clientConfig).getTransport();
        integrationTest(http2);
    }

    @ParameterizedTest(name = "TLS with custom auth handler factory, HTTP/2: {0}")
    @ValueSource(booleans = {false, true})
    void customAuthTls(final boolean http2) throws Exception {
        final var certData = generateX509CertData("RSA");
        final var localPort = freePort();
        doReturn(serverTransportTls(localAddress, localPort, certData.certificate(), certData.privateKey()))
            .when(serverConfig).getTransport();
        doReturn(clientTransportTls(localAddress, localPort, certData.certificate(), USERNAME, PASSWORD))
            .when(clientConfig).getTransport();
        integrationTest(http2, CUSTOM_AUTH_HANDLER_FACTORY);
    }

    private void integrationTest(final boolean http2) throws Exception {
        integrationTest(http2, null);
    }

    private void integrationTest(final boolean http2, final AuthHandlerFactory authHandlerFactory) throws Exception {
        final var server = HTTPServer.listen(serverTransportListener, bootstrapFactory.newServerBootstrap(),
            serverConfig).get(2, TimeUnit.SECONDS);
        try {
            final var client = HTTPClient.connect(clientTransportListener, bootstrapFactory.newBootstrap(),
                    clientConfig, http2).get(2, TimeUnit.SECONDS);
            try {
                verify(clientTransportListener, timeout(2000)).onTransportChannelEstablished(any());
                verify(serverTransportListener, timeout(2000)).onTransportChannelEstablished(any());

                for (var method : METHODS) {
                    final var uri = nextValue("/URI");
                    final var payload = nextValue("PAYLOAD");
                    final var request = new DefaultFullHttpRequest(HTTP_1_1, HttpMethod.valueOf(method),
                        uri, wrappedBuffer(payload.getBytes(StandardCharsets.UTF_8)));
                    request.headers()
                        .set(HOST, "example.com")
                        .set(CONTENT_TYPE, TEXT_PLAIN)
                        .setInt(CONTENT_LENGTH, request.content().readableBytes())
                        // allow multiple requests on same connections
                        .set(CONNECTION, KEEP_ALIVE);

                    final var response = invoke(client, request).get(2, TimeUnit.SECONDS);
                    assertNotNull(response);
                    assertEquals(OK, response.status());
                    assertEquals("Method: " + method + " URI: " + uri + " Payload: " + payload,
                        response.content().toString(StandardCharsets.UTF_8));
                }
            } finally {
                client.shutdown().get(2, TimeUnit.SECONDS);
            }
        } finally {
            server.shutdown().get(2, TimeUnit.SECONDS);
        }
    }

    @ParameterizedTest(name = "Java HttpClient compatibility check, Basic Auth: {0}")
    @ValueSource(booleans = {false, true})
    @Timeout(20)
    void cleartextUpgradeFlowCompatibility(final boolean withAuth) throws Exception {
        // validate server cleartext protocol upgrade flow being compatible with java.net.HttpClient
        final var localPort = freePort();
        final var transport = withAuth
            ? serverTransportTcp(localAddress, localPort, USER_HASHES_MAP)
            : serverTransportTcp(localAddress, localPort);
        doReturn(transport).when(serverConfig).getTransport();

        final var uri = nextValue("URI");
        final var payload = nextValue("PAYLOAD");
        final var url = URI.create("http://%s:%d/%s".formatted(localAddress, localPort, uri));

        final var server = HTTPServer.listen(serverTransportListener, bootstrapFactory.newServerBootstrap(),
            serverConfig).get(2, TimeUnit.SECONDS);
        try {
            try (var client = HttpClient.newBuilder()
                // authenticator is only used if only 401 returned
                .authenticator(new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(USERNAME, PASSWORD.toCharArray());
                    }
                })
                .build()) {

                final var request = HttpRequest.newBuilder(url)
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .header("Content-Type", "text-plain")
                    .header("Accept", "text-plain")
                    .build();
                final var response = client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .toCompletableFuture().get(2, TimeUnit.SECONDS);
                assertNotNull(response);
                assertEquals(200, response.statusCode());
                assertEquals("Method: PATCH URI: /" + uri + " Payload: " + payload, response.body());
            }
        } finally {
            server.shutdown().get(2, TimeUnit.SECONDS);
        }
    }

    private static String nextValue(final String prefix) {
        return prefix + COUNTER.incrementAndGet();
    }
}
