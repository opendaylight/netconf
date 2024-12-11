/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static java.util.Objects.requireNonNull;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Asynchronous task executor. Usually tied to {@link HTTPServerSession}.
 */
final class ServerRequestExecutor implements PendingRequestListener {
    /**
     * Transport-level details about a {@link PendingRequest} execution.
     *
     * @param ctx the {@link ChannelHandlerContext} on which the request is occuring
     * @param streamId the HTTP/2 stream ID, if present
     * @param version HTTP version of the request
     */
    @NonNullByDefault
    private record RequestContext(ChannelHandlerContext ctx, HttpVersion version, @Nullable Integer streamId) {
        RequestContext {
            requireNonNull(ctx);
            requireNonNull(version);
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(ServerRequestExecutor.class);

    private final ConcurrentHashMap<PendingRequest<?>, RequestContext> pendingRequests = new ConcurrentHashMap<>();
    private final ExecutorService reqExecutor;
    private final ExecutorService respExecutor;

    ServerRequestExecutor(final String threadNamePrefix) {
        reqExecutor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual()
            .name(threadNamePrefix + "-http-server-req-", 0)
            .inheritInheritableThreadLocals(false)
            .uncaughtExceptionHandler(
                (thread, exception) -> LOG.warn("Unhandled request-phase failure", exception))
            .factory());
        respExecutor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual()
            .name(threadNamePrefix + "-http-server-resp-", 0)
            .inheritInheritableThreadLocals(false)
            .uncaughtExceptionHandler(
                (thread, exception) -> LOG.warn("Unhandled response-phase failure", exception))
            .factory());
    }

    void shutdown() {
        reqExecutor.shutdown();
        respExecutor.shutdown();
    }

    void executeRequest(final ChannelHandlerContext ctx, final HttpVersion version, final Integer streamId,
            final PendingRequest<?> pending, final ByteBuf content) {
        // We are invoked with content's reference and need to make sure it gets released.
        final var context = new RequestContext(ctx, version, streamId);
        if (content.isReadable()) {
            executeRequest(context, pending, new ByteBufInputStream(content, true));
        } else {
            content.release();
            executeRequest(context, pending, (ByteBufInputStream) null);
        }
    }

    private void executeRequest(final RequestContext context, final PendingRequest<?> pending,
            final ByteBufInputStream body) {
        // Remember metadata about the request and then execute it
        pendingRequests.put(pending, context);
        reqExecutor.execute(() -> pending.execute(this, body));
    }

    @Override
    public void requestComplete(final PendingRequest<?> request, final Response response) {
        final var req = pendingRequests.remove(request);
        if (req != null) {
            respond(req.ctx, req.streamId, req.version, response);
        } else {
            LOG.warn("Cannot pair request {}, not sending response {}", request, response, new Throwable());
        }
    }

    @Override
    public void requestFailed(final PendingRequest<?> request, final Exception cause) {
        LOG.warn("Internal error while processing {}", request, cause);
        final var req = pendingRequests.remove(request);
        if (req != null) {
            HTTPServerSession.respond(req.ctx, req.streamId, formatException(cause, req.version()));
        } else {
            LOG.warn("Cannot pair request, not sending response", new Throwable());
        }
    }

    @NonNullByDefault
    void respond(final ChannelHandlerContext ctx, final @Nullable Integer streamId, final HttpVersion version,
            final Response response) {
        switch (response) {
            case ReadyResponse resp -> HTTPServerSession.respond(ctx, streamId, resp.toHttpResponse(version));
            case FiniteResponse resp -> respond(ctx, streamId, version, resp);
            case EventStreamResponse resp -> HTTPServerSession.respond(ctx, streamId,
                resp.start(ctx, streamId, version));
        }
    }

    @NonNullByDefault
    private void respond(final ChannelHandlerContext ctx, final @Nullable Integer streamId, final HttpVersion version,
            final FiniteResponse response) {
        try {
            respExecutor.execute(
                () -> HTTPServerSession.respond(ctx, streamId, formatResponse(response, ctx, version)));
        } catch (RejectedExecutionException e) {
            LOG.trace("Session shut down, dropping response {}", response, e);
        }
    }

    // Hand-coded, as simple as possible
    @NonNullByDefault
    private static FullHttpResponse formatException(final Exception cause, final HttpVersion version) {
        final var message = new DefaultFullHttpResponse(version, HttpResponseStatus.INTERNAL_SERVER_ERROR);
        final var content = message.content();
        // Note: we are tempted to do a cause.toString() here, but we are dealing with unhandled badness here,
        //       so we do not want to be too revealing -- hence a message is all the user gets.
        ByteBufUtil.writeUtf8(content, cause.getMessage());
        HttpUtil.setContentLength(message, content.readableBytes());
        return message;
    }

    // Executed on respExecutor, so it is okay to block
    @NonNullByDefault
    private static FullHttpResponse formatResponse(final FiniteResponse response, final ChannelHandlerContext ctx,
            final HttpVersion version) {
        return null;
//        final ReadyResponse ready;
//        try {
//            ready = response.toReadyResponse(ctx.alloc());
//        } catch (IOException e) {
//            LOG.warn("IO error while converting formatting response", e);
//            return formatException(e, version);
//        }
//        return ready.toHttpResponse(version);
    }
}
