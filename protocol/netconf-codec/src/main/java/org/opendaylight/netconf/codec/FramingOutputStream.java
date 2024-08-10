/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.codec;

import static java.util.Objects.requireNonNull;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

/**
 * Base class for {@link OutputStream}s given out by {@link MessageFramer}.
 */
abstract sealed class FramingOutputStream extends OutputStream {
    final class Chunk extends FramingOutputStream {
        public static final int MIN_CHUNK_SIZE = 128;
        public static final int MAX_CHUNK_SIZE = 16 * 1024 * 1024;

        private final int chunkSize;

        Chunk(final ChannelHandlerContext ctx, final int chunkSize) {
            super(ctx);
            if (chunkSize < MIN_CHUNK_SIZE) {
                throw new IllegalArgumentException(chunkSize + " is lower than minimum supported " + MIN_CHUNK_SIZE);
            }
            if (chunkSize > MAX_CHUNK_SIZE) {
                throw new IllegalArgumentException(chunkSize + " is lower than maximum supported " + MAX_CHUNK_SIZE);
            }
            this.chunkSize = chunkSize;
        }

        @Override
        int availableBytes(final ByteBuf buf, final int requestedBytes) {
            final int written = buf.writerIndex();
            final int target = written + requestedBytes;
            final int available = buf.writableBytes();

            if (total <= chunkSize) {
                return requestedBytes;
            }


            final var max = Math.min(chunkSize, written + requestedBytes);


            buf.writableBytes();



            // TODO Auto-generated method stub
            return 0;
        }
    }

    final class EOM extends FramingOutputStream {
        EOM(final ChannelHandlerContext ctx) {
            super(ctx);
        }

        @Override
        int availableBytes(final ByteBuf buf, final int requestedBytes) {

            // TODO Auto-generated method stub
            return 0;
        }
    }

    final ChannelHandlerContext ctx;

    private ChannelFuture last;
    private ByteBuf current;
    private long writtenBytes;

    private FramingOutputStream(final ChannelHandlerContext ctx) {
        this.ctx = requireNonNull(ctx);
        current = ctx.alloc().buffer();
    }

    @Override
    public final void write(final int value) throws IOException {
        current.writeByte(value);
        writtenBytes++;
        sendIfFull();
    }

    @Override
    public final void write(final byte[] bytes, final int off, final int len) throws IOException {
        Objects.checkFromIndexSize(off, len, bytes.length);
        if (len == 0) {
            return;
        }

        int from = off;
        int remaining = len;
        while (true) {
            final var size = availableBytes(current, remaining);
            if (size == 0) {
                sendFragment();
                continue;
            }

            final var size = Math.min(remaining, maxFragmentSize - current.writerIndex());
            current.writeBytes(bytes, from, size);
            from += size;
            remaining -= size;

            if (remaining == 0) {
                writtenBytes += len;
                sendIfFull();
                return;
            }

            sendFragment();
        }
    }

    @Override
    public final void close() {
        // FIXME: return state to parent, so the user can decide whether or not to complete the output
    }

    abstract int availableBytes(ByteBuf buf, int requestedBytes);

//    abstract ByteBuf wrapFragment(ByteBuf fragment);
//

    private void sendIfFull() {
        if (!current.isWritable()) {
            sendFragment();
        }
    }

    private void sendFragment() {
        last = ctx.write(wrapFragment(current));
        current = ctx.alloc().buffer();
    }

}
