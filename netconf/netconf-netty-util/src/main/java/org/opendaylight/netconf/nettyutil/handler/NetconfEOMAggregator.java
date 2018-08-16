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

public class NetconfEOMAggregator extends ByteToMessageDecoder {

    private static final ByteBuf DELIMITER = Unpooled.wrappedBuffer(MessageParts.END_OF_MESSAGE);
    private static final int DELIMITER_LENGTH = DELIMITER.capacity();

    private int nextByteToCheck;
    private boolean frameConsumed = true;

    @Override
    protected void decode(final ChannelHandlerContext ctx, final ByteBuf in, final List<Object> out) {
        if (frameConsumed) {
            nextByteToCheck = in.readerIndex();
            frameConsumed = false;
        }

        int frameLength = indexOfDelimiter(in);

        if (frameLength != -1) {
            ByteBuf frame = in.readSlice(frameLength);
            in.skipBytes(DELIMITER_LENGTH);

            out.add(frame.retain());
            frameConsumed = true;
        }
    }

    private int indexOfDelimiter(ByteBuf in) {
        for (int i = nextByteToCheck; i < in.writerIndex(); i++) {
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

            nextByteToCheck = i;
        }

        return -1;
    }
}
