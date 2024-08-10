/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChunkedFramingSupportTest extends FramingSupportTest {
    private static final int CHUNK_SIZE = 256;

    @Test
    void testIllegalSize() {
        assertThrows(IllegalArgumentException.class, () -> FramingSupport.chunk(10));
    }

    @Test
    void testIllegalSizeMax() {
        assertThrows(IllegalArgumentException.class, () -> FramingSupport.chunk(Integer.MAX_VALUE));
    }

    @Test
    void testEncode() {
        final int lastChunkSize = 20;
        final var out = Unpooled.buffer();

        writeBytes(FramingSupport.chunk(CHUNK_SIZE), getByteArray(CHUNK_SIZE * 4 + lastChunkSize), out);

        final var bytes = ByteBufUtil.getBytes(out);
        assertEquals(1077, bytes.length);

        String string = StandardCharsets.US_ASCII.decode(ByteBuffer.wrap(bytes)).toString();

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
