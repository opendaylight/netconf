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
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Flags;
import io.netty.handler.codec.http2.Http2FrameListener;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Settings;
import java.net.SocketAddress;
import java.net.URI;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
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
final class ConcurrentRestconfSession extends ConcurrentHTTPServerSession implements Http2FrameListener {
    private static final Logger LOG = LoggerFactory.getLogger(ConcurrentRestconfSession.class);

    private final @NonNull DefaultTransportSession transportSession;
    private final @NonNull EndpointRoot root;
    private final Set<Integer> aliveStreams = ConcurrentHashMap.newKeySet();

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
        // add stream management here?
        // see: Http2Connection.Listener and Http2EventAdapter
    }

    private void terminate(final int id) {
        // TODO terminate remaining executions for reset id
    }

    @Override
    protected ChannelFuture respond(final ChannelHandlerContext ctx, final @Nullable Integer streamId,
            final HttpResponse response) {
        if (!aliveStreams.contains(streamId)) {
            // error
        }
        // TODO use framesize to stream response
        return super.respond(ctx, streamId, response);
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

    @Override
    public int onDataRead(final ChannelHandlerContext ctx, final int streamId, final ByteBuf data, final int padding,
            final boolean endOfStream) throws Http2Exception {
        return 0;
    }

    @Override
    public void onHeadersRead(final ChannelHandlerContext ctx, final int streamId, final Http2Headers headers,
            final int padding, final boolean endOfStream) throws Http2Exception {

    }

    @Override
    public void onHeadersRead(final ChannelHandlerContext ctx, final int streamId, final Http2Headers headers,
            final int streamDependency, final short weight, final boolean exclusive, final int padding,
            final boolean endOfStream) throws Http2Exception {

    }

    @Override
    public void onPriorityRead(final ChannelHandlerContext ctx, final int streamId, final int streamDependency,
            final short weight, final boolean exclusive) throws Http2Exception {

    }

    @Override
    public void onRstStreamRead(final ChannelHandlerContext ctx, final int streamId, final long errorCode)
            throws Http2Exception {

    }

    @Override
    public void onSettingsAckRead(final ChannelHandlerContext ctx) throws Http2Exception {

    }

    @Override
    public void onSettingsRead(final ChannelHandlerContext ctx, final Http2Settings settings) throws Http2Exception {

    }

    @Override
    public void onPingRead(final ChannelHandlerContext ctx, final long data) throws Http2Exception {

    }

    @Override
    public void onPingAckRead(final ChannelHandlerContext ctx, final long data) throws Http2Exception {

    }

    @Override
    public void onPushPromiseRead(final ChannelHandlerContext ctx, final int streamId, final int promisedStreamId,
        final Http2Headers headers, final int padding) throws Http2Exception {

    }

    @Override
    public void onGoAwayRead(final ChannelHandlerContext ctx, final int lastStreamId, final long errorCode,
            final ByteBuf debugData) throws Http2Exception {

    }

    @Override
    public void onWindowUpdateRead(final ChannelHandlerContext ctx, final int streamId, final int windowSizeIncrement)
            throws Http2Exception {

    }

    @Override
    public void onUnknownFrame(final ChannelHandlerContext ctx, final byte frameType, final int streamId,
            final Http2Flags flags, final ByteBuf payload) throws Http2Exception {

    }
}
