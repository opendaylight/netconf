/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.nettyutil.handler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class ChunkedFramingMechanismEncoderTest {

    private int chunkSize;
    @Mock
    private ChannelHandlerContext ctx;

    @Before
    public void setUp() {
        chunkSize = 256;
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIllegalSize() throws Exception {
        new ChunkedFramingMechanismEncoder(10);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIllegalSizeMax() throws Exception {
        new ChunkedFramingMechanismEncoder(Integer.MAX_VALUE);
    }

    @Test
    public void testEncode() throws Exception {
        final ChunkedFramingMechanismEncoder encoder = new ChunkedFramingMechanismEncoder(chunkSize);
        final int lastChunkSize = 20;
        final ByteBuf src = Unpooled.wrappedBuffer(getByteArray(chunkSize * 4 + lastChunkSize));
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
