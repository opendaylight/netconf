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
 * examines every initial {@link HttpRequest} and selects the {@link RequestMode} in which that request, and only
 * that request, is going to be processed:
 * <ul>
 *   <li>a request using {@code Transfer-Encoding: chunked} is routed past {@link HttpObjectAggregator}, so that it and
 *       its body travel down the pipeline as they are decoded. Note that {@link HTTPServerSession} does not consume
 *       them yet: doing so requires a streaming request body, which is not implemented, hence such a request currently
 *       goes unanswered, and</li>
 *   <li>any other request is forwarded down the pipeline, i.e. through {@link HttpObjectAggregator} and onto the
 *       existing {@link FullHttpRequest} path, subject to the aggregator's content length limit.</li>
 * </ul>
 *
 * <p>Note the streaming path skips {@link HttpObjectAggregator} only: the messages are re-injected from the
 * aggregator's own {@link ChannelHandlerContext}, hence every other handler downstream of it, most notably the
 * {@link AuthHandler}, observes a streamed request just like it observes an aggregated one.
 *
 * <p>The selected mode stays in effect until the request's {@link LastHttpContent} has been forwarded, at which point
 * the dispatcher resets and evaluates the next request arriving on the same connection.
 */
@NonNullByDefault
final class Http1RequestDispatcher extends ChannelInboundHandlerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(Http1RequestDispatcher.class);

    static final String HANDLER_NAME = "http1-dispatcher";
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
         * The request is being forwarded past {@link HttpObjectAggregator}, ending up as an {@link HttpRequest}
         * followed by its {@link HttpContent}s.
         */
        STREAMING;

    }

    // True while the messages of a request are being dispatched, i.e. from its HttpRequest until its LastHttpContent
    private boolean inRequest;
    // Mode of that request, meaningful only while inRequest is true
    private RequestMode mode = RequestMode.AGGREGATED;

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
                inRequest = false;
            }
        } else {
            LOG.debug("{}: unexpected {} in {} mode", ctx.channel(), msg.getClass(), mode);
            ctx.fireChannelRead(msg);
        }
    }

    private void startRequest(final ChannelHandlerContext ctx, final HttpRequest request) {
        // FIXME: requests selected for STREAMING are currently not serviced: HTTPServerSession only accepts
        //        FullHttpRequest, so they travel to the end of the pipeline and are discarded, leaving the client
        //        without a response. Implementing a streaming request body is what makes this branch functional.
        final var selected = HttpUtil.isTransferEncodingChunked(request) ? RequestMode.STREAMING
            : RequestMode.AGGREGATED;
        LOG.debug("{}: dispatching {} {} as {}", ctx.channel(), request.method(), request.uri(), selected);

        if (selected == RequestMode.STREAMING && HttpUtil.is100ContinueExpected(request)) {
            // HttpObjectAggregator would have taken care of this for us, but we are bypassing it
            request.headers().remove(HttpHeaderNames.EXPECT);
            ctx.writeAndFlush(new DefaultFullHttpResponse(request.protocolVersion(), HttpResponseStatus.CONTINUE))
                .addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
        }

        mode = selected;
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
                // skips aggregation without skipping anything else
                final var aggregator = ctx.pipeline().context(AGGREGATOR_NAME);
                if (aggregator != null) {
                    aggregator.fireChannelRead(msg);
                } else {
                    // Should not happen: whoever installs us installs the aggregator as well. Fall back to aggregating
                    // the request, which is at least well-defined and does not leak the message.
                    LOG.error("{}: no handler named {}, aggregating {} instead", ctx.channel(), AGGREGATOR_NAME,
                        msg.getClass());
                    ctx.fireChannelRead(msg);
                }
            }
        }
    }
}
