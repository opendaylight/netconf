/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opendaylight.netconf.api.messages.NetconfMessage;
import org.opendaylight.netconf.codec.ChunkedFrameDecoder;
import org.opendaylight.netconf.codec.ChunkedFrameEncoder;
import org.opendaylight.netconf.codec.EOMFrameDecoder;
import org.opendaylight.netconf.codec.EOMFrameEncoder;
import org.opendaylight.netconf.codec.MessageEncoder;
import org.opendaylight.netconf.codec.XMLMessageDecoder;
import org.opendaylight.netconf.nettyutil.handler.XMLMessageWriter;
import org.opendaylight.netconf.test.util.XmlFileLoader;
import org.xmlunit.builder.DiffBuilder;

class MessageParserTest {
    private NetconfMessage msg;

    @BeforeEach
    void setUp() throws Exception {
        msg = XmlFileLoader.xmlFileToNetconfMessage("netconfMessages/getConfig.xml");
    }

    @Test
    void testChunkedFramingMechanismOnPipeline() throws Exception {
        final var testChunkChannel = new EmbeddedChannel(
                new ChunkedFrameEncoder(), new MessageEncoder(XMLMessageWriter.pretty()),
                new ChunkedFrameDecoder(ChunkedFrameEncoder.MAX_CHUNK_SIZE), new XMLMessageDecoder());

        testChunkChannel.writeOutbound(msg);
        final var messages = testChunkChannel.outboundMessages();
        assertEquals(1, messages.size());

        final var baos = new ByteArrayOutputStream();
        XMLMessageWriter.pretty().writeMessage(msg, baos);

        final int msgLength = baos.toByteArray().length;
        assertEquals(346, msgLength);

        int chunkCount = msgLength / ChunkedFrameEncoder.DEFAULT_CHUNK_SIZE;
        if (msgLength % ChunkedFrameEncoder.DEFAULT_CHUNK_SIZE != 0) {
            chunkCount++;
        }

        final var endOfChunkBytes = "\n##\n".getBytes(StandardCharsets.US_ASCII);
        for (int i = 1; i <= chunkCount; i++) {
            final var recievedOutbound = (ByteBuf) messages.poll();
            int exptHeaderLength = ChunkedFrameEncoder.DEFAULT_CHUNK_SIZE;
            if (i == chunkCount) {
                exptHeaderLength = msgLength - ChunkedFrameEncoder.DEFAULT_CHUNK_SIZE * (i - 1);
                byte[] eom = new byte[endOfChunkBytes.length];
                recievedOutbound.getBytes(recievedOutbound.readableBytes() - endOfChunkBytes.length, eom);
                assertArrayEquals(endOfChunkBytes, eom);
            }

            final var header = new byte[String.valueOf(exptHeaderLength).length() + 3];
            recievedOutbound.getBytes(0, header);
            assertEquals(exptHeaderLength, getHeaderLength(header));

            testChunkChannel.writeInbound(recievedOutbound);
        }
        assertEquals(0, messages.size());

        final NetconfMessage receivedMessage = testChunkChannel.readInbound();
        assertNotNull(receivedMessage);

        final var diff = DiffBuilder.compare(msg.getDocument())
            .withTest(receivedMessage.getDocument())
            .ignoreWhitespace()
            .checkForIdentical()
            .build();
        assertFalse(diff.hasDifferences(), diff.toString());
    }

    @Test
    void testEOMFramingMechanismOnPipeline() {
        final var testChunkChannel = new EmbeddedChannel(
                new EOMFrameEncoder(), new MessageEncoder(XMLMessageWriter.of()),
                new EOMFrameDecoder(), new XMLMessageDecoder());

        testChunkChannel.writeOutbound(msg);
        final ByteBuf recievedOutbound = testChunkChannel.readOutbound();

        final var endOfMsgBytes = "]]>]]>".getBytes(StandardCharsets.US_ASCII);
        final var eom = new byte[endOfMsgBytes.length];
        recievedOutbound.getBytes(recievedOutbound.readableBytes() - endOfMsgBytes.length, eom);
        assertArrayEquals(endOfMsgBytes, eom);

        testChunkChannel.writeInbound(recievedOutbound);
        NetconfMessage receivedMessage = testChunkChannel.readInbound();
        assertNotNull(receivedMessage);

        final var diff = DiffBuilder.compare(msg.getDocument())
            .withTest(receivedMessage.getDocument())
            .ignoreWhitespace()
            .checkForIdentical()
            .build();
        assertFalse(diff.hasDifferences(), diff.toString());
    }

    private static long getHeaderLength(final byte[] bytes) {
        final var headerStart = new byte[]{(byte) 0x0a, (byte) 0x23};
        return Long.parseLong(StandardCharsets.US_ASCII.decode(
                ByteBuffer.wrap(bytes, headerStart.length, bytes.length - headerStart.length - 1)).toString());
    }
}
