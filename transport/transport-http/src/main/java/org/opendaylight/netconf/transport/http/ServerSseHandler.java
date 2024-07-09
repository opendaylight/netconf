/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static java.util.Objects.requireNonNull;
import static org.opendaylight.netconf.transport.http.Http2Utils.copyStreamId;
import static org.opendaylight.netconf.transport.http.SseUtils.chunksOf;

import com.google.common.util.concurrent.FutureCallback;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.LastHttpContent;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.opendaylight.yangtools.concepts.Registration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server side Server-Sent Event (SSE) Handler.
 *
 * <p>
 * Intercepts GET requests with {@code accept=text/event-stream} header, invokes the
 * {@link EventStreamService#startEventStream(String, EventStreamListener, FutureCallback)} using
 * request {@code URI} as parameter. If request is accepted the handler starts an event stream as
 * {@code transfer-encoding=chunked} response: headers are sent immediately, body chunks are sent on
 * service events. If request is not accepted then BAD_REQUEST response returned with error message
 * in response body.
 *
 */
final class ServerSseHandler extends ChannelInboundHandlerAdapter implements EventStreamListener {
    private static final Logger LOG = LoggerFactory.getLogger(ServerSseHandler.class);
    private static final ByteBuf PING_MESSAGE = Unpooled.wrappedBuffer(":ping\r\n".getBytes(StandardCharsets.UTF_8));

    private final int maxFieldValueLength;
    private final int heartbeatIntervalMillis;
    private final EventStreamService service;
    private final AtomicBoolean streaming = new AtomicBoolean(false);

    private ChannelHandlerContext context;
    private Registration registration;

    ServerSseHandler(final EventStreamService service, final int maxFieldValueLength,
            final int heartbeatIntervalMillis) {
        this.service = requireNonNull(service);
        this.maxFieldValueLength = maxFieldValueLength;
        this.heartbeatIntervalMillis = heartbeatIntervalMillis;
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) throws Exception {
        this.context = ctx;
        ctx.channel().closeFuture().addListener(ignored -> unregister());
        LOG.info("Server SSE enabled on channel {}", context.channel());
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof FullHttpRequest request) {
            handleStreamRequest(ctx, request);
        } else {
            super.channelRead(ctx, msg);
        }
    }

    private void handleStreamRequest(final ChannelHandlerContext ctx, final FullHttpRequest request) throws Exception {
        if (streaming.get()) {
            LOG.warn("Ignoring unexpected request while SSE stream is active: {}", request);
            return;
        }
        if (HttpMethod.GET.equals(request.method())
                && request.headers().contains(HttpHeaderNames.ACCEPT, SseUtils.TEXT_EVENT_STREAM, true)) {

            service.startEventStream(request.retain().uri(), this, new FutureCallback<>() {
                @Override
                public void onSuccess(final Registration result) {
                    confirmEventStreamRequest(result, request);
                    request.release();
                }

                @Override
                public void onFailure(final Throwable exception) {
                    declineEventStreamRequest(request, exception);
                    request.release();
                }
            });

        } else {
            // pass request to next handler
            ctx.fireChannelRead(request);
        }
    }

    private void confirmEventStreamRequest(final Registration reg, final FullHttpRequest request) {
        this.registration = reg;
        streaming.set(true);
        // response OK with headers only, body chunks will be an event stream
        final var response = new DefaultHttpResponse(request.protocolVersion(), HttpResponseStatus.OK);
        response.headers()
            .set(HttpHeaderNames.CONTENT_TYPE, SseUtils.TEXT_EVENT_STREAM)
            .set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE)
            .set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
        copyStreamId(request, response);
        context.writeAndFlush(response);
        // schedule keep-alive events (no action required SSE 'ping' comment) if necessary
        if (heartbeatIntervalMillis > 0) {
            schedulePing();
        }
        LOG.info("Event Stream request accepted for URI={}", request.uri());
    }

    private void declineEventStreamRequest(final FullHttpRequest request, final Throwable exception) {
        // response BAD_REQUEST with error message taken from exception
        final var errorMessage = exception.getMessage();
        LOG.info("Event Stream request declined for URI={} -> {}", request.uri(), errorMessage);
        final var response = new DefaultFullHttpResponse(request.protocolVersion(), HttpResponseStatus.BAD_REQUEST,
            Unpooled.wrappedBuffer(errorMessage.getBytes(StandardCharsets.UTF_8)));
        response.headers()
                .set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN)
                .setInt(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
        copyStreamId(request, response);
        context.writeAndFlush(response);
    }

    @Override
    public void onEventField(final String fieldName, final String fieldValue) {
        if (isChannelWritable()) {
            chunksOf(fieldName, fieldValue, maxFieldValueLength, context.alloc())
                .forEach(chunk -> context.writeAndFlush(new DefaultHttpContent(chunk)));
        }
    }

    @Override
    public void onStreamStart() {
        // noop
    }

    @Override
    public void onStreamEnd() {
        onStreamEnd(new DefaultLastHttpContent());
    }

    private void onStreamEnd(final LastHttpContent lastContent) {
        if (isChannelWritable() && streaming.get()) {
            context.writeAndFlush(lastContent);
        }
        unregister();
        streaming.set(false);
    }

    private void schedulePing() {
        context.executor().schedule(this::sendPing, heartbeatIntervalMillis, TimeUnit.MILLISECONDS);
    }

    private void sendPing() {
        if (isChannelWritable() && streaming.get()) {
            context.writeAndFlush(PING_MESSAGE.copy());
            schedulePing();
        }
    }

    private boolean isChannelWritable() {
        return context != null && !context.isRemoved() && context.channel().isActive();
    }

    private void unregister() {
        if (registration != null) {
            registration.close();
        }
    }
}
