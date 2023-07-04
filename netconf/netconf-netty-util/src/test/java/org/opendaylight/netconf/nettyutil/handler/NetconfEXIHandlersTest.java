/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil.handler;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;
import org.custommonkey.xmlunit.XMLUnit;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.nettyutil.handler.exi.EXIParameters;
import org.opendaylight.netconf.shaded.exificient.core.exceptions.EXIException;
import org.opendaylight.netconf.shaded.exificient.main.api.sax.SAXEncoder;

public class NetconfEXIHandlersTest {

    private final String msgAsString = "<netconf-message/>";
    private NetconfMessageToEXIEncoder netconfMessageToEXIEncoder;
    private NetconfEXIToMessageDecoder netconfEXIToMessageDecoder;
    private NetconfMessage msg;
    private byte[] msgAsExi;

    @Before
    public void setUp() throws Exception {
        final NetconfEXICodec codec = NetconfEXICodec.forParameters(EXIParameters.empty());
        netconfMessageToEXIEncoder = NetconfMessageToEXIEncoder.create(codec);
        netconfEXIToMessageDecoder = NetconfEXIToMessageDecoder.create(codec);

        msg = NetconfMessage.of(XmlUtil.readXmlToDocument(msgAsString));
        msgAsExi = msgToExi(msg, codec);
    }

    private static byte[] msgToExi(final NetconfMessage msg, final NetconfEXICodec codec)
            throws IOException, EXIException, TransformerException {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        final SAXEncoder encoder = codec.getWriter();
        encoder.setOutputStream(byteArrayOutputStream);
        ThreadLocalTransformers.getDefaultTransformer().transform(new DOMSource(msg.getDocument()),
                new SAXResult(encoder));
        return byteArrayOutputStream.toByteArray();
    }

    @Test
    public void testEncodeDecode() throws Exception {
        final ByteBuf buffer = Unpooled.buffer();
        netconfMessageToEXIEncoder.encode(null, msg, buffer);
        final int exiLength = msgAsExi.length;
        // array from buffer is cca 256 n length, compare only subarray
        assertArrayEquals(msgAsExi, Arrays.copyOfRange(buffer.array(), 0, exiLength));

        // assert all other bytes in buffer be 0
        for (int i = exiLength; i < buffer.array().length; i++) {
            assertEquals((byte)0, buffer.array()[i]);
        }

        final List<Object> out = new ArrayList<>();
        netconfEXIToMessageDecoder.decode(null, buffer, out);

        XMLUnit.compareXML(msg.getDocument(), ((NetconfMessage) out.get(0)).getDocument());
    }
}
