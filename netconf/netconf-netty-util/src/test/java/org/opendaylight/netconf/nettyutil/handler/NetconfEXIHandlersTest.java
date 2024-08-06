/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil.handler;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.netty.buffer.Unpooled;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opendaylight.netconf.api.messages.NetconfMessage;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.nettyutil.handler.exi.EXIParameters;
import org.xmlunit.builder.DiffBuilder;

class NetconfEXIHandlersTest {
    private final String msgAsString = "<netconf-message/>";

    private EXIMessageEncoder netconfMessageToEXIEncoder;
    private EXIMessageDecoder netconfEXIMessageDecoder;
    private NetconfMessage msg;
    private byte[] msgAsExi;

    @BeforeEach
    void setUp() throws Exception {
        final var codec = NetconfEXICodec.forParameters(EXIParameters.empty());
        netconfMessageToEXIEncoder = EXIMessageEncoder.create(codec);
        netconfEXIMessageDecoder = EXIMessageDecoder.create(codec);

        msg = new NetconfMessage(XmlUtil.readXmlToDocument(msgAsString));
        msgAsExi = msgToExi(msg, codec);
    }

    private static byte[] msgToExi(final NetconfMessage msg, final NetconfEXICodec codec) throws Exception {
        final var bos = new ByteArrayOutputStream();
        final var encoder = codec.getWriter();
        encoder.setOutputStream(bos);
        ThreadLocalTransformers.getDefaultTransformer().transform(new DOMSource(msg.getDocument()),
            new SAXResult(encoder));
        return bos.toByteArray();
    }

    @Test
    void testEncodeDecode() throws Exception {
        final var buffer = Unpooled.buffer();
        netconfMessageToEXIEncoder.encode(null, msg, buffer);
        final int exiLength = msgAsExi.length;
        // array from buffer is cca 256 n length, compare only subarray
        assertArrayEquals(msgAsExi, Arrays.copyOfRange(buffer.array(), 0, exiLength));

        // assert all other bytes in buffer be 0
        for (int i = exiLength; i < buffer.array().length; i++) {
            assertEquals((byte)0, buffer.array()[i]);
        }

        final var out = new ArrayList<>();
        netconfEXIMessageDecoder.decode(null, buffer, out);

        final var diff = DiffBuilder.compare(msg.getDocument())
            .withTest(((NetconfMessage) out.get(0)).getDocument())
            .checkForIdentical()
            .build();
        assertFalse(diff.hasDifferences(), diff.toString());
    }
}
