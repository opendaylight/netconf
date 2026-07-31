/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.it.openapi;

import static io.netty.handler.codec.http3.Http3Headers.PseudoHeaderName.STATUS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http3.DefaultHttp3HeadersFrame;
import io.netty.handler.codec.http3.Http3;
import io.netty.handler.codec.http3.Http3ClientConnectionHandler;
import io.netty.handler.codec.http3.Http3DataFrame;
import io.netty.handler.codec.http3.Http3HeadersFrame;
import io.netty.handler.codec.http3.Http3RequestStreamInboundHandler;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicSslContextBuilder;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.util.ReferenceCountUtil;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.opendaylight.netconf.test.tool.NetconfDeviceSimulator;
import org.opendaylight.netconf.test.tool.config.ConfigurationBuilder;
import org.opendaylight.netconf.topology.impl.NetconfTopologyImpl;
import org.opendaylight.restconf.it.ProtocolVersion;

class BenchmarkOpenApiIT extends AbstractOpenApiTest {
    private NetconfDeviceSimulator deviceSimulator;
    private NetconfTopologyImpl topologyService;
    private Http3GetClient http3Client;
    private int devicePort;

    @BeforeEach
    @Override
    protected void beforeEach() throws Exception {
        super.beforeEach();
        // topology
        topologyService = setupTopology();
    }

    @AfterEach
    @Override
    protected void afterEach() throws Exception {
        if (http3Client != null) {
            http3Client.close();
            http3Client = null;
        }
        if (deviceSimulator != null) {
            deviceSimulator.close();
            deviceSimulator = null;
        }
        if (topologyService != null) {
            topologyService.close();
            topologyService = null;
        }
        super.afterEach();
    }

    @ParameterizedTest
    @EnumSource(value = ProtocolVersion.class, names = {"HTTP_1_1", "HTTP_2"})
    void benchmarkHttp1And2Test(final ProtocolVersion version) throws Exception {
        startDeviceSimulator();
        mountDeviceJson(devicePort);
        benchmarkTest(version);
    }

