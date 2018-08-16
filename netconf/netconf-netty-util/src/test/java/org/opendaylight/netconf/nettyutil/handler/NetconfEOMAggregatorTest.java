/*
 * Copyright (c) 2018 FRINX s.r.o., and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil.handler;

import static org.junit.Assert.assertEquals;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class NetconfEOMAggregatorTest {

    private static final String COMM_1 = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<rpc-reply message-id=\"105\"\n"
            + "xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n"
            + "<config xmlns=\"http://example.com/schema/1.2/config\">\n"
            + "<users>\n"
            + "<user><name>root</name><type>superuser</type></user>\n"
            + "<user><name>fred</name><type>admin</type></user>\n"
            + "<user><name>barney</name><type>admin</type></user>\n"
            + "</users>\n"
            + "</config>\n"
            + "</rpc-reply>\n"
            + "]]>]]>"
            + "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<rpc-reply message-id=\"106\"\n"
            + "xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n"
            + "<config xmlns=\"http://example.com/schema/1.2/config\">\n"
            + "<users>\n"
            + "<user><name>root</name><type>superuser</type></user>\n"
            + "<user><name>fred</name><type>admin</type></user>\n"
            + "<user><name>barney</name><type>admin</type></user>\n"
            + "<user><name>joe</name><type>user</type></user>\n"
            + "</users>\n"
            + "</config>\n"
            + "</rpc-reply>\n"
            + "]]>]]>";

    private static final String COMM_1_M_1 = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<rpc-reply message-id=\"105\"\n"
            + "xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n"
            + "<config xmlns=\"http://example.com/schema/1.2/config\">\n"
            + "<users>\n"
            + "<user><name>root</name><type>superuser</type></user>\n"
            + "<user><name>fred</name><type>admin</type></user>\n"
            + "<user><name>barney</name><type>admin</type></user>\n"
            + "</users>\n"
            + "</config>\n"
            + "</rpc-reply>\n";
    private static final String COMM_1_M_2 = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<rpc-reply message-id=\"106\"\n"
            + "xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n"
            + "<config xmlns=\"http://example.com/schema/1.2/config\">\n"
            + "<users>\n"
            + "<user><name>root</name><type>superuser</type></user>\n"
            + "<user><name>fred</name><type>admin</type></user>\n"
            + "<user><name>barney</name><type>admin</type></user>\n"
            + "<user><name>joe</name><type>user</type></user>\n"
            + "</users>\n"
            + "</config>\n"
            + "</rpc-reply>\n";

    private static final String COMM_2 = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<rpc-reply message-id=\"107\"\n"
            + "xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n"
            + "<config xmlns=\"http://example.com/schema/1.2/config\">\n"
            + "<cars>\n"
            + "<car><name>porsche</name></car>\n"
            + "<car><name>ford</name></car>\n"
            + "</cars>\n"
            + "</config>\n"
            + "</rpc-reply>\n"
            + "]]>]]>";

    private static final String COMM_2_M_1 = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<rpc-reply message-id=\"107\"\n"
            + "xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n"
            + "<config xmlns=\"http://example.com/schema/1.2/config\">\n"
            + "<cars>\n"
            + "<car><name>porsche</name></car>\n"
            + "<car><name>ford</name></car>\n"
            + "</cars>\n"
            + "</config>\n"
            + "</rpc-reply>\n";

    private static final String COMM_3_S_1 = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<rpc-reply message-id=\"105\"\n"
            + "xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n"
            + "<config xmlns=\"http://example.com/schema/1.2/config\">\n"
            + "<users>\n"
            + "<user><name>root</name><type>superuser</type></user>\n"
            + "<user><name>fred</name><type>admin</type></user>\n"
            + "<user><name>barney</name><type>admin</type></user>\n"
            + "</users>\n"
            + "</config>\n"
            + "</rpc-reply>\n"
            + "]]>]]>";
    private static final String COMM_3_S_2 = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<rpc-reply message-id=\"107\"\n"
            + "xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n"
            + "<config xmlns=\"http://example.com/schema/1.2/config\">\n"
            + "<cars>\n";
    private static final String COMM_3_S_3 = "<car><name>porsche</name></car>\n"
            + "<car><name>ford</name></car>\n"
            + "</cars>\n"
            + "</config>\n"
            + "</rpc-reply>\n"
            + "]]>]]>";

    private static final String COMM_3_M_1 = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<rpc-reply message-id=\"105\"\n"
            + "xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n"
            + "<config xmlns=\"http://example.com/schema/1.2/config\">\n"
            + "<users>\n"
            + "<user><name>root</name><type>superuser</type></user>\n"
            + "<user><name>fred</name><type>admin</type></user>\n"
            + "<user><name>barney</name><type>admin</type></user>\n"
            + "</users>\n"
            + "</config>\n"
            + "</rpc-reply>\n";
    private static final String COMM_3_M_2 = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<rpc-reply message-id=\"107\"\n"
            + "xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n"
            + "<config xmlns=\"http://example.com/schema/1.2/config\">\n"
            + "<cars>\n"
            + "<car><name>porsche</name></car>\n"
            + "<car><name>ford</name></car>\n"
            + "</cars>\n"
            + "</config>\n"
            + "</rpc-reply>\n";

    private static NetconfEOMAggregator aggregator;

    @Before
    public void setUp() throws Exception {
        aggregator = new NetconfEOMAggregator();
    }

    @Test
    public void testDecodeMessagesReadAtOnce() {
        final ByteBuf in = Unpooled.copiedBuffer(COMM_1.getBytes());
        final List<Object> out = new LinkedList<>();

        aggregator.decode(null, in, out);
        assertEquals(2, out.size());
        assertEquals(COMM_1_M_1, byteBufToString((ByteBuf) out.get(0)));
        assertEquals(COMM_1_M_2, byteBufToString((ByteBuf) out.get(1)));
    }

    @Test
    public void testDecodeMessagesReadByteByByte() {
        final ByteBuf in = Unpooled.buffer();
        final List<Object> out = new LinkedList<>();

        for (final byte b : COMM_1.getBytes()) {
            in.writeByte(b);
            aggregator.decode(null, in, out);
        }

        assertEquals(2, out.size());
        assertEquals(COMM_1_M_1, byteBufToString((ByteBuf) out.get(0)));
        assertEquals(COMM_1_M_2, byteBufToString((ByteBuf) out.get(1)));
    }

    @Test
    public void testDecodeMultipleStreams() {
        final ByteBuf in = Unpooled.copiedBuffer(COMM_1.getBytes());
        final List<Object> out = new LinkedList<>();

        aggregator.decode(null, in, out);
        assertEquals(2, out.size());
        assertEquals(COMM_1_M_1, byteBufToString((ByteBuf) out.get(0)));
        assertEquals(COMM_1_M_2, byteBufToString((ByteBuf) out.get(1)));

        final ByteBuf in2 = Unpooled.copiedBuffer(COMM_2.getBytes());
        aggregator.decode(null, in2, out);
        assertEquals(3, out.size());
        assertEquals(COMM_2_M_1, byteBufToString((ByteBuf) out.get(2)));
    }

    @Test
    public void testDecodeBufferReset() {
        final ByteBuf in = Unpooled.buffer();
        final List<Object> out = new LinkedList<>();

        in.writeBytes((COMM_3_S_1 + COMM_3_S_2).getBytes());

        aggregator.decode(null, in, out);
        assertEquals(1, out.size());
        assertEquals(COMM_3_M_1, byteBufToString((ByteBuf) out.get(0)));

        aggregator.decode(null, in, out);
        assertEquals(1, out.size());

        in.clear();
        in.writeBytes((COMM_3_S_2 + COMM_3_S_3).getBytes());

        aggregator.decode(null, in, out);
        assertEquals(2, out.size());
        assertEquals(COMM_3_M_2, byteBufToString((ByteBuf) out.get(1)));
    }

    @Test
    public void testDecodeEmptyMessage() {
        final ByteBuf in = Unpooled.buffer();
        final List<Object> out = new LinkedList<>();

        for (final byte b : MessageParts.END_OF_MESSAGE) {
            assertEquals(0, aggregator.getBodyLength());
            in.writeByte(b);
            aggregator.decode(null, in, out);
        }

        assertEquals(1, out.size());
        assertEquals("", byteBufToString((ByteBuf) out.get(0)));
    }

    private static String byteBufToString(final ByteBuf byteBuf) {
        return byteBuf.toString(Charset.defaultCharset());
    }
}
