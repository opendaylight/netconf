/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.TooLongHttpHeaderException;
import io.netty.handler.codec.http.TooLongHttpLineException;
import io.netty.util.CharsetUtil;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opendaylight.yangtools.yang.common.Uint32;

class HTTPSchemeTest {
    private final List<FullHttpRequest> received = new ArrayList<>();

    @AfterEach
    void afterEach() {
        received.forEach(FullHttpRequest::release);
    }

    @ParameterizedTest
    @MethodSource
    void hostUriOfValid(final String expected, final HTTPScheme scheme, final String host) throws Exception {
        assertEquals(URI.create(expected), scheme.hostUriOf(host));
    }

    private static List<Arguments> hostUriOfValid() {
        return List.of(
            Arguments.of("http://foo", HTTPScheme.HTTP, "foo"),
            Arguments.of("https://bar:1234", HTTPScheme.HTTPS, "bar:1234"));
    }

    @Test
    void hostUriOfInvalidPort() {
        final var ex = assertThrows(URISyntaxException.class, () -> HTTPScheme.HTTP.hostUriOf("foo:abc"));
        assertEquals("Illegal character in port number at index 11: http://foo:abc", ex.getMessage());
    }

    @Test
    void hostUriOfInvalidHostname() {
        final var ex = assertThrows(URISyntaxException.class, () -> HTTPScheme.HTTP.hostUriOf("--"));
        assertEquals("Illegal character in hostname at index 7: http://--", ex.getMessage());
    }

    @Test
    void hostUriOfWithUser() {
        final var ex = assertThrows(URISyntaxException.class, () -> HTTPScheme.HTTP.hostUriOf("user@host"));
        assertEquals("Host contains userinfo: user@host", ex.getMessage());
    }

    @Test
    void requestBodyOverPreviousLimitIsAggregated() {
        final var channel = newChannel(8192, 16384, 10485760);
        channel.writeInbound(request(29007));
        channel.runPendingTasks();

        assertEquals(1, received.size());
        final var request = received.getFirst();
        assertTrue(request.decoderResult().isSuccess());
        assertEquals(29007, request.content().readableBytes());
        channel.finishAndReleaseAll();
    }

    @Test
    void oversizedRequestBodyIsRejected() {
        final var channel = newChannel(4096, 16384, 1024);
        channel.writeInbound(request(4096));
        channel.runPendingTasks();

        assertTrue(received.isEmpty(), "an oversized request must not reach the session");
        assertEquals(413, statusCode(channel));
        channel.finishAndReleaseAll();
    }

    @Test
    void oversizedRequestLineIsReportedAsTooLongLine() {
        final var channel = newChannel(64, 16384, 10485760);
        channel.writeInbound(Unpooled.copiedBuffer(
            "GET /rests/data/" + "a".repeat(128) + " HTTP/1.1\r\nHost: localhost\r\n\r\n", CharsetUtil.US_ASCII));
        channel.runPendingTasks();

        assertEquals(1, received.size());
        final var cause = received.getFirst().decoderResult().cause();
        assertInstanceOf(TooLongHttpLineException.class, cause);
        channel.finishAndReleaseAll();
    }

    @Test
    void oversizedHeaderSectionIsReportedAsTooLongHeader() {
        final var channel = newChannel(4096, 128, 10485760);
        channel.writeInbound(Unpooled.copiedBuffer(
            "GET /rests/data HTTP/1.1\r\nHost: localhost\r\nX-Padding: " + "a".repeat(256) + "\r\n\r\n",
            CharsetUtil.US_ASCII));
        channel.runPendingTasks();

        assertEquals(1, received.size());
        final var cause = received.getFirst().decoderResult().cause();
        assertInstanceOf(TooLongHttpHeaderException.class, cause);
        channel.finishAndReleaseAll();
    }

    @Test
    void oversizedRequestLineIsAnsweredWith414() {
        final var channel = newSessionChannel(64, 16384, 10485760);
        channel.writeInbound(Unpooled.copiedBuffer(
            "GET /rests/data/" + "a".repeat(128) + " HTTP/1.1\r\nHost: localhost\r\n\r\n", CharsetUtil.US_ASCII));
        channel.runPendingTasks();

        assertEquals(414, statusCode(channel));
        assertFalse(channel.isActive());
        channel.finishAndReleaseAll();
    }

    @Test
    void oversizedHeaderSectionIsAnsweredWith431() {
        final var channel = newSessionChannel(4096, 128, 10485760);
        channel.writeInbound(Unpooled.copiedBuffer(
            "GET /rests/data HTTP/1.1\r\nHost: localhost\r\nx-http2-stream-id: 42\r\nX-Padding: "
                + "a".repeat(256) + "\r\n\r\n",
            CharsetUtil.US_ASCII));
        channel.runPendingTasks();

        final var response = responseText(channel);
        assertEquals(431, statusCode(response));
        assertFalse(response.contains("x-http2-stream-id"));
        assertFalse(channel.isActive());
        channel.finishAndReleaseAll();
    }

    private static EmbeddedChannel newSessionChannel(final int maxInitialLineLength, final int maxHeaderSize,
            final int maxRequestBodySize) {
        final var anchor = new ChannelInboundHandlerAdapter();
        final var channel = new EmbeddedChannel(anchor, new PipelinedHTTPServerSession(HTTPScheme.HTTP,
                Uint32.valueOf(8192)) {
            @Override
            protected PreparedRequest prepareRequest(final ImplementedMethod method, final URI targetUri,
                    final HttpHeaders headers) {
                throw new AssertionError("Unexpected request " + method + " " + targetUri);
            }
        });
        initializePipeline(channel, anchor, maxInitialLineLength, maxHeaderSize, maxRequestBodySize);
        return channel;
    }

    private EmbeddedChannel newChannel(final int maxInitialLineLength, final int maxHeaderSize,
            final int maxRequestBodySize) {
        final var anchor = new ChannelInboundHandlerAdapter();
        final var sink = new SimpleChannelInboundHandler<FullHttpRequest>(FullHttpRequest.class, false) {
            @Override
            protected void channelRead0(final ChannelHandlerContext ctx, final FullHttpRequest msg) {
                received.add(msg);
            }
        };
        final var channel = new EmbeddedChannel(anchor, sink);
        initializePipeline(channel, anchor, maxInitialLineLength, maxHeaderSize, maxRequestBodySize);
        return channel;
    }

    private static void initializePipeline(final EmbeddedChannel channel, final ChannelInboundHandlerAdapter anchor,
            final int maxInitialLineLength, final int maxHeaderSize, final int maxRequestBodySize) {
        HTTPScheme.HTTP.initializeServerPipeline(channel.pipeline().context(anchor), Uint32.valueOf(16384),
            Uint32.valueOf(maxInitialLineLength), Uint32.valueOf(maxHeaderSize), Uint32.valueOf(8192),
            Uint32.valueOf(maxRequestBodySize));
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

    private static int statusCode(final EmbeddedChannel channel) {
        return statusCode(responseText(channel));
    }

    private static int statusCode(final String response) {
        final var eol = response.indexOf("\r\n");
        final var statusLine = eol == -1 ? response : response.substring(0, eol);
        return Integer.parseInt(statusLine.split(" ", 3)[1]);
    }

    private static String responseText(final EmbeddedChannel channel) {
        final var response = assertInstanceOf(ByteBuf.class, channel.readOutbound());
        final var text = response.toString(CharsetUtil.US_ASCII);
        response.release();
        return text;
    }
}
