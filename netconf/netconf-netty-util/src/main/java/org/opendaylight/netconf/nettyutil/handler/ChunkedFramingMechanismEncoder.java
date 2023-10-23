/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil.handler;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import java.nio.charset.StandardCharsets;
import org.opendaylight.netconf.api.messages.FramingMechanism;

/**
 * A {@link FramingMechanismEncoder} handling {@link FramingMechanism#CHUNK}.
 */
public final class ChunkedFramingMechanismEncoder extends FramingMechanismEncoder {
    public static final int DEFAULT_CHUNK_SIZE = 8192;
    public static final int MIN_CHUNK_SIZE = 128;
    public static final int MAX_CHUNK_SIZE = 16 * 1024 * 1024;

    private final int chunkSize;

    public ChunkedFramingMechanismEncoder() {
        this(DEFAULT_CHUNK_SIZE);
    }

    public ChunkedFramingMechanismEncoder(final int chunkSize) {
        if (chunkSize < MIN_CHUNK_SIZE) {
            throw new IllegalArgumentException(chunkSize + " is lower than minimum supported " + MIN_CHUNK_SIZE);
        }
        if (chunkSize > MAX_CHUNK_SIZE) {
            throw new IllegalArgumentException(chunkSize + " is lower than maximum supported " + MAX_CHUNK_SIZE);
        }
        this.chunkSize = chunkSize;
    }

    @Override
    protected void encode(final ChannelHandlerContext ctx, final ByteBuf msg, final ByteBuf out)  {
        do {
            final int xfer = Math.min(chunkSize, msg.readableBytes());

            out.writeBytes(MessageParts.START_OF_CHUNK);
            // FIXME: use out.writeCharSequece() ? if not, explain why
            out.writeBytes(String.valueOf(xfer).getBytes(StandardCharsets.US_ASCII));
            out.writeByte('\n');

            out.writeBytes(msg, xfer);
        } while (msg.isReadable());

        out.writeBytes(MessageParts.END_OF_CHUNK);
    }
}
