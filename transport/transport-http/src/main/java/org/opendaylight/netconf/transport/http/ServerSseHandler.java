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
import org.opendaylight.netconf.transport.http.EventStreamService.StartCallback;
import org.opendaylight.netconf.transport.http.EventStreamService.StreamControl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server side Server-Sent Event (SSE) Handler.
 *
 * <p>
 * Intercepts GET requests with {@code accept=text/event-stream} header, invokes the
 * {@link EventStreamService#startEventStream(String, EventStreamListener, StartCallback)} using
 * request {@code URI} as parameter. If request is accepted the handler starts an event stream as
 * {@code transfer-encoding=chunked} response: headers are sent immediately, body chunks are sent on
 * service events.
 *
 * <p>
 * If request is not accepted then error response will be returned with error message as response body.
 * If decline exception is an instance of {@link ErrorResponseException} then explicitly defined
 * {@code content-type} value and response status code will be used in error response header.
 */
public final class ServerSseHandler extends ChannelInboundHandlerAdapter implements EventStreamListener {
    private static final Logger LOG = LoggerFactory.getLogger(ServerSseHandler.class);
    private static final ByteBuf PING_MESSAGE =
        Unpooled.wrappedBuffer(new byte[] { ':', 'p', 'i', 'n', 'g', '\r', '\n', '\r', '\n' }).asReadOnly();
    private static final ByteBuf EMPTY_LINE = Unpooled.wrappedBuffer(new byte[] { '\r', '\n' }).asReadOnly();

    private final int maxFieldValueLength;
    private final long heartbeatIntervalMillis;
    private final EventStreamService service;

    private ChannelHandlerContext context;
    private StreamControl eventStream;
    private boolean streaming;

    /**
     * Default constructor.
     *
     * @param service the event stream service instance
     * @param maxFieldValueLength max length of event message in chars, if parameter value is greater than zero and
     *        message length exceeds the limit then message will split to sequence of shorter messages;
     *        if parameter value is zero or less, then message length won't be checked
     * @param heartbeatIntervalMillis the keep-alive ping message interval in milliseconds, if set to zero or less
     *        no ping message will be sent by server
     */
    public ServerSseHandler(final EventStreamService service, final int maxFieldValueLength,
            final long heartbeatIntervalMillis) {
        this.service = requireNonNull(service);
        this.maxFieldValueLength = maxFieldValueLength;
        this.heartbeatIntervalMillis = heartbeatIntervalMillis;
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) throws Exception {
        context = ctx;
        ctx.channel().closeFuture().addListener(ignored -> unregister());
        LOG.debug("Server SSE enabled on channel {}", context.channel());
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
        switch (msg) {
            case FullHttpRequest request -> channelRead(ctx, request);
            default -> super.channelRead(ctx, msg);
        }
    }

    private void channelRead(final ChannelHandlerContext ctx, final FullHttpRequest request) {
        if (streaming) {
            LOG.warn("Ignoring unexpected request while SSE stream is active: {}", request);
            return;
        }

        if (HttpMethod.GET.equals(request.method())
            && request.headers().contains(HttpHeaderNames.ACCEPT, HttpHeaderValues.TEXT_EVENT_STREAM, true)) {

            service.startEventStream(request.retain().uri(), this, new StartCallback() {
                @Override
                public void onStreamStarted(final StreamControl streamControl) {
                    confirmEventStreamRequest(request, streamControl);
                    request.release();
                }

                @Override
                public void onStartFailure(final Exception exception) {
                    declineEventStreamRequest(request, exception);
                    request.release();
                }
            });
            return;
        }

        // pass request to next handler
        ctx.fireChannelRead(request);
    }

    private void confirmEventStreamRequest(final FullHttpRequest request, final StreamControl startedStream) {
        eventStream = startedStream;
        streaming = true;
        // response OK with headers only, body chunks will be an event stream
        final var response = new DefaultHttpResponse(request.protocolVersion(), HttpResponseStatus.OK);
        response.headers()
            .set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_EVENT_STREAM)
            .set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE)
            .set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
        copyStreamId(request, response);
        context.writeAndFlush(response);
        // schedule keep-alive events (no action required SSE 'ping' comment) if necessary
        if (heartbeatIntervalMillis > 0) {
            schedulePing();
        }
        LOG.debug("Event Stream request accepted for URI={}", request.uri());
    }

    private void declineEventStreamRequest(final FullHttpRequest request, final Throwable exception) {
        final var errorMessage = exception.getMessage();
        LOG.debug("Event Stream request declined for URI={} -> {}", request.uri(), errorMessage);
        final HttpResponseStatus status;
        final CharSequence contentType;
        if (exception instanceof ErrorResponseException errorResponse) {
            status = HttpResponseStatus.valueOf(errorResponse.statusCode());
            contentType = errorResponse.contentType();
        } else {
            status = HttpResponseStatus.BAD_REQUEST;
            contentType = HttpHeaderValues.TEXT_PLAIN;
        }
        final var response = new DefaultFullHttpResponse(request.protocolVersion(), status,
            Unpooled.wrappedBuffer(errorMessage.getBytes(StandardCharsets.UTF_8)));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType)
            .setInt(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
        copyStreamId(request, response);
        context.writeAndFlush(response);
    }

    @Override
    public void onEventField(final String fieldName, final String fieldValue) {
        if (isChannelWritable()) {
            chunksOf(fieldName, fieldValue, maxFieldValueLength, context.alloc())
                .forEach(chunk -> context.writeAndFlush(new DefaultHttpContent(chunk)));
            context.writeAndFlush(new DefaultHttpContent(EMPTY_LINE.retainedSlice()));
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
        if (isChannelWritable() && streaming) {
            context.writeAndFlush(lastContent);
        }
        unregister();
        streaming = false;
    }

    private void schedulePing() {
        context.executor().schedule(this::sendPing, heartbeatIntervalMillis, TimeUnit.MILLISECONDS);
    }

    private void sendPing() {
        if (isChannelWritable() && streaming) {
            context.writeAndFlush(new DefaultHttpContent(PING_MESSAGE.retainedSlice()));
            schedulePing();
        }
    }

    private boolean isChannelWritable() {
        return context != null && !context.isRemoved() && context.channel().isActive();
    }

    private void unregister() {
        if (eventStream != null) {
            eventStream.close();
        }
    }
}
