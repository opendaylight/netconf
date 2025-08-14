/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http2.Http2Connection;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Stream;
import java.net.SocketAddress;
import java.net.URI;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.transport.http.ConcurrentHTTPServerSession;
import org.opendaylight.netconf.transport.http.HTTPScheme;
import org.opendaylight.netconf.transport.http.ImplementedMethod;
import org.opendaylight.netconf.transport.http.PreparedRequest;
import org.opendaylight.restconf.server.api.TransportSession;
import org.opendaylight.restconf.server.spi.DefaultTransportSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP/2+ RESTCONF session, as defined in <a href="https://www.rfc-editor.org/rfc/rfc8650#section-3.1">RFC8650</a>.
 *
 * <p>It acts as glue between a Netty channel and a RESTCONF server and services multiple HTTP/2+ logical connections.
 */
final class ConcurrentRestconfSession extends ConcurrentHTTPServerSession implements Http2Connection.Listener {
    private static final Logger LOG = LoggerFactory.getLogger(ConcurrentRestconfSession.class);

    private final Set<Integer> aliveStreams = ConcurrentHashMap.newKeySet();

    @Override
    public void onStreamAdded(final Http2Stream stream) {

    }

    @Override
    public void onStreamActive(final Http2Stream stream) {
        System.out.println("Stream #" + stream.id() + " is active.");
        aliveStreams.add(stream.id());
    }

    @Override
    public void onStreamHalfClosed(final Http2Stream stream) {

    }

    @Override
    public void onStreamClosed(final Http2Stream stream) {
        System.out.println("Stream #" + stream.id() + " is closed.");
        aliveStreams.remove(stream.id());
    }

    @Override
    public void onStreamRemoved(final Http2Stream stream) {

    }

    @Override
    public void onGoAwaySent(final int lastStreamId, final long errorCode, final ByteBuf debugData) {

    }

    @Override
    public void onGoAwayReceived(final int lastStreamId, final long errorCode, final ByteBuf debugData) {

    }

    private final @NonNull DefaultTransportSession transportSession;
    private final @NonNull EndpointRoot root;

    @NonNullByDefault
    ConcurrentRestconfSession(final HTTPScheme scheme, final SocketAddress remoteAddress, final EndpointRoot root) {
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
            aliveStreams.clear();
        }
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx, final FullHttpRequest msg) {
        ctx.fireChannelRead(msg);
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