    private void benchmarkTest(final ProtocolVersion version) throws Exception {
        final var client = HttpClient.newBuilder()
            .version(version == ProtocolVersion.HTTP_2 ? HttpClient.Version.HTTP_2 : HttpClient.Version.HTTP_1_1)
            .authenticator(new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(USERNAME, PASSWORD.toCharArray());
                }
            })
            .build();

        // Due to size of the response, we discard the body.
        final var headers = client.send(HttpRequest.newBuilder()
            .GET()
            .uri(createApiUri("/mounts/1", version))
            .timeout(Duration.ofMinutes(5))
            .build(), HttpResponse.BodyHandlers.discarding());
        assertEquals(200, headers.statusCode());
        if (version == ProtocolVersion.HTTP_2) {
            assertEquals(HttpClient.Version.HTTP_2, headers.version());
        } else {
            // chunked Transfer-Encoding is an HTTP/1.1-specific concept; HTTP/2 has its own native framing
            assertEquals("chunked", headers.headers().firstValue("Transfer-Encoding").orElseThrow());
        }

        // The response is still too large for whole comparison, so just check some random rpc, to verify the data
        final var response = client.send(HttpRequest.newBuilder()
            .GET()
            .uri(createApiUri("/mounts/1?depth=1&width=1", version))
            .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        if (version == ProtocolVersion.HTTP_2) {
            assertEquals(HttpClient.Version.HTTP_2, response.version());
        }
        assertJuniperContent(response.body());
    }

    // Native HttpClient support only HTTP/1.1 and HTTP/2 for now, so HTTP/3 uses a different, dedicated client
    // (Http3GetClient below) and, consequently, its own non-parameterized test.
    @Test
    void benchmarkHttp3Test() throws Exception {
        startDeviceSimulator();
        mountDeviceJson(devicePort);
        http3Client = new Http3GetClient(localAddress(), port(), USERNAME, PASSWORD);

        // Due to size of the response, we discard the body.
        final var headers = http3Client.get(createApiUri("/mounts/1", ProtocolVersion.HTTP_3), true,
            Duration.ofMinutes(5));
        assertEquals(HttpResponseStatus.OK, headers.status());

        final var response = http3Client.get(createApiUri("/mounts/1?depth=1&width=1", ProtocolVersion.HTTP_3),
            false, Duration.ofSeconds(2));
        assertEquals(HttpResponseStatus.OK, response.status());
        assertJuniperContent(response.content());
    }

    private static void assertJuniperContent(final String body) {
        assertTrue(body.contains("junos-conf-root:configuration"));
        assertTrue(body.contains("junos-rpc-services:get-l2tp-disconnect-cause-summary"));
        assertTrue(body.contains("junos-rpc-unified-edge_get-sgw-cac-statistics_input"));
    }

    private URI createApiUri(final String path, final ProtocolVersion version) throws URISyntaxException {
        return new URI(schemeOf(version) + "://" + host() + API_V3_PATH + path);
    }

    private void startDeviceSimulator() {
        devicePort = randomBindablePort();
        final var configBuilder = new ConfigurationBuilder()
            .setStartingPort(devicePort)
            .setDeviceCount(1)
            .setSsh(true)
            .setAuthProvider((usr, pwd) -> DEVICE_USERNAME.equals(usr) && DEVICE_PASSWORD.equals(pwd))
            .build();

        configBuilder.setSchemasDir(Path.of("target/test-classes/juniper").toFile());
        deviceSimulator = new NetconfDeviceSimulator(configBuilder);
        deviceSimulator.start();
    }

    /**
     * Minimal, one-shot, GET-only HTTP/3 client, hardcoded to this benchmark's exact needs: fetch a
     * response over QUIC with HTTP Basic auth.
     */
    private static final class Http3GetClient implements AutoCloseable {
        private final Channel datagramChannel;
        private final QuicChannel quicChannel;
        private final MultiThreadIoEventLoopGroup group;
        private final String username;
        private final String password;

        Http3GetClient(final String host, final int port, final String username, final String password)
                throws Exception {
            this.username = username;
            this.password = password;

            group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
            final var sslContext = QuicSslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .applicationProtocols(Http3.supportedApplicationProtocols())
                .build();
            final var codec = Http3.newQuicClientCodecBuilder()
                .sslContext(sslContext)
                .maxIdleTimeout(50_000, TimeUnit.MILLISECONDS)
                .initialMaxData(quicInitialMaxData().longValue())
                .initialMaxStreamDataBidirectionalLocal(quicInitialMaxStreamDataBidiRemote().longValue())
                .build();

            datagramChannel = new Bootstrap().group(group)
                .channel(NioDatagramChannel.class)
                .handler(codec)
                .bind(0)
                .sync()
                .channel();

            quicChannel = QuicChannel.newBootstrap(datagramChannel)
                .handler(new Http3ClientConnectionHandler())
                .remoteAddress(new InetSocketAddress(host, port))
                .connect()
                .get();
        }

        Response get(final URI uri, final boolean discardBody, final Duration timeout) throws Exception {
            final var query = uri.getRawQuery();
            final var path = uri.getRawPath() + (query != null && !query.isEmpty() ? "?" + query : "");
            final var body = new StringBuilder();
            final var status = new AtomicInteger();
            final var responseFuture = new CompletableFuture<Response>();

            final var streamChannelFuture = Http3.newRequestStream(quicChannel,
                new Http3RequestStreamInboundHandler() {
                    @Override
                    protected void channelRead(final ChannelHandlerContext ctx, final Http3HeadersFrame frame) {
                        final var statusValue = frame.headers().get(STATUS.value());
                        if (statusValue != null) {
                            status.set(Integer.parseInt(statusValue.toString()));
                        }
                        ReferenceCountUtil.release(frame);
                    }

                    @Override
                    protected void channelRead(final ChannelHandlerContext ctx, final Http3DataFrame frame) {
                        if (!discardBody) {
                            body.append(frame.content().toString(StandardCharsets.UTF_8));
                        }
                        ReferenceCountUtil.release(frame);
                    }

                    @Override
                    protected void channelInputClosed(final ChannelHandlerContext ctx) {
                        responseFuture.complete(new Response(HttpResponseStatus.valueOf(status.get()),
                            body.toString()));
                        ctx.close();
                    }

                    @Override
                    public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) {
                        responseFuture.completeExceptionally(cause);
                        ctx.close();
                    }
                });

            if (!streamChannelFuture.await(2, TimeUnit.SECONDS)) {
                throw new TimeoutException("Stream creation timed out after 2 seconds");
            }

            final var headersFrame = new DefaultHttp3HeadersFrame();
            headersFrame.headers()
                .method("GET")
                .path(path)
                .authority(uri.getAuthority())
                .scheme(uri.getScheme())
                .add(HttpHeaderNames.AUTHORIZATION,
                    "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes()));

            streamChannelFuture.getNow().writeAndFlush(headersFrame)
                .addListener(QuicStreamChannel.SHUTDOWN_OUTPUT).sync();

            return responseFuture.get(timeout.toSeconds(), TimeUnit.SECONDS);
        }

        @Override
        public void close() throws Exception {
            quicChannel.close().sync();
            datagramChannel.close().sync();
            group.shutdownGracefully();
        }

        record Response(HttpResponseStatus status, String content) {
        }
    }
}
