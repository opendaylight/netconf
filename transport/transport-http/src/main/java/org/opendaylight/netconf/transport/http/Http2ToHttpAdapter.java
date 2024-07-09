/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.http;

import static io.netty.handler.codec.http2.Http2Error.PROTOCOL_ERROR;
import static io.netty.handler.codec.http2.Http2Exception.connectionError;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http2.AbstractInboundHttp2ToHttpAdapterBuilder;
import io.netty.handler.codec.http2.Http2Connection;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.InboundHttp2ToHttpAdapter;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class Http2ToHttpAdapter extends InboundHttp2ToHttpAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(Http2ToHttpAdapter.class);
    private final AtomicBoolean aggregationDisabled = new AtomicBoolean(false);

    private Http2ToHttpAdapter(final Http2Connection connection, final int maxContentLength,
            final boolean validateHttpHeaders, boolean propagateSettings) {
        super(connection, maxContentLength, validateHttpHeaders, propagateSettings);
    }

    @Override
    public void onHeadersRead(final ChannelHandlerContext ctx, final int streamId, final Http2Headers headers,
            final int padding, final boolean endOfStream) throws Http2Exception {
//        LOG.info("#onHeadersRead(ctx, streamId={}, headers, padding, endOfStream={})", streamId, endOfStream);
//        LOG.info("headers: {}", headers);
        checkAggregationMode(ctx);
        super.onHeadersRead(ctx, streamId, headers, padding, endOfStream);
        if (aggregationDisabled.get()) {
            sendHeaderObject(ctx, streamId);
        }
    }

    @Override
    public void onHeadersRead(final ChannelHandlerContext ctx, final int streamId, final Http2Headers headers,
            final int streamDependency, final short weight, final boolean exclusive, final int padding,
            final boolean endOfStream) throws Http2Exception {
//        LOG.info("#onHeadersRead(ctx, streamId={}, headers, streamDependency, weight, exclusive, padding, "
//            + "endOfStream={})", streamId, endOfStream);
//        LOG.info("headers: {}", headers);
        checkAggregationMode(ctx);
        super.onHeadersRead(ctx, streamId, headers, streamDependency, weight, exclusive, padding, endOfStream);
        if (!endOfStream && aggregationDisabled.get()) {
            sendHeaderObject(ctx, streamId);
        }
    }

    @Override
    public int onDataRead(final ChannelHandlerContext ctx, final int streamId, final ByteBuf data,
        final int padding, final boolean endOfStream) throws Http2Exception {
//        LOG.info("#onDataRead(ctx, streamId={}, data=, padding, endOfStream={})", streamId, endOfStream);
        if (aggregationDisabled.get()) {
            final var stream = connection.stream(streamId);
            final var msg = getMessage(stream);
            if (msg == null) {
                throw connectionError(PROTOCOL_ERROR, "Data Frame received for unknown stream id %d", streamId);
            }
            final var dataReadableBytes = data.readableBytes();
            final var content = data.retainedSlice();
            if (endOfStream) {
                ctx.fireChannelRead(new DefaultLastHttpContent(content));
                // remove & release internally aggregated full message
                removeMessage(stream, true);
            } else {
                ctx.fireChannelRead(new DefaultHttpContent(content));
            }
            // All bytes have been processed.
            return dataReadableBytes + padding;
        }
        // default behavior
        return super.onDataRead(ctx, streamId, data, padding, endOfStream);
    }

    private void sendHeaderObject(final ChannelHandlerContext ctx, final int streamId) {
        final var stream = connection.stream(streamId);
        final var message  = stream == null ? null : getMessage(stream);
        if (message instanceof FullHttpRequest req) {
            ctx.fireChannelRead(new DefaultHttpRequest(req.protocolVersion(), req.method(), req.uri(), req.headers()));
        } else if (message instanceof FullHttpResponse resp) {
            ctx.fireChannelRead(new DefaultHttpResponse(resp.protocolVersion(), resp.status(), resp.headers()));
        }
    }

    private void checkAggregationMode(final ChannelHandlerContext context) {
        aggregationDisabled.set(context.pipeline().context(HttpObjectAggregator.class) != null);
        LOG.info("aggregation disabled: {}", aggregationDisabled.get());
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
