/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.TooLongHttpHeaderException;
import io.netty.handler.codec.http.TooLongHttpLineException;
import io.netty.util.CharsetUtil;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev260204.http.server.listen.stack.grouping.transport.http.over.tcp.http.over.tcp.HttpServerParametersBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.http.server.rev260204.http.server.listen.stack.grouping.transport.http.over.tcp.http.over.tcp.TcpServerParameters;
import org.opendaylight.yang.gen.v1.urn.opendaylight.yang.http.server.rev260731.odl.http.server.listen.stack.grouping.LimitsUnderHttpTcpBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.yang.http.server.rev260731.server.limits.grouping.ServerLimitsBuilder;
import org.opendaylight.yangtools.yang.common.Uint32;

/**
 * Coverage of {@link HTTPServerLimits} as applied to a cleartext HTTP/1.1 server pipeline.
 */
class HTTPSchemeLimitsTest {
    private final List<FullHttpRequest> received = new ArrayList<>();

    @AfterEach
    void afterEach() {
        received.forEach(FullHttpRequest::release);
    }

    @Test
    void requestBodyWithinLimitIsAggregated() {
        final var channel = newChannel(HTTPServerLimits.DEFAULT);
        channel.writeInbound(request(2048));
        channel.runPendingTasks();

        assertEquals(1, received.size());
        final var request = received.getFirst();
        assertTrue(request.decoderResult().isSuccess());
        assertEquals(2048, request.content().readableBytes());
        channel.finishAndReleaseAll();
    }

    @Test
    void oversizedRequestBodyIsRejected() {
        // 1 KiB body limit, everything else left at its default
        final var channel = newChannel(limits(4096, 16384, 1024));
        channel.writeInbound(request(4096));
        channel.runPendingTasks();

        assertTrue(received.isEmpty(), "an over-sized request must not reach the session");
        assertEquals("HTTP/1.1 413 Request Entity Too Large", statusLine(channel));
        channel.finishAndReleaseAll();
    }

    @Test
    void oversizedRequestLineIsReportedAsTooLongLine() {
        final var channel = newChannel(limits(64, 16384, 10485760));
        channel.writeInbound(Unpooled.copiedBuffer(
            "GET /rests/data/" + "a".repeat(128) + " HTTP/1.1\r\nHost: localhost\r\n\r\n", CharsetUtil.US_ASCII));
        channel.runPendingTasks();

        // The decoder does not throw: it marks the request and lets the session decide the status code
        assertEquals(1, received.size());
        final var cause = received.getFirst().decoderResult().cause();
        assertInstanceOf(TooLongHttpLineException.class, cause);
        assertEquals(HttpResponseStatus.REQUEST_URI_TOO_LONG, HTTPServerSession.statusOf(cause));
        channel.finishAndReleaseAll();
    }

    @Test
    void oversizedHeaderSectionIsReportedAsTooLongHeader() {
        final var channel = newChannel(limits(4096, 128, 10485760));
        channel.writeInbound(Unpooled.copiedBuffer(
            "GET /rests/data HTTP/1.1\r\nHost: localhost\r\nX-Padding: " + "a".repeat(256) + "\r\n\r\n",
            CharsetUtil.US_ASCII));
        channel.runPendingTasks();

        assertEquals(1, received.size());
        final var cause = received.getFirst().decoderResult().cause();
        assertInstanceOf(TooLongHttpHeaderException.class, cause);
        assertEquals(HttpResponseStatus.REQUEST_HEADER_FIELDS_TOO_LARGE, HTTPServerSession.statusOf(cause));
        channel.finishAndReleaseAll();
    }

    @Test
    void oversizedRequestLineIsAnsweredWith414() {
        final var channel = newSessionChannel(limits(64, 16384, 10485760));
        channel.writeInbound(Unpooled.copiedBuffer(
            "GET /rests/data/" + "a".repeat(128) + " HTTP/1.1\r\nHost: localhost\r\n\r\n", CharsetUtil.US_ASCII));
        channel.runPendingTasks();

        // note: HTTP/1.1, even though the decoder reports the unparseable request as HTTP/1.0
        assertEquals("HTTP/1.1 414 Request-URI Too Long", statusLine(channel));
        channel.finishAndReleaseAll();
    }

    @Test
    void oversizedHeaderSectionIsAnsweredWith431() {
        final var channel = newSessionChannel(limits(4096, 128, 10485760));
        channel.writeInbound(Unpooled.copiedBuffer(
            "GET /rests/data HTTP/1.1\r\nHost: localhost\r\nX-Padding: " + "a".repeat(256) + "\r\n\r\n",
            CharsetUtil.US_ASCII));
        channel.runPendingTasks();

        assertEquals("HTTP/1.1 431 Request Header Fields Too Large", statusLine(channel));
        channel.finishAndReleaseAll();
    }

