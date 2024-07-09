/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http2.AbstractInboundHttp2ToHttpAdapterBuilder;
import io.netty.handler.codec.http2.Http2Connection;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2FrameListener;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.InboundHttp2ToHttpAdapter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extended version of {@link InboundHttp2ToHttpAdapter} allowing delegation of header and data frames processing to
 * an external component which is dynamically detected on per stream-id basis. This allows dynamically process
 * incoming message chunks (streams) instead of awaiting of full message arrival (aggregation completion).
 *
 * <p>
 * External components are provided by an instance of {@link Http2FrameListenerProvider} which expected to be deployed
 * on same channel pipeline. If there is no such provider or if it provides no frame listener then default (message
 * aggregation) logic will be used. The external frame listener is requested by stream-id on headers frame processing.
 * If the stream-id associate listener is determined on header processing then same one will be used to process
 * subsequent data frames.
 *
 */
final class Http2ToHttpAdapter extends InboundHttp2ToHttpAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(Http2ToHttpAdapter.class);
    private final Map<Integer, Http2FrameListener> delegateListeners = new ConcurrentHashMap<>();

    private Http2ToHttpAdapter(final Http2Connection connection, final int maxContentLength,
            final boolean validateHttpHeaders, boolean propagateSettings) {
        super(connection, maxContentLength, validateHttpHeaders, propagateSettings);
    }

    @Override
    public void onHeadersRead(final ChannelHandlerContext ctx, final int streamId, final Http2Headers headers,
            final int padding, final boolean endOfStream) throws Http2Exception {
        checkListenerDelegate(ctx, streamId);
        final var delegate = delegateListeners.get(streamId);
        if (delegate != null) {
            delegate.onHeadersRead(ctx, streamId, headers, padding, endOfStream);
        } else {
            super.onHeadersRead(ctx, streamId, headers, padding, endOfStream);
        }
    }

    @Override
    public void onHeadersRead(final ChannelHandlerContext ctx, final int streamId, final Http2Headers headers,
            final int streamDependency, final short weight, final boolean exclusive, final int padding,
            final boolean endOfStream) throws Http2Exception {
        checkListenerDelegate(ctx, streamId);
        final var delegate = delegateListeners.get(streamId);
        if (delegate != null) {
            delegate.onHeadersRead(ctx, streamId, headers, streamDependency, weight, exclusive, padding, endOfStream);
        } else {
            super.onHeadersRead(ctx, streamId, headers, streamDependency, weight, exclusive, padding, endOfStream);
        }
    }

    @Override
    public int onDataRead(final ChannelHandlerContext ctx, final int streamId, final ByteBuf data,
        final int padding, final boolean endOfStream) throws Http2Exception {
        final var delegate = delegateListeners.get(streamId);
        return delegate != null
            ? delegate.onDataRead(ctx, streamId, data, padding, endOfStream)
            : super.onDataRead(ctx, streamId, data, padding, endOfStream);
    }

    private void checkListenerDelegate(final ChannelHandlerContext context, final int streamId) {
        final var frameListenerProvider = context.pipeline().get(Http2FrameListenerProvider.class);
        if (frameListenerProvider != null) {
            final var delegate = frameListenerProvider.getListenerFor(streamId);
            if (delegate != null) {
                delegateListeners.put(streamId, delegate);
                LOG.debug("Frame listener delegate found for stream-id: {}", streamId);
            } else {
                // ensure no old delegate remaining in map
                delegateListeners.remove(streamId);
            }
        } else {
            delegateListeners.clear();
        }
    }

    static Builder builder(final Http2Connection connection) {
        return new Builder(connection);
    }

    static final class Builder extends AbstractInboundHttp2ToHttpAdapterBuilder<Http2ToHttpAdapter, Builder> {
        private Builder(final Http2Connection connection) {
            super(connection);
        }

        @Override
        public Builder maxContentLength(int maxContentLength) {
            return super.maxContentLength(maxContentLength);
        }

        @Override
        public Builder validateHttpHeaders(boolean validate) {
            return super.validateHttpHeaders(validate);
        }

        @Override
        public Builder propagateSettings(boolean propagate) {
            return super.propagateSettings(propagate);
        }

        @Override
        public Http2ToHttpAdapter build() {
            return super.build();
        }

        protected Http2ToHttpAdapter build(Http2Connection connection, int maxContentLength,
                boolean validateHttpHeaders, boolean propagateSettings) throws Exception {
            return new Http2ToHttpAdapter(connection, maxContentLength, validateHttpHeaders, propagateSettings);
        }
    }
}
