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
    private static final ByteBuf DELIMITER = Unpooled.wrappedBuffer(MessageParts.END_OF_MESSAGE);
    private static final int DELIM_LENGTH = DELIMITER.capacity();

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        Object decoded = decode(ctx, in);
        if (decoded != null) {
            out.add(decoded);
        }
    }

    private Object decode(final ChannelHandlerContext ctx, final ByteBuf buffer) {
        LOG.debug("Received buffer {}, ctx {}", buffer, ctx);
        final int frameLength = indexOf(buffer);
        if (frameLength >= 0) {
            final var frame = buffer.readRetainedSlice(frameLength);
            buffer.skipBytes(DELIM_LENGTH);
            buffer.markReaderIndex();
            LOG.debug("Frame length {}, buffer: {}", frameLength, buffer);

            return frame;
        }

        final int readerIndex = buffer.readerIndex();
        final int markedReaderIndex = buffer.writerIndex() - 1;
        buffer.readerIndex(markedReaderIndex).markReaderIndex().readerIndex(readerIndex);
        LOG.debug("No delimiter in buffer {}, markedReaderIndex {}", buffer, markedReaderIndex);

        return null;
    }

    private int indexOf(final ByteBuf haystack) {
        final int readerIndex = haystack.readerIndex();
        final int markedReaderIndex = haystack.resetReaderIndex().readerIndex();
        haystack.readerIndex(readerIndex);
        LOG.debug("Looking for delimiter in buffer {}, markedReadIndex {}", haystack, markedReaderIndex);

        final int startIndex = markedReaderIndex - readerIndex > DELIM_LENGTH
                ? markedReaderIndex - DELIM_LENGTH : readerIndex;

        for (int i = startIndex; i < haystack.writerIndex(); i ++) {
            int haystackIndex = i;
            int needleIndex;
            for (needleIndex = 0; needleIndex < DELIM_LENGTH; needleIndex ++) {
                if (haystack.getByte(haystackIndex) != DELIMITER.getByte(needleIndex)) {
                    break;
                } else {
                    haystackIndex ++;
                    if (haystackIndex == haystack.writerIndex()
                            && needleIndex != DELIM_LENGTH - 1) {
                        return -1;
                    }
                }
            }

            if (needleIndex == DELIM_LENGTH) {
                final int frameLength = i - haystack.readerIndex();
                LOG.debug("Delimiter in buffer {}, index {}, length {}", haystack, i, frameLength);
                return frameLength;
            }
        }

        return -1;
    }
}
