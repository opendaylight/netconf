/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.CharsetUtil;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class Http1RequestDispatcherTest {
    /**
     * Records what reaches the pipeline downstream of {@link HttpObjectAggregator}. This is where the
     * {@link AuthHandler} and {@link HTTPServerSession} live in a real pipeline, so it is what a dispatched request
     * must still be seen by.
     */
    private static final class Probe extends ChannelInboundHandlerAdapter {
        private final List<Class<?>> observed = new ArrayList<>();

        @Override
        public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
            observed.add(msg.getClass());
            ctx.fireChannelRead(msg);
        }
    }

    private static final int MAX_CONTENT_LENGTH = 1024;

    private final List<EmbeddedChannel> channels = new ArrayList<>();
    private final Probe probe = new Probe();

    private EmbeddedChannel channel;

    @BeforeEach
    void beforeEach() {
        channel = newChannel();
        channel.pipeline()
            .addLast(Http1RequestDispatcher.HANDLER_NAME, new Http1RequestDispatcher(MAX_CONTENT_LENGTH))
            .addLast("probe", probe);
    }

    @AfterEach
    void afterEach() {
        for (var testChannel : channels) {
            // An unsupported expectation leaves the aggregator discarding content until the next request. Remove it
            // first so its handler cleanup releases any state without reporting a premature channel closure.
            testChannel.pipeline().remove(HttpObjectAggregator.class);
            testChannel.finishAndReleaseAll();
        }
    }

    /**
     * A request declaring a Content-Length is AGGREGATED: nothing reaches the rest of the pipeline until its
     * LastHttpContent has arrived, and then it arrives there as a single FullHttpRequest.
     */
    @Test
    void requestWithContentLengthIsAggregated() {
        final var request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/restconf/data");
        request.headers().set(HttpHeaderNames.CONTENT_LENGTH, 5);
        channel.writeInbound(request);
        // the aggregator is holding on to the request
        assertEquals(List.of(), probe.observed);
        assertNull(channel.readInbound());

        channel.writeInbound(new DefaultLastHttpContent(Unpooled.copiedBuffer("hello", CharsetUtil.UTF_8)));

        // a single aggregated message made it past the aggregator
        assertEquals(1, probe.observed.size());
        assertAggregated("hello", channel.readInbound());
        assertNull(channel.readInbound());
    }

    /**
     * A request with no body at all is AGGREGATED just the same, ending up as a FullHttpRequest with empty content.
     */
    @Test
    void requestWithoutBodyIsAggregated() {
        final var request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/restconf/data");
        channel.writeInbound(request);
        channel.writeInbound(LastHttpContent.EMPTY_LAST_CONTENT);

        assertAggregated("", channel.readInbound());
    }

    /**
     * Interposing the dispatcher must not weaken the aggregator's content length limit: an oversized request is still
     * answered 413 and never let through.
     */
    @Test
    void oversizedRequestIsStillRejectedByAggregator() {
        final var request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/restconf/data");
        request.headers().set(HttpHeaderNames.CONTENT_LENGTH, MAX_CONTENT_LENGTH + 1);
        channel.writeInbound(request);

        assertStatus(HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE, channel.readOutbound());
        // nothing was let through to the rest of the pipeline
        assertEquals(List.of(), probe.observed);
    }

    /**
     * A chunked request would be STREAMING, but while streaming is disabled it is aggregated instead, because
     * HTTPServerSession consumes only FullHttpRequest.
     */
    @Test
    // FIXME NETCONF-1668: rework this test after implementation is settled
    void chunkedRequestIsAggregatedUntilStreamingIsImplemented() {
        channel.writeInbound(chunkedRequest());
        assertNull(channel.readInbound());

        channel.writeInbound(new DefaultHttpContent(Unpooled.copiedBuffer("first", CharsetUtil.UTF_8)));
        assertNull(channel.readInbound());

        channel.writeInbound(new DefaultLastHttpContent(Unpooled.copiedBuffer("last", CharsetUtil.UTF_8)));
        assertAggregated("firstlast", channel.readInbound());
    }

    /**
     * A chunked request still reaches everything installed after the aggregator, which in a real pipeline is the
     * AuthHandler and the session.
     */
    @Test
    void chunkedRequestReachesHandlersAfterAggregator() {
        channel.writeInbound(chunkedRequest());
        channel.writeInbound(new DefaultHttpContent(Unpooled.copiedBuffer("body", CharsetUtil.UTF_8)));
        channel.writeInbound(LastHttpContent.EMPTY_LAST_CONTENT);

        assertEquals(1, probe.observed.size());
        assertAggregated("body", channel.readInbound());
    }

    /**
     * The mode is picked per request, not per connection: requests pipelined on a single connection are evaluated
     * independently of one another.
     */
    @Test
    void pipelinedRequestsAreEvaluatedSeparately() {
        // a chunked request ...
        channel.writeInbound(chunkedRequest());
        channel.writeInbound(new DefaultLastHttpContent(Unpooled.copiedBuffer("one", CharsetUtil.UTF_8)));
        assertAggregated("one", channel.readInbound());

        // ... followed by a fixed-length one on the same connection
        final var second = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/restconf/data");
        second.headers().set(HttpHeaderNames.CONTENT_LENGTH, 2);
        channel.writeInbound(second);
        assertNull(channel.readInbound());

        channel.writeInbound(new DefaultLastHttpContent(Unpooled.copiedBuffer("ok", CharsetUtil.UTF_8)));
        assertAggregated("ok", channel.readInbound());
    }

    /**
     * An aggregated request expecting 100-continue is answered by the aggregator alone: the dispatcher must not chime
     * in with a second response, and the met expectation must not be visible downstream.
     */
    @Test
    void expect100ContinueIsAnsweredExactlyOnce() {
        final var request = chunkedRequest();
        request.headers().set(HttpHeaderNames.EXPECT, HttpHeaderValues.CONTINUE);
        channel.writeInbound(request);

        assertStatus(HttpResponseStatus.CONTINUE, channel.readOutbound());
        assertNull(channel.readOutbound());

        channel.writeInbound(new DefaultLastHttpContent(Unpooled.copiedBuffer("ok", CharsetUtil.UTF_8)));
        final var aggregated = assertInstanceOf(FullHttpRequest.class, channel.readInbound());
        try {
            // the expectation has been met, downstream handlers should not see it
            assertFalse(aggregated.headers().contains(HttpHeaderNames.EXPECT));
        } finally {
            aggregated.release();
        }
    }

    /**
     * ClientHttp1SseService issues its SSE GET with {@code Transfer-Encoding: chunked} even though it carries no body,
     * hence the server never decodes it into a FullHttpRequest on its own. Routing it past the aggregator leaves it
     * unanswered, which silently disables HTTP/1.1 event streams.
     */
    @Test
    void sseStyleChunkedGetIsStillDeliveredAsFullHttpRequest() {
        final var wireProbe = new Probe();
        final var wireChannel = newChannel();
        wireChannel.pipeline()
            .addLast(new HttpServerCodec())
            .addLast(Http1RequestDispatcher.HANDLER_NAME, new Http1RequestDispatcher(MAX_CONTENT_LENGTH))
            .addLast("probe", wireProbe);

        // exactly what ClientHttp1SseService puts on the wire
        wireChannel.writeInbound(Unpooled.copiedBuffer("""
            GET /streams/foo HTTP/1.1\r
            accept: text/event-stream\r
            connection: keep-alive\r
            transfer-encoding: chunked\r
            host: localhost\r
            \r
            0\r
            \r
            """, CharsetUtil.UTF_8));

        // exactly one message, and it is a fully aggregated request, not a bare header plus content
        assertEquals(1, wireProbe.observed.size());
        assertAggregated("", wireChannel.readInbound());
    }

    /**
     * A pipeline with the streaming path enabled, i.e. what the dispatcher does once HTTPServerSession can consume a
     * streamed request body.
     */
    private EmbeddedChannel streamingChannel() {
        final var streaming = newChannel();
        streaming.pipeline()
            .addLast(Http1RequestDispatcher.HANDLER_NAME, new Http1RequestDispatcher(MAX_CONTENT_LENGTH, true));
        return streaming;
    }

    /**
     * With streaming enabled a chunked request and every one of its chunks are forwarded as they are decoded, with the
     * aggregator holding on to none of them.
     */
    @Test
    void streamedRequestBypassesAggregator() {
        final var streaming = streamingChannel();
        final var request = chunkedRequest();
        streaming.writeInbound(request);
        // forwarded as-is, the aggregator is not holding on to it
        assertSame(request, streaming.readInbound());

        streaming.writeInbound(new DefaultHttpContent(Unpooled.copiedBuffer("first", CharsetUtil.UTF_8)));
        assertContent("first", streaming.readInbound());

        streaming.writeInbound(new DefaultLastHttpContent(Unpooled.copiedBuffer("last", CharsetUtil.UTF_8)));
        assertContent("last", streaming.readInbound());
    }

    /**
     * Skipping the aggregator must not skip anything installed after it, which in a real pipeline is the AuthHandler
     * and the session.
     */
    @Test
    void streamedRequestStillReachesHandlersAfterAggregator() {
        final var streamingProbe = new Probe();
        final var streaming = streamingChannel();
        streaming.pipeline().addLast("probe", streamingProbe);
        streaming.writeInbound(chunkedRequest());
        streaming.writeInbound(new DefaultHttpContent(Unpooled.copiedBuffer("body", CharsetUtil.UTF_8)));
        streaming.writeInbound(LastHttpContent.EMPTY_LAST_CONTENT);

        assertEquals(List.of(DefaultHttpRequest.class, DefaultHttpContent.class,
            LastHttpContent.EMPTY_LAST_CONTENT.getClass()), streamingProbe.observed);
    }

    /**
     * A request rejected by the decoder must stay on the aggregated path even if its surviving headers describe
     * chunked framing, so the session receives a FullHttpRequest and can turn its failed DecoderResult into a 400.
     */
    @Test
    void failedChunkedRequestIsAggregated() {
        final var wireChannel = newChannel();
        wireChannel.pipeline()
            .addLast(new HttpServerCodec())
            .addLast(Http1RequestDispatcher.HANDLER_NAME,
                new Http1RequestDispatcher(MAX_CONTENT_LENGTH, true));

        wireChannel.writeInbound(Unpooled.copiedBuffer("""
            POST /restconf/data HTTP/1.1\r
            host: localhost\r
            content-length: 1\r
            transfer-encoding: chunked\r
            \r
            """, CharsetUtil.US_ASCII));

        final var request = assertInstanceOf(FullHttpRequest.class, wireChannel.readInbound());
        try {
            assertFalse(request.decoderResult().isSuccess());
        } finally {
            request.release();
        }
    }

    /**
     * A streamed request expecting 100-continue has to be answered by the dispatcher, as the aggregator, which would
     * otherwise do that, no longer sees the request.
     */
    @Test
    void streamedRequestIsAnswered100Continue() {
        final var streaming = streamingChannel();
        final var request = chunkedRequest();
        request.headers().set(HttpHeaderNames.EXPECT, HttpHeaderValues.CONTINUE);
        streaming.writeInbound(request);

        assertStatus(HttpResponseStatus.CONTINUE, streaming.readOutbound());
        assertNull(streaming.readOutbound());
        assertFalse(request.headers().contains(HttpHeaderNames.EXPECT));
        assertSame(request, streaming.readInbound());
    }

    /**
     * A chunked request carrying an expectation we cannot satisfy is aggregated regardless, as answering 417 and
     * discarding the body is more than the dispatcher should be reimplementing.
     */
    @Test
    void unsupportedExpectationIsLeftToTheAggregator() {
        final var streaming = streamingChannel();
        final var request = chunkedRequest();
        request.headers().set(HttpHeaderNames.EXPECT, "chocolate");
        streaming.writeInbound(request);

        assertStatus(HttpResponseStatus.EXPECTATION_FAILED, streaming.readOutbound());
        assertNull(streaming.readInbound());
    }

    /**
     * The dispatcher resets on LastHttpContent, so a streamed request can be followed by an aggregated one on the same
     * connection.
     */
    @Test
    void streamedRequestIsFollowedByAnAggregatedOne() {
        final var streaming = streamingChannel();
        streaming.writeInbound(chunkedRequest());
        assertInstanceOf(HttpRequest.class, streaming.readInbound());
        streaming.writeInbound(LastHttpContent.EMPTY_LAST_CONTENT);
        assertInstanceOf(LastHttpContent.class, streaming.readInbound());

        final var second = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/restconf/data");
        second.headers().set(HttpHeaderNames.CONTENT_LENGTH, 2);
        streaming.writeInbound(second);
        assertNull(streaming.readInbound());

        streaming.writeInbound(new DefaultLastHttpContent(Unpooled.copiedBuffer("ok", CharsetUtil.UTF_8)));
        assertAggregated("ok", streaming.readInbound());
    }

    private EmbeddedChannel newChannel() {
        final var newChannel = new EmbeddedChannel();
        channels.add(newChannel);
        return newChannel;
    }

    private static void assertContent(final String expected, final Object msg) {
        final var content = assertInstanceOf(HttpContent.class, msg);
        try {
            assertEquals(expected, content.content().toString(CharsetUtil.UTF_8));
        } finally {
            content.release();
        }
    }

    private static DefaultHttpRequest chunkedRequest() {
        final var request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/restconf/data");
        request.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
        return request;
    }

    private static void assertAggregated(final String expectedContent, final Object msg) {
        final var request = assertInstanceOf(FullHttpRequest.class, msg);
        try {
            assertEquals(expectedContent, request.content().toString(CharsetUtil.UTF_8));
        } finally {
            request.release();
        }
    }

    private static void assertStatus(final HttpResponseStatus expected, final Object msg) {
        final var response = assertInstanceOf(FullHttpResponse.class, msg);
        try {
            assertEquals(expected, response.status());
        } finally {
            response.release();
        }
    }
}
