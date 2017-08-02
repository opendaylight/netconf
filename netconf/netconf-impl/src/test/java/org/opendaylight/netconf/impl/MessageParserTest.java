/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.impl;

import static org.custommonkey.xmlunit.XMLAssert.assertXMLEqual;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Queue;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.nettyutil.handler.ChunkedFramingMechanismEncoder;
import org.opendaylight.netconf.nettyutil.handler.FramingMechanismHandlerFactory;
import org.opendaylight.netconf.nettyutil.handler.NetconfChunkAggregator;
import org.opendaylight.netconf.nettyutil.handler.NetconfEOMAggregator;
import org.opendaylight.netconf.nettyutil.handler.NetconfMessageToXMLEncoder;
import org.opendaylight.netconf.nettyutil.handler.NetconfXMLToMessageDecoder;
import org.opendaylight.netconf.util.messages.FramingMechanism;
import org.opendaylight.netconf.util.messages.NetconfMessageConstants;
import org.opendaylight.netconf.util.test.XmlFileLoader;

public class MessageParserTest {

    private NetconfMessage msg;

    @Before
    public void setUp() throws Exception {
        this.msg = XmlFileLoader.xmlFileToNetconfMessage("netconfMessages/getConfig.xml");
    }

    @Test
    public void testChunkedFramingMechanismOnPipeline() throws Exception {
        EmbeddedChannel testChunkChannel = new EmbeddedChannel(
                FramingMechanismHandlerFactory.createHandler(FramingMechanism.CHUNK),
                new NetconfMessageToXMLEncoder(),

                new NetconfChunkAggregator(),
                new NetconfXMLToMessageDecoder());

        testChunkChannel.writeOutbound(this.msg);
        Queue<Object> messages = testChunkChannel.outboundMessages();
        assertFalse(messages.isEmpty());

        final NetconfMessageToXMLEncoder enc = new NetconfMessageToXMLEncoder();
        final ByteBuf out = Unpooled.buffer();
        enc.encode(null, msg, out);
        int msgLength = out.readableBytes();

        int chunkCount = msgLength / ChunkedFramingMechanismEncoder.DEFAULT_CHUNK_SIZE;
        if ((msgLength % ChunkedFramingMechanismEncoder.DEFAULT_CHUNK_SIZE) != 0) {
            chunkCount++;
        }
        for (int i = 1; i <= chunkCount; i++) {
            ByteBuf recievedOutbound = (ByteBuf) messages.poll();
            int exptHeaderLength = ChunkedFramingMechanismEncoder.DEFAULT_CHUNK_SIZE;
            if (i == chunkCount) {
                exptHeaderLength = msgLength - (ChunkedFramingMechanismEncoder.DEFAULT_CHUNK_SIZE * (i - 1));
                byte[] eom = new byte[NetconfMessageConstants.END_OF_CHUNK.length];
                recievedOutbound
                        .getBytes(recievedOutbound.readableBytes() - NetconfMessageConstants.END_OF_CHUNK.length, eom);
                assertArrayEquals(NetconfMessageConstants.END_OF_CHUNK, eom);
            }

            byte[] header = new byte[String.valueOf(exptHeaderLength).length()
                    + NetconfMessageConstants.MIN_HEADER_LENGTH - 1];
            recievedOutbound.getBytes(0, header);
            assertEquals(exptHeaderLength, getHeaderLength(header));

            testChunkChannel.writeInbound(recievedOutbound);
        }
        assertTrue(messages.isEmpty());

        NetconfMessage receivedMessage = testChunkChannel.readInbound();
        assertNotNull(receivedMessage);
        assertXMLEqual(this.msg.getDocument(), receivedMessage.getDocument());
    }

    @Test
    public void testEOMFramingMechanismOnPipeline() throws Exception {
        EmbeddedChannel testChunkChannel = new EmbeddedChannel(
                FramingMechanismHandlerFactory.createHandler(FramingMechanism.EOM),
                new NetconfMessageToXMLEncoder(), new NetconfEOMAggregator(), new NetconfXMLToMessageDecoder());

        testChunkChannel.writeOutbound(this.msg);
        ByteBuf recievedOutbound = testChunkChannel.readOutbound();

        byte[] eom = new byte[NetconfMessageConstants.END_OF_MESSAGE.length];
        recievedOutbound.getBytes(recievedOutbound.readableBytes() - NetconfMessageConstants.END_OF_MESSAGE.length,
                eom);
        assertArrayEquals(NetconfMessageConstants.END_OF_MESSAGE, eom);

        testChunkChannel.writeInbound(recievedOutbound);
        NetconfMessage receivedMessage = testChunkChannel.readInbound();
        assertNotNull(receivedMessage);
        assertXMLEqual(this.msg.getDocument(), receivedMessage.getDocument());
    }

    private static long getHeaderLength(byte[] bytes) {
        byte[] headerStart = new byte[]{(byte) 0x0a, (byte) 0x23};
        return Long.parseLong(StandardCharsets.US_ASCII.decode(
                ByteBuffer.wrap(bytes, headerStart.length, bytes.length - headerStart.length - 1)).toString());
    }
}
