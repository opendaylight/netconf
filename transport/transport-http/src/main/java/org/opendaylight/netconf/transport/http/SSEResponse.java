package org.opendaylight.netconf.transport.http;

import static io.netty.handler.codec.http2.HttpConversionUtil.ExtensionHeaderNames.STREAM_ID;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import java.util.concurrent.TimeUnit;
import org.eclipse.jdt.annotation.Nullable;

public final class SSEResponse implements Response {
    private static final ByteBuf PING_MESSAGE =
        Unpooled.wrappedBuffer(new byte[] { ':', 'p', 'i', 'n', 'g', '\r', '\n', '\r', '\n' }).asReadOnly();

    private final ChannelHandler sender;
    private final ChannelHandler handler;

    private ChannelHandlerContext context;

    public SSEResponse(ChannelHandler sender, ChannelHandler handler) {
        this.sender = sender;
        this.handler = handler;
    }

    @Override
    public HttpResponseStatus status() {
        return null;
    }

    public DefaultFullHttpResponse start(ChannelHandlerContext ctx, @Nullable Integer streamId, HttpVersion version) {
        // Replace handler with the sender and enable
        context = ctx;
        ctx.channel().pipeline().replace(handler, null, sender);

        final var response = new DefaultFullHttpResponse(version, HttpResponseStatus.OK);
        response.headers()
            .set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_EVENT_STREAM)
            .set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE)
            .set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
        copyStreamId(streamId, response);

        //if (heartbeatIntervalMillis > 0) {
        schedulePing();

        return response;
    }

    private void schedulePing() {
        context.executor().schedule(this::sendPing, 10000, TimeUnit.MILLISECONDS);
    }

    private void sendPing() {
        if (isChannelWritable()) {
            context.writeAndFlush(new DefaultHttpContent(PING_MESSAGE.retainedSlice()));
            schedulePing();
        }
    }

    private boolean isChannelWritable() {
        return context != null && !context.isRemoved() && context.channel().isActive();
    }

    static void copyStreamId(final Integer streamId, final HttpMessage to) {
        if (streamId != null) {
            to.headers().setInt(STREAM_ID.text(), streamId);
        }
    }
}
