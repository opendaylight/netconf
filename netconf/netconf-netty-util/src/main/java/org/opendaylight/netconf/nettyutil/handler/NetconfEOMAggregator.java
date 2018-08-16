/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil.handler;

import static com.google.common.base.Verify.verify;

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
    private static final int DELIMITER_LENGTH = DELIMITER.capacity();

    private int nextByteToCheck = -1;
    private int bytesConsumed = 0;

    @Override
    protected void decode(final ChannelHandlerContext ctx, final ByteBuf in, final List<Object> out) {
        if (bytesConsumed != nextByteToCheck - in.readerIndex()) {
            LOG.debug("Buffer reset: in {}, ctx {}, nextByteToCheck {}, bytesConsumed {}",
                    in, ctx, nextByteToCheck, bytesConsumed);
            // readerIndex is reset or the method is called first time. Adjust nextByteToCheck.
            nextByteToCheck = in.readerIndex() + bytesConsumed;
        }
        verify(nextByteToCheck >= in.readerIndex(), "nextByteToCheck (%s) is smaller than readerIndex (%s).",
                nextByteToCheck, in.readerIndex());

        final int frameLength = indexOfDelimiter(in);

        if (frameLength > 0) {
            LOG.debug("Frame detected: length {}, in {}", frameLength, in);
            final ByteBuf frame = in.readSlice(frameLength);
            in.skipBytes(DELIMITER_LENGTH);
            out.add(frame.retain());
            bytesConsumed = 0;
        } else {
            bytesConsumed = nextByteToCheck - in.readerIndex();
        }
    }

    private int indexOfDelimiter(final ByteBuf in) {
        for (int i = nextByteToCheck; i < in.writerIndex(); i++) {
            int inputBufIndex = i;
            int delimiterIndex;
            for (delimiterIndex = 0; delimiterIndex < DELIMITER_LENGTH; delimiterIndex++) {
                if (in.getByte(inputBufIndex) != DELIMITER.getByte(delimiterIndex)) {
                    break;
                } else {
                    inputBufIndex++;
                    if (inputBufIndex == in.writerIndex() && delimiterIndex != DELIMITER_LENGTH - 1) {
                        // Delimiter pattern matched up to (delimiterIndex + 1) bytes,
                        // but reached the end of the available data, so next search should
                        // start from the current index.
                        nextByteToCheck = i;
                        return -1;
                    }
                }
            }

            if (delimiterIndex == DELIMITER_LENGTH) {
                // Found the delimiter in the input buffer!
                // Next search should start after skipping the delimiter.
                nextByteToCheck = i + DELIMITER_LENGTH;
                return i - in.readerIndex();
            }
        }

        // Delimiter was not found and there is no partial match at the end. So next search
        // can start from writerIndex.
        nextByteToCheck = in.writerIndex();
        return -1;
    }
}
