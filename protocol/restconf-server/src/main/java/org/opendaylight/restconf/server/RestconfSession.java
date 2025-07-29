/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http2.Http2Exception;
import java.net.SocketAddress;
import java.net.URI;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.transport.http.HTTPScheme;
import org.opendaylight.netconf.transport.http.ImplementedMethod;
import org.opendaylight.netconf.transport.http.PipelinedHTTPServerSession;
import org.opendaylight.netconf.transport.http.PreparedRequest;
import org.opendaylight.restconf.server.api.TransportSession;
import org.opendaylight.restconf.server.spi.DefaultTransportSession;

/**
 * HTTP/1.1 RESTCONF session, as defined in <a href="https://www.rfc-editor.org/rfc/rfc8650#section-3.1">RFC8650</a>.
 *
 * <p>It acts as glue between a Netty channel and a RESTCONF server and services one HTTP/1.1 logical connections.
 */
final class RestconfSession extends PipelinedHTTPServerSession {
    private final @NonNull DefaultTransportSession transportSession;
    private final @NonNull EndpointRoot root;
    private final @NonNull Deque<FullHttpRequest> blockedRequests = new ConcurrentLinkedDeque<>();

    @NonNullByDefault
    RestconfSession(final HTTPScheme scheme, final SocketAddress remoteAddress, final EndpointRoot root) {
        super(scheme);
        this.root = requireNonNull(root);
        transportSession = new DefaultTransportSession(new HttpDescription(scheme, remoteAddress));
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) {
        super.handlerAdded(ctx);
        final var authHandlerFactory = root.authHandlerFactory();
        ctx.pipeline().addBefore(ctx.name(), null, authHandlerFactory.create());
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) throws Exception {
        if (cause instanceof Http2Exception.StreamException) {
            return;
        }
        super.exceptionCaught(ctx, cause);
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) throws Exception {
        try {
            super.channelInactive(ctx);
        } finally {
            transportSession.close();
        }
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx, final FullHttpRequest msg) {
        final var wasEmpty = blockedRequests.isEmpty();
        blockedRequests.offer(msg);
        if (wasEmpty) {
            super.channelRead0(ctx, msg);
        }
    }

    @Override
    protected ChannelFuture respond(final ChannelHandlerContext ctx, final @Nullable Integer streamId,
            final HttpResponse response) {
        final var respond = super.respond(ctx, streamId, response);
        respond.addListener(future -> {
            ctx.executor().execute(() -> {
                blockedRequests.poll();
                if (future.isSuccess()) {
                    if (!blockedRequests.isEmpty()) {
                        super.channelRead0(ctx, blockedRequests.peek());
                    }
                } else {
                    ctx.close();
                }
            });
        });
        return respond;
    }

    @Override
    protected PreparedRequest prepareRequest(final ImplementedMethod method, final URI targetUri,
            final HttpHeaders headers) {
        return root.prepare(transportSession, method, targetUri, headers);
    }

    @VisibleForTesting
    @NonNull TransportSession transportSession() {
        return transportSession;
    }
}
