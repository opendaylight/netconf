/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil.handler;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NetconfEOMAggregator extends ByteToMessageDecoder {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfEOMAggregator.class);

    // Cached for brevity and constantness
    private static final byte[] EOM = MessageParts.END_OF_MESSAGE;
    private static final int EOM_LENGTH = EOM.length;

    private static final ByteBuf DELIMITER = Unpooled.wrappedBuffer(MessageParts.END_OF_MESSAGE);
    private static final int DELIMITER_LENGTH = DELIMITER.capacity();

    // Number of input ByteBuf bytes known to not include the delimiter.
    private int bodyLength = 0;

    @Override
    protected void decode(final ChannelHandlerContext ctx, final ByteBuf in, final List<Object> out) {
        ByteBuf frame;
        while ((frame = decodeFrame(ctx, in)) != null) {
            out.add(frame);
        }
    }

    private ByteBuf decodeFrame(final ChannelHandlerContext ctx, final ByteBuf in) {
        // Cache the details of input ByteBuf as they are invariants
        final int readerIndex = in.readerIndex();
        final int writerIndex = in.writerIndex();

        int searchIndex = readerIndex + bodyLength;
        while (true) {
            // Try to find the first EOM byte
            final int eomIndex = in.indexOf(searchIndex, writerIndex, EOM[0]);
            if (eomIndex == -1) {
                // The first byte (']') is not present, everything in the buffer is part of the body
                bodyLength = writerIndex - readerIndex;
                return null;
            }

            // a.k.a. in.readableBytes() from the first EOM byte
            final int readableBytes = writerIndex - eomIndex;
            if (readableBytes < EOM_LENGTH) {
                // Not enough bytes to contain a delimiter, bail out
                LOG.trace("Context {} buffer {} has only {} new bytes", ctx, in, readableBytes);
                bodyLength = eomIndex - readerIndex - 1;
                return null;
            }

            // Check for EOM match
            if (isEom(in, eomIndex)) {
                final int frameLength = eomIndex - readerIndex;
                LOG.debug("Context {} buffer {} frame detected: length {}", ctx, in, frameLength);
                final var ret = in.readRetainedSlice(frameLength);
                in.skipBytes(DELIMITER_LENGTH);
                bodyLength = 0;
                return ret;
            }

            // No match: move one byte past eomIndex and repeat
            searchIndex = eomIndex + 1;
            LOG.trace("Context {} buffer {} restart at {}", ctx, in, searchIndex);
        }
    }

    // TODO: ByteBufUtil.indexOf() uses https://en.wikipedia.org/wiki/Two-way_string-matching_algorithm
    //       We should be able to take advantage of this improvement, i.e. adjust bodyLength when a match turns out to
    //       to be impossible. As our needle is only 6 bytes long, this may be a moot point. On the other hand, we might
    //       get some improvement by treating making this search a getInt() + getShort() comparison.
    private static boolean isEom(final ByteBuf in, final int index) {
        for (int i = 1; i < EOM_LENGTH; ++i) {
            if (in.getByte(index + i) != EOM[i]) {
                return false;
            }
        }
        return true;
    }
}
