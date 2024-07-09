/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static io.netty.buffer.Unpooled.EMPTY_BUFFER;
import static io.netty.handler.codec.http2.HttpConversionUtil.ExtensionHeaderNames.SCHEME;
import static io.netty.handler.codec.http2.HttpConversionUtil.ExtensionHeaderNames.STREAM_ID;
import static java.util.Objects.requireNonNull;
import static org.opendaylight.netconf.transport.http.SseUtils.processChunks;

import com.google.common.util.concurrent.FutureCallback;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpScheme;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import org.opendaylight.yangtools.concepts.Registration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ClientSseHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(ClientSseHandler.class);

    private final String uri;
    private final FutureCallback<Registration> requestCallback;
    private final EventStreamListener listener;
    private final Runnable onClose;
    private final AtomicBoolean streaming = new AtomicBoolean(false);
    private final AtomicBoolean awaitingErrorMessage = new AtomicBoolean(false);
    private GenericFutureListener<? extends Future<Void>> closeListener;

    ClientSseHandler(final String uri, final EventStreamListener listener,
            final FutureCallback<Registration> requestCallback, final Runnable onClose) {
        this.uri = requireNonNull(uri);
        this.listener = requireNonNull(listener);
        this.requestCallback = requestCallback;
        this.onClose = onClose;
    }

    String uri() {
        return uri;
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) throws Exception {
        // send SSE stream request
        final var request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri, EMPTY_BUFFER);
        request.headers()
            .set(HttpHeaderNames.ACCEPT, SseUtils.TEXT_EVENT_STREAM)
            .set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE)
            .set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);

        final var http2dispatcher = ctx.pipeline().get(ClientHttp2RequestDispatcher.class);
        if (http2dispatcher != null) {
            // http 2 specific headers
            final var ssl = ctx.pipeline().get(SslHandler.class) != null;
            request.headers()
                .setInt(STREAM_ID.text(), http2dispatcher.nextStreamId())
                .set(SCHEME.text(), ssl ? HttpScheme.HTTPS.name() : HttpScheme.HTTP.name());
        }

        ctx.channel().writeAndFlush(request);
        LOG.info("SSE request sent to {}", uri);
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
        LOG.info("RECEIVED {} \n {}", msg.getClass(), msg);

        if (!streaming.get() && msg instanceof HttpResponse response) {
            // response header to stream request
            if (HttpResponseStatus.OK.equals(response.status())
                && response.headers().contains(HttpHeaderNames.CONTENT_TYPE, SseUtils.TEXT_EVENT_STREAM, true)) {
                // stream request accepted
                streaming.set(true);
                listener.onStreamStart();
                // setup on channel close listener
                closeListener = ignored -> listener.onStreamEnd();
                ctx.channel().closeFuture().addListener(closeListener);
                // notify SSE request succeeded with Registration (Closable)
                requestCallback.onSuccess(() -> {
                    if (!ctx.isRemoved() && ctx.channel().isActive()) {
                        // terminate connection if current handler is attached to active connection
                        // so the server is notified the stream no longer consumed
                        ctx.channel().close();
                    }
                });
            } else {
                // stream request declined
                awaitingErrorMessage.set(true);
            }

        } else if (awaitingErrorMessage.get() && msg instanceof HttpContent content) {
            // decline response body
            final var errorMessage = new String(ByteBufUtil.getBytes(content.content()), StandardCharsets.UTF_8);
            requestCallback.onFailure(new IllegalStateException(errorMessage));
            if (content instanceof LastHttpContent) {
                ctx.pipeline().remove(this);
            }

        } else if (streaming.get() && msg instanceof HttpContent chunk) {
            // event chunk
            processChunks(chunk.content(), listener);

            // if last one
            if (msg instanceof LastHttpContent) {
                // end stream
                LOG.info("SSE stream ended for URI={}", uri);
                listener.onStreamEnd();
                streaming.set(false);
                // nothing to expect -- remove self
                ctx.pipeline().remove(this);
            }
        } else {
            LOG.warn("Unexpected SSE message {}", msg);
        }
    }

    @Override
    public void handlerRemoved(final ChannelHandlerContext ctx) throws Exception {
        if (closeListener != null) {
            ctx.channel().closeFuture().removeListener(closeListener);
        }
        if (onClose != null) {
            onClose.run();
        }
    }
}
