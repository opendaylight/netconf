/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;
import static org.opendaylight.netconf.transport.http.SseUtils.chunksOf;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http2.DefaultHttp2DataFrame;
import io.netty.handler.codec.http2.DefaultHttp2ResetFrame;
import io.netty.handler.codec.http2.Http2ChannelDuplexHandler;
import io.netty.handler.codec.http2.Http2Error;
import io.netty.handler.codec.http2.Http2FrameStream;
import io.netty.handler.codec.http2.Http2Stream;
import org.opendaylight.restconf.server.spi.RestconfStream.Sender;
import org.opendaylight.yangtools.concepts.Registration;

/**
 * A {@link Sender} bound to a logical stream. This is how event streams are delivered over HTTP/2: other requests can
 * be executed concurrently and the sender can be terminated when the stream is terminated.
 */
public final class StreamSender extends Http2ChannelDuplexHandler implements Sender {
    @SuppressFBWarnings("URF_UNREAD_FIELD") //FIXME: Just to pass check, remove when in use.
    private static final ByteBuf EMPTY_LINE = Unpooled.wrappedBuffer(new byte[] { '\r', '\n' }).asReadOnly();

    private final Integer streamId;
    private final int sseMaximumFragmentLength;
    private ChannelHandlerContext context;
    private Registration registration;

    public StreamSender(final Integer streamId, final int sseMaximumFragmentLength) {
        this.streamId = requireNonNull(streamId);
        this.sseMaximumFragmentLength = sseMaximumFragmentLength;
        context = null;
    }

    @Override
    public void sendDataMessage(final String data) {
        if (!data.isEmpty() && context != null) {
            chunksOf("data", data, sseMaximumFragmentLength, context.alloc())
                .forEach(chunk -> {
                    final var frame = new DefaultHttp2DataFrame(chunk, false);
                    frame.stream(new Http2FrameStream() {
                        @Override
                        public int id() {
                            return streamId;
                        }

                        @Override
                        public Http2Stream.State state() {
                            return null;
                        }
                    });
                    context.writeAndFlush(frame);
                });
        }
    }

    @Override
    public void endOfStream() {
        // Send an empty DATA frame with endStream flag set to true
        final var endFrame = new DefaultHttp2DataFrame(Unpooled.EMPTY_BUFFER, true);
        context.writeAndFlush(endFrame);
        if (context != null) {
            context.close();
        }
    }

    void terminate() {
        // Send an empty DATA frame with endStream flag set to true
        final var resetFrame = new DefaultHttp2ResetFrame(Http2Error.CANCEL);
        context.writeAndFlush(resetFrame);
        if (context != null) {
            context.close();
        }
    }

    @Override
    protected void handlerAdded0(final ChannelHandlerContext ctx) throws Exception {
        context = ctx;
        super.handlerAdded0(ctx);
    }

    public void enable(final Registration reg) {
        registration = requireNonNull(reg);
    }
}
