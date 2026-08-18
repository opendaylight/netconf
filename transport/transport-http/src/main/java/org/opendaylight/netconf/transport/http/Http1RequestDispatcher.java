/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.LastHttpContent;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A permanent HTTP/1.1 request dispatcher, sitting between {@link HttpServerCodec} and {@link HttpObjectAggregator}. It
 * examines every initial {@link HttpRequest} and selects the {@link RequestMode} in which that request, and only that
 * request, is going to be processed. The selected mode stays in effect until the request's {@link LastHttpContent} has
 * been forwarded, at which point the dispatcher resets and evaluates the next request arriving on the same connection.
 *
 * <p>A request using {@code Transfer-Encoding: chunked} is selected as {@link RequestMode#STREAMING} and routed past
 * {@link HttpObjectAggregator}, so that it and its body travel down the pipeline as they are decoded. Any other
 * request is selected as {@link RequestMode#AGGREGATED} and forwarded through the aggregator onto the existing
 * {@link FullHttpRequest} path, subject to the aggregator's content length limit.
 *
 * <p>The streaming path skips {@link HttpObjectAggregator} only: the messages are re-injected from the aggregator's
 * own {@link ChannelHandlerContext}, hence every other handler downstream of it, most notably the {@link AuthHandler},
 * observes a streamed request. This does not yet allow {@link AbstractBasicAuthHandler} to reject the entire request:
 * it consumes the rejected {@link HttpRequest}, but the following {@link HttpContent} messages continue to the
 * session. An aggregated request does not have this gap because its body is part of the rejected
 * {@link FullHttpRequest}.
 *
 * <p>Note streaming is disabled until {@link HTTPServerSession} can consume a streamed request body, as it accepts
 * only {@link FullHttpRequest}. While it is disabled, a request selected as {@link RequestMode#STREAMING} is
 * aggregated instead.
 */
@NonNullByDefault
final class Http1RequestDispatcher extends ChannelInboundHandlerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(Http1RequestDispatcher.class);

    /**
     * The name under which this handler is installed in the pipeline.
     */
    static final String HANDLER_NAME = "http1-dispatcher";
    /**
     * The name under which {@link HttpObjectAggregator} is installed in the pipeline. Looking the aggregator up by name
     * is deliberate: {@code ChannelPipeline.context(Class)} matches assignable types scanning from the head, and
     * {@link io.netty.handler.codec.http.HttpServerUpgradeHandler}, which the cleartext h2c pipeline places ahead
     * of this handler, extends {@link HttpObjectAggregator}.
     */
    static final String AGGREGATOR_NAME = "http1-aggregator";

    /**
     * The way the request currently being received is being processed.
     */
    private enum RequestMode {
        /**
         * The request is being forwarded through {@link HttpObjectAggregator}, ending up as a {@link FullHttpRequest}.
         */
        AGGREGATED,
        /**
         * The request is eligible for being forwarded past {@link HttpObjectAggregator}, ending up as an
         * {@link HttpRequest} followed by its {@link HttpContent}s.
         */
        STREAMING;
    }

    // True if a request selected as STREAMING is actually routed past the aggregator
    private final boolean streamingSupported;

    // True while the messages of a request are being dispatched, i.e. from its HttpRequest until its LastHttpContent
    private boolean inRequest;
    // Mode of that request, meaningful only while inRequest is true
    private RequestMode mode = RequestMode.AGGREGATED;

    // FIXME: remove this constructor and stream unconditionally once HTTPServerSession consumes a streamed request
    //        body. Until it does, routing a request past the aggregator leaves it unanswered, as the session accepts
    //        only FullHttpRequest -- including the SSE GET issued by ClientHttp1SseService, which carries
    //        Transfer-Encoding: chunked despite having no body.
    // FIXME: streaming also needs a way to suppress the remaining HttpContent after AuthHandler rejects its request.
    // FIXME: streaming must enforce the configured request-body size limit and provide backpressure before it is
    //        enabled, as bypassing HttpObjectAggregator also bypasses its MAX_HTTP_CONTENT_LENGTH enforcement.
    Http1RequestDispatcher() {
        this(false);
    }

    Http1RequestDispatcher(final boolean streamingSupported) {
        this.streamingSupported = streamingSupported;
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
        // Someone upstream has already aggregated this request: there is nothing to dispatch and no body to follow
        if (msg instanceof FullHttpRequest) {
            ctx.fireChannelRead(msg);
            return;
        }

        if (!inRequest) {
            if (msg instanceof HttpRequest request) {
                startRequest(ctx, request);
            } else {
                // The codec should never produce content outside of a request, just pass it on
                LOG.debug("{}: unexpected {} outside of a request", ctx.channel(), msg.getClass());
                ctx.fireChannelRead(msg);
            }
            return;
        }

        if (msg instanceof HttpContent content) {
            forward(ctx, content);
            if (content instanceof LastHttpContent) {
                LOG.debug("{}: {} request complete, next request will be evaluated afresh", ctx.channel(), mode);
                inRequest = false;
            }
        } else {
            LOG.debug("{}: unexpected {} in {} mode", ctx.channel(), msg.getClass(), mode);
            ctx.fireChannelRead(msg);
        }
    }

    private void startRequest(final ChannelHandlerContext ctx, final HttpRequest request) {
        // note: an expectation other than 100-continue is left to HttpObjectAggregator, which answers 417 and discards
        //       the body, i.e. more than this handler should be reimplementing. HttpUtil.isUnsupportedExpectation() is
        //       not public, hence the explicit comparison.
        final var expect = request.headers().get(HttpHeaderNames.EXPECT);
        final var expectationSupported = expect == null || HttpHeaderValues.CONTINUE.contentEqualsIgnoreCase(expect);

        if (!HttpUtil.isTransferEncodingChunked(request) || !expectationSupported) {
            mode = RequestMode.AGGREGATED;
        } else if (streamingSupported) {
            mode = RequestMode.STREAMING;
        } else {
            mode = RequestMode.AGGREGATED;
        }
        LOG.debug("{}: {} {} dispatched as {}", ctx.channel(), request.method(), request.uri(), mode);

        if (mode == RequestMode.STREAMING && HttpUtil.is100ContinueExpected(request)) {
            // HttpObjectAggregator answers this for the requests it aggregates, hence a request routed past it has to
            // have its expectation answered here.
            request.headers().remove(HttpHeaderNames.EXPECT);
            ctx.writeAndFlush(new DefaultFullHttpResponse(request.protocolVersion(), HttpResponseStatus.CONTINUE))
                .addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
        }

        // FullHttpRequest is the only HttpRequest which is also a LastHttpContent, and those have been dealt
        // with in channelRead() already, so there is always a body to follow here
        inRequest = true;
        forward(ctx, request);
    }

    @SuppressWarnings("checkstyle:MissingSwitchDefault")
    private void forward(final ChannelHandlerContext ctx, final Object msg) {
        switch (mode) {
            case AGGREGATED -> ctx.fireChannelRead(msg);
            case STREAMING -> {
                // Firing from the aggregator's own context delivers the message to the handler following it, i.e.
                // skips aggregation without skipping anything else, most notably not the AuthHandler
                final var aggregator = ctx.pipeline().context(AGGREGATOR_NAME);
                if (aggregator != null) {
                    aggregator.fireChannelRead(msg);
                } else {
                    // Should not happen: whoever installs us installs the aggregator as well. Fall back to
                    // aggregating, which is at least well-defined and does not leak the message.
                    LOG.error("{}: no handler named {}, aggregating {} instead", ctx.channel(), AGGREGATOR_NAME,
                        msg.getClass());
                    ctx.fireChannelRead(msg);
                }
            }
        }
    }
}
