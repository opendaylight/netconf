/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil.handler;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.netconf.api.messages.HelloMessage;
import org.opendaylight.netconf.api.messages.NetconfHelloMessageAdditionalHeader;
import org.opendaylight.netconf.api.messages.NetconfMessage;
import org.opendaylight.netconf.api.xml.XmlUtil;

@ExtendWith(MockitoExtension.class)
class HelloXMLMessageEncoderTest {
    @Mock
    private ChannelHandlerContext ctx;

    @Test
    void testEncode() throws Exception {
        final NetconfMessage msg = new HelloMessage(XmlUtil.readXmlToDocument(
                "<hello xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\"/>"),
                NetconfHelloMessageAdditionalHeader.fromString("[tomas;10.0.0.0:10000;tcp;client;]"));
        final ByteBuf destination = Unpooled.buffer();
        new HelloXMLMessageEncoder().encode(ctx, msg, destination);

        final String encoded = new String(destination.array());
        assertThat(encoded, containsString("[tomas;10.0.0.0:10000;tcp;client;]"));
        assertThat(encoded, containsString("<hello xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\"/>"));
    }

    @Test
    void testEncodeNoHeader() throws Exception {
        final NetconfMessage msg = new HelloMessage(XmlUtil.readXmlToDocument(
                "<hello xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\"/>"));
        final ByteBuf destination = Unpooled.buffer();
        new HelloXMLMessageEncoder().encode(ctx, msg, destination);

        final String encoded = new String(destination.array());
        assertThat(encoded, not(containsString("[tomas;10.0.0.0:10000;tcp;client;]")));
        assertThat(encoded, containsString("<hello xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\"/>"));
    }

    @Test
    void testEncodeNotHello() throws Exception {
        final NetconfMessage msg = new NetconfMessage(XmlUtil.readXmlToDocument(
                "<hello xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\"/>"));
        assertThrows(IllegalStateException.class, () -> new HelloXMLMessageEncoder().encode(ctx, msg, null));
    }
}
