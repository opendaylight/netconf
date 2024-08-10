/*
 * Copyright (c) 2018 FRINX s.r.o., and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.List;
import org.junit.jupiter.api.Test;

class EOMFrameDecoderTest {
    private static final String COMM_1 = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rpc-reply message-id="105"
        xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
        <config xmlns="http://example.com/schema/1.2/config">
        <users>
        <user><name>root</name><type>superuser</type></user>
        <user><name>fred</name><type>admin</type></user>
        <user><name>barney</name><type>admin</type></user>
        </users>
        </config>
        </rpc-reply>
        ]]>]]>\
        <?xml version="1.0" encoding="UTF-8"?>
        <rpc-reply message-id="106"
        xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
        <config xmlns="http://example.com/schema/1.2/config">
        <users>
        <user><name>root</name><type>superuser</type></user>
        <user><name>fred</name><type>admin</type></user>
        <user><name>barney</name><type>admin</type></user>
        <user><name>joe</name><type>user</type></user>
        </users>
        </config>
        </rpc-reply>
        ]]>]]>
        """;

    private static final String COMM_1_M_1 = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rpc-reply message-id="105"
        xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
        <config xmlns="http://example.com/schema/1.2/config">
        <users>
        <user><name>root</name><type>superuser</type></user>
        <user><name>fred</name><type>admin</type></user>
        <user><name>barney</name><type>admin</type></user>
        </users>
        </config>
        </rpc-reply>
        """;

    private static final String COMM_1_M_2 = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rpc-reply message-id="106"
        xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
        <config xmlns="http://example.com/schema/1.2/config">
        <users>
        <user><name>root</name><type>superuser</type></user>
        <user><name>fred</name><type>admin</type></user>
        <user><name>barney</name><type>admin</type></user>
        <user><name>joe</name><type>user</type></user>
        </users>
        </config>
        </rpc-reply>
        """;

    private static final String COMM_2 = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rpc-reply message-id="107"
        xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
        <config xmlns="http://example.com/schema/1.2/config">
        <cars>
        <car><name>porsche</name></car>
        <car><name>ford</name></car>
        </cars>
        </config>
        </rpc-reply>
        ]]>]]>
        """;
    private static final String COMM_2_M_1 = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rpc-reply message-id="107"
        xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
        <config xmlns="http://example.com/schema/1.2/config">
        <cars>
        <car><name>porsche</name></car>
        <car><name>ford</name></car>
        </cars>
        </config>
        </rpc-reply>
        """;
    private static final String COMM_3_S_1 = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rpc-reply message-id="105"
        xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
        <config xmlns="http://example.com/schema/1.2/config">
        <users>
        <user><name>root</name><type>superuser</type></user>
        <user><name>fred</name><type>admin</type></user>
        <user><name>barney</name><type>admin</type></user>
        </users>
        </config>
        </rpc-reply>
        ]]>]]>""";
    private static final String COMM_3_S_2 = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rpc-reply message-id="107"
        xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
        <config xmlns="http://example.com/schema/1.2/config">
        <cars>
        """;
    private static final String COMM_3_S_3 = """
        <car><name>porsche</name></car>
        <car><name>ford</name></car>
        </cars>
        </config>
        </rpc-reply>
        ]]>]]>
        """;

    private static final String COMM_3_M_1 = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rpc-reply message-id="105"
        xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
        <config xmlns="http://example.com/schema/1.2/config">
        <users>
        <user><name>root</name><type>superuser</type></user>
        <user><name>fred</name><type>admin</type></user>
        <user><name>barney</name><type>admin</type></user>
        </users>
        </config>
        </rpc-reply>
        """;
    private static final String COMM_3_M_2 = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rpc-reply message-id="107"
        xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
        <config xmlns="http://example.com/schema/1.2/config">
        <cars>
        <car><name>porsche</name></car>
        <car><name>ford</name></car>
        </cars>
        </config>
        </rpc-reply>
        """;

    private final EOMFrameDecoder decoder = new EOMFrameDecoder();

    @Test
    void testDecodeMessagesReadAtOnce() {
        final ByteBuf in = Unpooled.copiedBuffer(COMM_1.getBytes());
        final List<Object> out = new LinkedList<>();

        decoder.decode(null, in, out);
        assertEquals(2, out.size());
        assertEquals(COMM_1_M_1, byteBufToString((ByteBuf) out.get(0)));
        assertEquals(COMM_1_M_2, byteBufToString((ByteBuf) out.get(1)));
    }

    @Test
    void testDecodeMessagesReadByteByByte() {
        final ByteBuf in = Unpooled.buffer();
        final List<Object> out = new LinkedList<>();

        for (final byte b : COMM_1.getBytes()) {
            in.writeByte(b);
            decoder.decode(null, in, out);
        }

        assertEquals(2, out.size());
        assertEquals(COMM_1_M_1, byteBufToString((ByteBuf) out.get(0)));
        assertEquals(COMM_1_M_2, byteBufToString((ByteBuf) out.get(1)));
    }

    @Test
    void testDecodeMultipleStreams() {
        final ByteBuf in = Unpooled.copiedBuffer(COMM_1.getBytes());
        final List<Object> out = new LinkedList<>();

        decoder.decode(null, in, out);
        assertEquals(2, out.size());
        assertEquals(COMM_1_M_1, byteBufToString((ByteBuf) out.get(0)));
        assertEquals(COMM_1_M_2, byteBufToString((ByteBuf) out.get(1)));

        final ByteBuf in2 = Unpooled.copiedBuffer(COMM_2.getBytes());
        decoder.decode(null, in2, out);
        assertEquals(3, out.size());
        assertEquals(COMM_2_M_1, byteBufToString((ByteBuf) out.get(2)));
    }

    @Test
    void testDecodeBufferReset() {
        final ByteBuf in = Unpooled.buffer();
        final List<Object> out = new LinkedList<>();

        in.writeBytes((COMM_3_S_1 + COMM_3_S_2).getBytes());

        decoder.decode(null, in, out);
        assertEquals(1, out.size());
        assertEquals(COMM_3_M_1, byteBufToString((ByteBuf) out.get(0)));

        decoder.decode(null, in, out);
        assertEquals(1, out.size());

        in.clear();
        in.writeBytes((COMM_3_S_2 + COMM_3_S_3).getBytes());

        decoder.decode(null, in, out);
        assertEquals(2, out.size());
        assertEquals(COMM_3_M_2, byteBufToString((ByteBuf) out.get(1)));
    }

    @Test
    void testDecodeEmptyMessage() {
        final ByteBuf in = Unpooled.buffer();
        final List<Object> out = new LinkedList<>();

        for (final byte b : FramingParts.END_OF_MESSAGE) {
            in.writeByte(b);
            decoder.decode(null, in, out);
            assertEquals(0, decoder.bodyLength());
        }

        assertEquals(1, out.size());
        assertEquals("", byteBufToString((ByteBuf) out.get(0)));
    }

    private static String byteBufToString(final ByteBuf byteBuf) {
        return byteBuf.toString(Charset.defaultCharset());
    }
}