    @Test
    void limitsSurviveTheTransportConfiguration() {
        final var limits = new HTTPServerLimits(1024, 2048, 4096, 8192, 16384);

        assertEquals(limits, HTTPServerLimits.of(
            new HttpServerStackConfiguration(HTTPServerOverTcp.of("127.0.0.1", 8182, limits))));
    }

    @Test
    void limitsSurviveTheTlsTransportConfiguration() throws Exception {
        // the TLS branch of of() differs from the TCP one only in the augmentation class it looks up, which is exactly
        // the kind of copy-paste that goes unnoticed: a wrong class yields null and silently falls back to DEFAULT
        final var certData = TestUtils.generateX509CertData("RSA");
        final var limits = new HTTPServerLimits(1024, 2048, 4096, 8192, 16384);

        assertEquals(limits, HTTPServerLimits.of(new HttpServerStackConfiguration(HTTPServerOverTls.of(
            "127.0.0.1", 8443, certData.certificate(), certData.privateKey(), limits))));
    }

    @Test
    void transportWithoutParametersYieldsDefaults() {
        assertEquals(HTTPServerLimits.DEFAULT, HTTPServerLimits.of(
            new HttpServerStackConfiguration(HTTPServerOverTcp.of(tcpServerParameters(), null))));
    }

    @Test
    void unsetLeavesYieldTheirDefaults() {
        // only one leaf configured: everything else, including the entire odl-server-limits container, is absent
        final var transport = HTTPServerOverTcp.of(tcpServerParameters(), new HttpServerParametersBuilder()
            .addAugmentation(new LimitsUnderHttpTcpBuilder()
                .setServerLimits(new ServerLimitsBuilder()
                    .setMaxRequestBodySize(Uint32.valueOf(1234567))
                    .build())
                .build())
            .build());

        final var expected = new HTTPServerLimits(
            HTTPServerLimits.DEFAULT.maxInitialLineLength(),
            HTTPServerLimits.DEFAULT.maxHeaderSize(),
            HTTPServerLimits.DEFAULT.maxRequestChunkSize(),
            1234567,
            HTTPServerLimits.DEFAULT.maxFrameSize());
        assertEquals(expected, HTTPServerLimits.of(new HttpServerStackConfiguration(transport)));
    }

    private static HTTPServerLimits limits(final int maxInitialLineLength, final int maxHeaderSize,
            final int maxRequestBodySize) {
        return new HTTPServerLimits(maxInitialLineLength, maxHeaderSize, 8192, maxRequestBodySize, 16384);
    }

    private static TcpServerParameters tcpServerParameters() {
        return HTTPServerOverTcp.of("127.0.0.1", 8182).getHttpOverTcp().getTcpServerParameters();
    }

    private static EmbeddedChannel newSessionChannel(final HTTPServerLimits limits) {
        final var anchor = new ChannelInboundHandlerAdapter();
        final var channel = new EmbeddedChannel(anchor, new PipelinedHTTPServerSession(HTTPScheme.HTTP,
                Uint32.valueOf(8192)) {
            @Override
            protected PreparedRequest prepareRequest(final ImplementedMethod method, final URI targetUri,
                    final HttpHeaders headers) {
                throw new AssertionError("Unexpected request " + method + " " + targetUri);
            }
        });
        HTTPScheme.HTTP.initializeServerPipeline(channel.pipeline().context(anchor), limits);
        return channel;
    }

    private EmbeddedChannel newChannel(final HTTPServerLimits limits) {
        final var anchor = new ChannelInboundHandlerAdapter();
        final var sink = new SimpleChannelInboundHandler<FullHttpRequest>(FullHttpRequest.class, false) {
            @Override
            protected void channelRead0(final ChannelHandlerContext ctx, final FullHttpRequest msg) {
                received.add(msg);
            }
        };
        final var channel = new EmbeddedChannel(anchor, sink);
        HTTPScheme.HTTP.initializeServerPipeline(channel.pipeline().context(anchor), limits);
        return channel;
    }

    private static ByteBuf request(final int contentLength) {
        return Unpooled.copiedBuffer("""
            POST /rests/data HTTP/1.1\r
            Host: localhost\r
            Content-Type: application/json\r
            Content-Length: %s\r
            \r
            %s""".formatted(contentLength, "x".repeat(contentLength)), CharsetUtil.US_ASCII);
    }

    private static String statusLine(final EmbeddedChannel channel) {
        final var response = assertInstanceOf(ByteBuf.class, channel.readOutbound());
        final var text = response.toString(CharsetUtil.US_ASCII);
        response.release();
        final var eol = text.indexOf("\r\n");
        return eol == -1 ? text : text.substring(0, eol);
    }
}
