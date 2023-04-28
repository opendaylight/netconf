/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server;

import static org.custommonkey.xmlunit.XMLAssert.assertXMLEqual;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Queue;
import org.custommonkey.xmlunit.XMLUnit;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.api.messages.FramingMechanism;
import org.opendaylight.netconf.nettyutil.handler.ChunkedFramingMechanismEncoder;
import org.opendaylight.netconf.nettyutil.handler.FramingMechanismHandlerFactory;
import org.opendaylight.netconf.nettyutil.handler.NetconfChunkAggregator;
import org.opendaylight.netconf.nettyutil.handler.NetconfEOMAggregator;
import org.opendaylight.netconf.nettyutil.handler.NetconfMessageToXMLEncoder;
import org.opendaylight.netconf.nettyutil.handler.NetconfXMLToMessageDecoder;
import org.opendaylight.netconf.test.util.XmlFileLoader;

public class MessageParserTest {
    private NetconfMessage msg;

    @Before
    public void setUp() throws Exception {
        msg = XmlFileLoader.xmlFileToNetconfMessage("netconfMessages/getConfig.xml");
    }

    @Test
    public void testChunkedFramingMechanismOnPipeline() throws Exception {
        EmbeddedChannel testChunkChannel = new EmbeddedChannel(
                FramingMechanismHandlerFactory.createHandler(FramingMechanism.CHUNK),
                new NetconfMessageToXMLEncoder(),
                new NetconfChunkAggregator(ChunkedFramingMechanismEncoder.MAX_CHUNK_SIZE),
                new NetconfXMLToMessageDecoder());

        testChunkChannel.writeOutbound(msg);
        Queue<Object> messages = testChunkChannel.outboundMessages();
        assertEquals(1, messages.size());

        final NetconfMessageToXMLEncoder enc = new NetconfMessageToXMLEncoder();
        final ByteBuf out = Unpooled.buffer();
        enc.encode(null, msg, out);
        int msgLength = out.readableBytes();

        int chunkCount = msgLength / ChunkedFramingMechanismEncoder.DEFAULT_CHUNK_SIZE;
        if (msgLength % ChunkedFramingMechanismEncoder.DEFAULT_CHUNK_SIZE != 0) {
            chunkCount++;
        }

        byte[] endOfChunkBytes = FramingMechanism.CHUNK_END_STR.getBytes(StandardCharsets.US_ASCII);
        for (int i = 1; i <= chunkCount; i++) {
            ByteBuf recievedOutbound = (ByteBuf) messages.poll();
            int exptHeaderLength = ChunkedFramingMechanismEncoder.DEFAULT_CHUNK_SIZE;
            if (i == chunkCount) {
                exptHeaderLength = msgLength - ChunkedFramingMechanismEncoder.DEFAULT_CHUNK_SIZE * (i - 1);
                byte[] eom = new byte[endOfChunkBytes.length];
                recievedOutbound.getBytes(recievedOutbound.readableBytes() - endOfChunkBytes.length, eom);
                assertArrayEquals(endOfChunkBytes, eom);
            }

            byte[] header = new byte[String.valueOf(exptHeaderLength).length() + 3];
            recievedOutbound.getBytes(0, header);
            assertEquals(exptHeaderLength, getHeaderLength(header));

            testChunkChannel.writeInbound(recievedOutbound);
        }
        assertEquals(0, messages.size());

        NetconfMessage receivedMessage = testChunkChannel.readInbound();
        assertNotNull(receivedMessage);
        XMLUnit.setIgnoreWhitespace(true);
        assertXMLEqual(msg.getDocument(), receivedMessage.getDocument());
    }

    @Test
    public void testEOMFramingMechanismOnPipeline() throws Exception {
        EmbeddedChannel testChunkChannel = new EmbeddedChannel(
                FramingMechanismHandlerFactory.createHandler(FramingMechanism.EOM),
                new NetconfMessageToXMLEncoder(), new NetconfEOMAggregator(), new NetconfXMLToMessageDecoder());

        testChunkChannel.writeOutbound(msg);
        ByteBuf recievedOutbound = testChunkChannel.readOutbound();

        byte[] endOfMsgBytes = FramingMechanism.EOM_STR.getBytes(StandardCharsets.US_ASCII);
        byte[] eom = new byte[endOfMsgBytes.length];
        recievedOutbound.getBytes(recievedOutbound.readableBytes() - endOfMsgBytes.length, eom);
        assertArrayEquals(endOfMsgBytes, eom);

        testChunkChannel.writeInbound(recievedOutbound);
        NetconfMessage receivedMessage = testChunkChannel.readInbound();
        assertNotNull(receivedMessage);
        XMLUnit.setIgnoreWhitespace(true);
        assertXMLEqual(msg.getDocument(), receivedMessage.getDocument());
    }

    private static long getHeaderLength(final byte[] bytes) {
        byte[] headerStart = new byte[]{(byte) 0x0a, (byte) 0x23};
        return Long.parseLong(StandardCharsets.US_ASCII.decode(
                ByteBuffer.wrap(bytes, headerStart.length, bytes.length - headerStart.length - 1)).toString());
    }
}
