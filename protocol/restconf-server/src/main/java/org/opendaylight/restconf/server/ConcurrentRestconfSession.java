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
import io.netty.handler.codec.http2.DelegatingDecompressorFrameListener;
import io.netty.handler.codec.http2.Http2Connection;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Flags;
import io.netty.handler.codec.http2.Http2FrameListener;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.codec.http2.Http2Stream;
import io.netty.handler.codec.http2.HttpConversionUtil;
import io.netty.handler.codec.http2.HttpToHttp2ConnectionHandler;
import io.netty.handler.codec.http2.HttpToHttp2ConnectionHandlerBuilder;
import java.net.SocketAddress;
import java.net.URI;
import java.util.Map;
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
final class ConcurrentRestconfSession extends ConcurrentHTTPServerSession implements Http2Connection.Listener, Http2FrameListener {
    private static final Logger LOG = LoggerFactory.getLogger(ConcurrentRestconfSession.class);

    private final Map<Integer, RestconfHttp2Handler> aliveStreams = new ConcurrentHashMap<>();

    private final RestconfHttp2Handler delegate;

    private final @NonNull DefaultTransportSession transportSession;
    private final @NonNull EndpointRoot root;

    @NonNullByDefault
    ConcurrentRestconfSession(final HTTPScheme scheme, final SocketAddress remoteAddress, final EndpointRoot root) {
        super(scheme);
        this.root = requireNonNull(root);
        transportSession = new DefaultTransportSession(new HttpDescription(scheme, remoteAddress));
        delegate = new RestconfHttp2Handler(this);
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) {
        super.handlerAdded(ctx);
        final var authHandlerFactory = root.authHandlerFactory();
        ctx.pipeline().addBefore(ctx.name(), null, authHandlerFactory.create());

        final var connectionHandler = ctx.pipeline().get(HttpToHttp2ConnectionHandler.class);
        final var connection = connectionHandler.connection();

        final var newConnectionHandler = new HttpToHttp2ConnectionHandlerBuilder()
            .connection(connection)
            .frameListener(new DelegatingDecompressorFrameListener(connection, this, 0))
            .gracefulShutdownTimeoutMillis(0L)
            .build();

        ctx.pipeline().replace(HttpToHttp2ConnectionHandler.class, null, newConnectionHandler);
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx, final FullHttpRequest msg) {
        final var headers = msg.headers();
        final var streamId = headers.getInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text());
        // FIXME find correct delegate according to stream id from 'aliveStreams'
        delegate.onDataRead(ctx, streamId, msg.content(), 0, true);
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
    protected PreparedRequest prepareRequest(final ImplementedMethod method, final URI targetUri,
            final HttpHeaders headers) {
        return root.prepare(transportSession, method, targetUri, headers);
    }

    @VisibleForTesting
    @NonNull TransportSession transportSession() {
        return transportSession;
    }

    @Override
    public void onStreamAdded(final Http2Stream stream) {
        LOG.info("Stream added: {}", stream.id());
        aliveStreams.put(stream.id(), new RestconfHttp2Handler(this));
        delegate.onStreamAdded(stream);
    }

    @Override
    public void onStreamActive(final Http2Stream stream) {
        LOG.info("Stream active: {}", stream.id());
        aliveStreams.put(stream.id(), new RestconfHttp2Handler(this));
        delegate.onStreamActive(stream);
    }

    @Override
    public void onStreamHalfClosed(final Http2Stream stream) {
        LOG.info("Stream half-closed: {}", stream.id());
        delegate.onStreamHalfClosed(stream);
    }

    @Override
    public void onStreamClosed(final Http2Stream stream) {
        LOG.info("Stream closed: {}", stream.id());
        aliveStreams.remove(stream.id());
        delegate.onStreamClosed(stream);
    }

    @Override
    public void onStreamRemoved(final Http2Stream stream) {
        LOG.info("Stream removed: {}", stream.id());
        aliveStreams.remove(stream.id());
        delegate.onStreamRemoved(stream);
    }

    @Override
    public void onGoAwaySent(final int lastStreamId, final long errorCode, final ByteBuf debugData) {
        delegate.onGoAwaySent(lastStreamId, errorCode, debugData);
    }

    @Override
    public void onGoAwayReceived(final int lastStreamId, final long errorCode, final ByteBuf debugData) {
        delegate.onGoAwayReceived(lastStreamId, errorCode, debugData);
    }

    @Override
    public int onDataRead(final ChannelHandlerContext ctx, final int streamId, final ByteBuf data, final int padding,
            final boolean endOfStream) throws Http2Exception {
        return delegate.onDataRead(ctx, streamId, data, padding, endOfStream);
    }

    @Override
    public void onHeadersRead(final ChannelHandlerContext ctx, final int streamId, final Http2Headers headers,
            final int padding, final boolean endOfStream) throws Http2Exception {
        delegate.onHeadersRead(ctx, streamId, headers, padding, endOfStream);
    }

    @Override
    public void onHeadersRead(final ChannelHandlerContext ctx, final int streamId, final Http2Headers headers,
            final int streamDependency, final short weight, final boolean exclusive, final int padding,
            final boolean endOfStream) throws Http2Exception {
        delegate.onHeadersRead(ctx, streamId, headers, padding, endOfStream);
    }

    @Override
    public void onPriorityRead(final ChannelHandlerContext ctx, final int streamId, final int streamDependency,
            final short weight, final boolean exclusive) throws Http2Exception {
        delegate.onPriorityRead(ctx, streamId, streamDependency, weight, exclusive);
    }

    @Override
    public void onRstStreamRead(final ChannelHandlerContext ctx, final int streamId, final long errorCode)
            throws Http2Exception {
        delegate.onRstStreamRead(ctx, streamId, errorCode);
    }

    @Override
    public void onSettingsAckRead(final ChannelHandlerContext ctx) throws Http2Exception {
        delegate.onSettingsAckRead(ctx);
    }

    @Override
    public void onSettingsRead(final ChannelHandlerContext ctx, final Http2Settings settings) throws Http2Exception {
        delegate.onSettingsRead(ctx, settings);
    }

    @Override
    public void onPingRead(final ChannelHandlerContext ctx, final long data) throws Http2Exception {
        delegate.onPingRead(ctx, data);
    }

    @Override
    public void onPingAckRead(final ChannelHandlerContext ctx, final long data) throws Http2Exception {
        delegate.onPingAckRead(ctx, data);
    }

    @Override
    public void onPushPromiseRead(final ChannelHandlerContext ctx, final int streamId, final int promisedStreamId,
            final Http2Headers headers, final int padding) throws Http2Exception {
        delegate.onPushPromiseRead(ctx, streamId, promisedStreamId, headers, padding);
    }

    @Override
    public void onGoAwayRead(final ChannelHandlerContext ctx, final int lastStreamId, final long errorCode,
            final ByteBuf debugData) throws Http2Exception {
        delegate.onGoAwayRead(ctx, lastStreamId, errorCode, debugData);
    }

    @Override
    public void onWindowUpdateRead(final ChannelHandlerContext ctx, final int streamId, final int windowSizeIncrement)
            throws Http2Exception {
        delegate.onWindowUpdateRead(ctx, streamId, windowSizeIncrement);
    }

    @Override
    public void onUnknownFrame(final ChannelHandlerContext ctx, final byte frameType, final int streamId,
            final Http2Flags flags, final ByteBuf payload) throws Http2Exception {
        delegate.onUnknownFrame(ctx, frameType,streamId, flags, payload);
    }
}
