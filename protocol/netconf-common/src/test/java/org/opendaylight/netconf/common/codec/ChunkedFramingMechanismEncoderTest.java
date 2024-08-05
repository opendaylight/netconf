/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.common.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChunkedFramingMechanismEncoderTest {
    private static final int CHUNK_SIZE = 256;

    @Mock
    private ChannelHandlerContext ctx;

    @Test
    void testIllegalSize() {
        assertThrows(IllegalArgumentException.class, () -> new ChunkedFramingMechanismEncoder(10));
    }

    @Test
    void testIllegalSizeMax() {
        assertThrows(IllegalArgumentException.class, () -> new ChunkedFramingMechanismEncoder(Integer.MAX_VALUE));
    }

    @Test
    void testEncode() {
        final ChunkedFramingMechanismEncoder encoder = new ChunkedFramingMechanismEncoder(CHUNK_SIZE);
        final int lastChunkSize = 20;
        final ByteBuf src = Unpooled.wrappedBuffer(getByteArray(CHUNK_SIZE * 4 + lastChunkSize));
        final ByteBuf destination = Unpooled.buffer();
        encoder.encode(ctx, src, destination);

        assertEquals(1077, destination.readableBytes());

        byte[] buf = new byte[destination.readableBytes()];
        destination.readBytes(buf);
        String string = StandardCharsets.US_ASCII.decode(ByteBuffer.wrap(buf)).toString();

        assertTrue(string.startsWith("\n#256\na"));
        assertTrue(string.endsWith("\n#20\naaaaaaaaaaaaaaaaaaaa\n##\n"));
    }

    private static byte[] getByteArray(final int size) {
        final byte[] bytes = new byte[size];
        for (int i = 0; i < size; i++) {
            bytes[i] = 'a';
        }
        return bytes;
    }
}
