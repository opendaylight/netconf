/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil.handler;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.Iterables;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Test;
import org.opendaylight.netconf.api.messages.HelloMessage;
import org.opendaylight.netconf.api.xml.XmlUtil;

class HelloXMLMessageDecoderTest {
    @Test
    void testDecodeWithHeader() throws Exception {
        final ByteBuf src = Unpooled.wrappedBuffer(String.format("%s\n%s",
                "[tomas;10.0.0.0:10000;tcp;client;]",
                "<hello xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\"/>").getBytes());
        final List<Object> out = new ArrayList<>();
        new HelloXMLMessageDecoder().decode(null, src, out);

        assertEquals(1, out.size());
        final HelloMessage hello = assertInstanceOf(HelloMessage.class, out.get(0));
        assertTrue(hello.getAdditionalHeader().isPresent());
        assertEquals("[tomas;10.0.0.0:10000;tcp;client;]" + System.lineSeparator(),
                hello.getAdditionalHeader().orElseThrow().toFormattedString());
        assertThat(XmlUtil.toString(hello.getDocument()),
                CoreMatchers.containsString("<hello xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\""));
    }

    @Test
    void testDecodeNoHeader() throws Exception {
        final ByteBuf src =
                Unpooled.wrappedBuffer("<hello xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\"/>".getBytes());
        final List<Object> out = new ArrayList<>();
        new HelloXMLMessageDecoder().decode(null, src, out);

        assertEquals(1, out.size());
        final HelloMessage hello = assertInstanceOf(HelloMessage.class, out.get(0));
        assertFalse(hello.getAdditionalHeader().isPresent());
    }

    @Test
    void testDecodeCaching() throws Exception {
        final ByteBuf msg1 =
                Unpooled.wrappedBuffer("<rpc-reply xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\"/>".getBytes());
        final ByteBuf msg2 =
                Unpooled.wrappedBuffer("<rpc-reply xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\"/>".getBytes());
        final ByteBuf src =
                Unpooled.wrappedBuffer("<hello xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\"/>".getBytes());
        final List<Object> out = new ArrayList<>();
        final HelloXMLMessageDecoder decoder = new HelloXMLMessageDecoder();
        decoder.decode(null, src, out);
        decoder.decode(null, msg1, out);
        decoder.decode(null, msg2, out);

        assertEquals(1, out.size());

        assertEquals(2, Iterables.size(decoder.getPostHelloNetconfMessages()));
    }

    @Test
    void testDecodeNotHelloReceived() {
        final ByteBuf msg1 =
                Unpooled.wrappedBuffer("<rpc-reply xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\"/>".getBytes());
        final List<Object> out = new ArrayList<>();
        HelloXMLMessageDecoder decoder = new HelloXMLMessageDecoder();
        assertThrows(IllegalStateException.class, () -> decoder.decode(null, msg1, out));
    }
}
