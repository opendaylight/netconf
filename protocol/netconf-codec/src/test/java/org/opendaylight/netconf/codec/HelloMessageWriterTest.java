/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.codec;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.netty.channel.ChannelHandlerContext;
import java.io.ByteArrayOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.netconf.api.messages.HelloMessage;
import org.opendaylight.netconf.api.messages.NetconfHelloMessageAdditionalHeader;
import org.opendaylight.netconf.api.messages.NetconfMessage;
import org.opendaylight.netconf.api.xml.XmlUtil;

@ExtendWith(MockitoExtension.class)
class HelloMessageWriterTest {
    @Mock
    private ChannelHandlerContext ctx;

    private final HelloMessageWriter encoder = HelloMessageWriter.of();
    private final ByteArrayOutputStream baos = new ByteArrayOutputStream();

    @Test
    void testEncode() throws Exception {
        encoder.writeMessage(new HelloMessage(XmlUtil.readXmlToDocument(
                "<hello xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\"/>"),
                NetconfHelloMessageAdditionalHeader.fromString("[tomas;10.0.0.0:10000;tcp;client;]")), baos);

        final var encoded = new String(baos.toByteArray());
        assertThat(encoded, containsString("[tomas;10.0.0.0:10000;tcp;client;]"));
        assertThat(encoded, containsString("<hello xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\"/>"));
    }

    @Test
    void testEncodeNoHeader() throws Exception {
        encoder.writeMessage(new HelloMessage(XmlUtil.readXmlToDocument(
                "<hello xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\"/>")), baos);

        final var encoded = new String(baos.toByteArray());
        assertThat(encoded, not(containsString("[tomas;10.0.0.0:10000;tcp;client;]")));
        assertThat(encoded, containsString("<hello xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\"/>"));
    }

    @Test
    void testEncodeNotHello() throws Exception {
        final var msg = new NetconfMessage(XmlUtil.readXmlToDocument(
                "<hello xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\"/>"));
        assertThrows(IllegalStateException.class, () -> encoder.writeMessage(msg, baos));
    }
}
