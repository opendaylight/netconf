/*
 * Copyright (c) 2018 FRINX s.r.o., and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.nettyutil.handler;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

public class NetconfEOMAggregatorTest {

    private static final String COMM_1 = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"+
            "<rpc-reply message-id=\"105\"\n"+
            "xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n"+
            "<config xmlns=\"http://example.com/schema/1.2/config\">\n"+
            "<users>\n"+
            "<user><name>root</name><type>superuser</type></user>\n"+
            "<user><name>fred</name><type>admin</type></user>\n"+
            "<user><name>barney</name><type>admin</type></user>\n"+
            "</users>\n"+
            "</config>\n"+
            "</rpc-reply>\n"+
            "]]>]]>" +
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"+
            "<rpc-reply message-id=\"106\"\n"+
            "xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n"+
            "<config xmlns=\"http://example.com/schema/1.2/config\">\n"+
            "<users>\n"+
            "<user><name>root</name><type>superuser</type></user>\n"+
            "<user><name>fred</name><type>admin</type></user>\n"+
            "<user><name>barney</name><type>admin</type></user>\n"+
            "<user><name>joe</name><type>user</type></user>\n"+
            "</users>\n"+
            "</config>\n"+
            "</rpc-reply>\n"+
            "]]>]]>";

    private static final String COMM_1_M_105 = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"+
            "<rpc-reply message-id=\"105\"\n"+
            "xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n"+
            "<config xmlns=\"http://example.com/schema/1.2/config\">\n"+
            "<users>\n"+
            "<user><name>root</name><type>superuser</type></user>\n"+
            "<user><name>fred</name><type>admin</type></user>\n"+
            "<user><name>barney</name><type>admin</type></user>\n"+
            "</users>\n"+
            "</config>\n"+
            "</rpc-reply>\n";

    private static final String COMM_1_M_106 = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"+
            "<rpc-reply message-id=\"106\"\n"+
            "xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n"+
            "<config xmlns=\"http://example.com/schema/1.2/config\">\n"+
            "<users>\n"+
            "<user><name>root</name><type>superuser</type></user>\n"+
            "<user><name>fred</name><type>admin</type></user>\n"+
            "<user><name>barney</name><type>admin</type></user>\n"+
            "<user><name>joe</name><type>user</type></user>\n"+
            "</users>\n"+
            "</config>\n"+
            "</rpc-reply>\n";

    private static final String COMM_2 = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"+
            "<rpc-reply message-id=\"107\"\n"+
            "xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n"+
            "<config xmlns=\"http://example.com/schema/1.2/config\">\n"+
            "<cars>\n"+
            "<car><name>porsche</name></car>\n"+
            "<car><name>ford</name></car>\n"+
            "</cars>\n"+
            "</config>\n"+
            "</rpc-reply>\n" +
            "]]>]]>";


    private static final String COMM_2_M_107 = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"+
            "<rpc-reply message-id=\"107\"\n"+
            "xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n"+
            "<config xmlns=\"http://example.com/schema/1.2/config\">\n"+
            "<cars>\n"+
            "<car><name>porsche</name></car>\n"+
            "<car><name>ford</name></car>\n"+
            "</cars>\n"+
            "</config>\n"+
            "</rpc-reply>\n";

    @Test
    public void testDecodeMessagesReadAtOnce() {
        final NetconfEOMAggregator aggregator = new NetconfEOMAggregator();
        final ByteBuf in = Unpooled.copiedBuffer(COMM_1.getBytes());
        final List<Object> out = new LinkedList<>();

        aggregator.decode(null, in, out);
        Assert.assertEquals(1, out.size());
        Assert.assertEquals(COMM_1_M_105, byteBufToString((ByteBuf) out.get(0)));

        aggregator.decode(null, in, out);
        Assert.assertEquals(2, out.size());
        Assert.assertEquals(COMM_1_M_106, byteBufToString((ByteBuf) out.get(1)));
    }

    @Test
    public void testDecodeMessagesReadByteByByte() {
        final NetconfEOMAggregator aggregator = new NetconfEOMAggregator();
        final ByteBuf in = Unpooled.buffer();
        final List<Object> out = new LinkedList<>();

        for (final byte b : COMM_1.getBytes()) {
            in.writeByte(b);
            aggregator.decode(null, in, out);
        }

        Assert.assertEquals(2, out.size());
        Assert.assertEquals(COMM_1_M_105, byteBufToString((ByteBuf) out.get(0)));
        Assert.assertEquals(COMM_1_M_106, byteBufToString((ByteBuf) out.get(1)));
    }

    @Test
    public void testDecodeMultipleStreams() {
        final NetconfEOMAggregator aggregator = new NetconfEOMAggregator();
        final ByteBuf in = Unpooled.copiedBuffer(COMM_1.getBytes());
        final List<Object> out = new LinkedList<>();

        aggregator.decode(null, in, out);
        Assert.assertEquals(1, out.size());
        Assert.assertEquals(COMM_1_M_105, byteBufToString((ByteBuf) out.get(0)));

        aggregator.decode(null, in, out);
        Assert.assertEquals(2, out.size());
        Assert.assertEquals(COMM_1_M_106, byteBufToString((ByteBuf) out.get(1)));

        final ByteBuf in2 = Unpooled.copiedBuffer(COMM_2.getBytes());
        aggregator.decode(null, in2, out);
        Assert.assertEquals(3, out.size());
        Assert.assertEquals(COMM_2_M_107, byteBufToString((ByteBuf) out.get(2)));

    }

    private static String byteBufToString(final ByteBuf byteBuf) {
        return byteBuf.toString(Charset.defaultCharset());
    }

}