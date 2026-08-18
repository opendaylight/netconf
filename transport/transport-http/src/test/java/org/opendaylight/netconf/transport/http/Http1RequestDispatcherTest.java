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
     * {@link AuthHandler} and {@link HTTPServerSession} live in a real pipeline, so it is what a streamed request must
     * still be seen by.
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

    private final Probe probe = new Probe();

    private EmbeddedChannel channel;

    @BeforeEach
    void beforeEach() {
        channel = new EmbeddedChannel();
        channel.pipeline()
            .addLast(Http1RequestDispatcher.HANDLER_NAME, new Http1RequestDispatcher())
            .addLast(Http1RequestDispatcher.AGGREGATOR_NAME, new HttpObjectAggregator(MAX_CONTENT_LENGTH))
            .addLast("probe", probe);
    }

    @AfterEach
    void afterEach() {
        channel.finishAndReleaseAll();
    }

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
        final var aggregated = assertInstanceOf(FullHttpRequest.class, channel.readInbound());
        try {
            assertEquals("hello", aggregated.content().toString(CharsetUtil.UTF_8));
        } finally {
            aggregated.release();
        }
        assertNull(channel.readInbound());
    }

    @Test
    void requestWithoutBodyIsAggregated() {
        final var request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/restconf/data");
        channel.writeInbound(request);
        channel.writeInbound(LastHttpContent.EMPTY_LAST_CONTENT);

        final var aggregated = assertInstanceOf(FullHttpRequest.class, channel.readInbound());
        try {
            assertFalse(aggregated.content().isReadable());
        } finally {
            aggregated.release();
        }
    }

    @Test
    void oversizedRequestIsStillRejectedByAggregator() {
        // the aggregator's content length limit must remain in force on the aggregated path
        final var request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/restconf/data");
        request.headers().set(HttpHeaderNames.CONTENT_LENGTH, MAX_CONTENT_LENGTH + 1);
        channel.writeInbound(request);

        final var response = assertInstanceOf(FullHttpResponse.class, channel.readOutbound());
        try {
            assertEquals(HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE, response.status());
        } finally {
            response.release();
        }
        // nothing was let through to the rest of the pipeline
        assertEquals(List.of(), probe.observed);
    }

    @Test
    void chunkedRequestBypassesAggregator() {
        final var request = chunkedRequest();
        channel.writeInbound(request);
        // the request has been forwarded past the aggregator as-is
        assertSame(request, channel.readInbound());

        channel.writeInbound(new DefaultHttpContent(Unpooled.copiedBuffer("first", CharsetUtil.UTF_8)));
        assertContent("first", channel.readInbound());

        channel.writeInbound(new DefaultLastHttpContent(Unpooled.copiedBuffer("last", CharsetUtil.UTF_8)));
        final var last = assertInstanceOf(LastHttpContent.class, channel.readInbound());
        try {
            assertEquals("last", last.content().toString(CharsetUtil.UTF_8));
        } finally {
            last.release();
        }
    }

    @Test
    void chunkedRequestStillReachesHandlersAfterAggregator() {
        // skipping the aggregator must not skip anything installed after it; in a real pipeline that is the
        // AuthHandler and the session
        channel.writeInbound(chunkedRequest());
        channel.writeInbound(new DefaultHttpContent(Unpooled.copiedBuffer("body", CharsetUtil.UTF_8)));
        channel.writeInbound(LastHttpContent.EMPTY_LAST_CONTENT);

        assertEquals(List.of(DefaultHttpRequest.class, DefaultHttpContent.class, LastHttpContent.EMPTY_LAST_CONTENT
            .getClass()), probe.observed);
    }

    @Test
    void modeIsReevaluatedForEachRequest() {
        // a streamed request ...
        channel.writeInbound(chunkedRequest());
        assertInstanceOf(HttpRequest.class, channel.readInbound());
        channel.writeInbound(LastHttpContent.EMPTY_LAST_CONTENT);
        assertInstanceOf(LastHttpContent.class, channel.readInbound());

        // ... followed by an aggregated one on the same connection
        final var second = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/restconf/data");
        second.headers().set(HttpHeaderNames.CONTENT_LENGTH, 2);
        channel.writeInbound(second);
        assertNull(channel.readInbound());

        channel.writeInbound(new DefaultLastHttpContent(Unpooled.copiedBuffer("ok", CharsetUtil.UTF_8)));
        final var aggregated = assertInstanceOf(FullHttpRequest.class, channel.readInbound());
        try {
            assertEquals("ok", aggregated.content().toString(CharsetUtil.UTF_8));
        } finally {
            aggregated.release();
        }
    }

    @Test
    void chunkedRequestAnswers100Continue() {
        final var request = chunkedRequest();
        request.headers().set(HttpHeaderNames.EXPECT, HttpHeaderValues.CONTINUE);
        channel.writeInbound(request);

        final var response = assertInstanceOf(FullHttpResponse.class, channel.readOutbound());
        try {
            assertEquals(HttpResponseStatus.CONTINUE, response.status());
        } finally {
            response.release();
        }
        // only one, and the expectation has been met, so downstream handlers should not see it
        assertNull(channel.readOutbound());
        assertFalse(request.headers().contains(HttpHeaderNames.EXPECT));
        assertSame(request, channel.readInbound());
    }

    @Test
    void dispatcherDoesNotAnswer100ContinueForAggregatedRequest() {
        // the aggregator is the one dealing with this, the dispatcher must not chime in
        final var request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/restconf/data");
        request.headers().set(HttpHeaderNames.CONTENT_LENGTH, 2);
        request.headers().set(HttpHeaderNames.EXPECT, HttpHeaderValues.CONTINUE);
        channel.writeInbound(request);

        final var response = assertInstanceOf(FullHttpResponse.class, channel.readOutbound());
        try {
            assertEquals(HttpResponseStatus.CONTINUE, response.status());
        } finally {
            response.release();
        }
        assertNull(channel.readOutbound());

        // finish the request, so that the aggregator is not left mid-message
        channel.writeInbound(new DefaultLastHttpContent(Unpooled.copiedBuffer("ok", CharsetUtil.UTF_8)));
        assertInstanceOf(FullHttpRequest.class, channel.readInbound()).release();
    }

    private static DefaultHttpRequest chunkedRequest() {
        final var request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/restconf/data");
        request.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
        return request;
    }

    private static void assertContent(final String expected, final Object msg) {
        final var content = assertInstanceOf(HttpContent.class, msg);
        try {
            assertEquals(expected, content.content().toString(CharsetUtil.UTF_8));
        } finally {
            content.release();
        }
    }
}
