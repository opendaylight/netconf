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
    private static final int DELIMITER_LENGTH = DELIMITER.capacity();

    private int nextByteToCheck;
    private boolean frameConsumed = true;
    private int bytesConsumed;

    @Override
    protected void decode(final ChannelHandlerContext ctx, final ByteBuf in, final List<Object> out) {
        if (frameConsumed || bytesConsumed != nextByteToCheck - in.readerIndex()) {
            LOG.debug("Reset frame delimiter search: in {}, ctx {},"
                            + " nextByteToCheck {}, frameConsumed {}, bytesConsumed {}",
                    in, ctx, nextByteToCheck, frameConsumed, bytesConsumed);
            nextByteToCheck = in.readerIndex();
            frameConsumed = false;
            bytesConsumed = 0;
        }

        final int frameLength = indexOfDelimiter(in, nextByteToCheck);

        if (frameLength > 0) {
            final ByteBuf frame = in.readSlice(frameLength);
            in.skipBytes(DELIMITER_LENGTH);
            LOG.debug("Frame length {}, in {}", frameLength, in);

            out.add(frame.retain());
            frameConsumed = true;
        } else {
            bytesConsumed = nextByteToCheck - in.readerIndex();
        }
    }

    private int indexOfDelimiter(final ByteBuf in, final int startIndex) {
        for (int i = startIndex; i < in.writerIndex(); i++) {
            nextByteToCheck = i;
            int inputBufIndex = i;
            int delimiterIndex;
            for (delimiterIndex = 0; delimiterIndex < DELIMITER_LENGTH; delimiterIndex++) {
                if (in.getByte(inputBufIndex) != DELIMITER.getByte(delimiterIndex)) {
                    break;
                } else {
                    inputBufIndex++;
                    if (inputBufIndex == in.writerIndex() && delimiterIndex != DELIMITER_LENGTH - 1) {
                        return -1;
                    }
                }
            }

            if (delimiterIndex == DELIMITER_LENGTH) {
                // Found the delimiter in the input buffer!
                return i - in.readerIndex();
            }
        }

        return -1;
    }
}
