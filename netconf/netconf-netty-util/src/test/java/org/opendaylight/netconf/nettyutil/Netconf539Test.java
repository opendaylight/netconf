/*
 * Copyright (c) 2018 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.concurrent.Promise;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.netconf.api.CapabilityURN;
import org.opendaylight.netconf.api.NetconfSessionListener;
import org.opendaylight.netconf.api.messages.FramingMechanism;
import org.opendaylight.netconf.api.messages.HelloMessage;
import org.opendaylight.netconf.codec.ChunkedFrameDecoder;
import org.opendaylight.netconf.codec.EOMFrameDecoder;
import org.opendaylight.netconf.codec.FrameDecoder;
import org.opendaylight.netconf.codec.MessageDecoder;
import org.opendaylight.netconf.codec.MessageEncoder;
import org.opendaylight.netconf.codec.XMLMessageWriter;
import org.opendaylight.netconf.common.di.DefaultNetconfTimer;
import org.opendaylight.netconf.nettyutil.handler.HelloXMLMessageDecoder;
import org.opendaylight.netconf.test.util.XmlFileLoader;

@ExtendWith(MockitoExtension.class)
class Netconf539Test {
    @Mock
    private NetconfSessionListener<TestingNetconfSession> listener;
    @Mock
    private Promise<TestingNetconfSession> promise;

    private final EmbeddedChannel channel = new EmbeddedChannel();
    private TestSessionNegotiator negotiator;

    @BeforeEach
    void setUp() {
        channel.pipeline()
            .addLast("mockEncoder", new MessageEncoder(XMLMessageWriter.of()))
            .addLast(MessageDecoder.HANDLER_NAME, new HelloXMLMessageDecoder())
            .addLast(FrameDecoder.HANDLER_NAME, new EOMFrameDecoder());
        negotiator = new TestSessionNegotiator(
            HelloMessage.createClientHello(Set.of(CapabilityURN.BASE_1_1), Optional.empty()), promise, channel,
            new DefaultNetconfTimer(), listener, 100L);
    }

    @Test
    void testGetSessionForHelloMessageDefaultNs() throws Exception {
        testGetSessionForHelloMessage("netconf539/client_hello_1.1.xml");
    }

    @Test
    void testGetSessionForHelloMessageNsPrefix() throws Exception {
        testGetSessionForHelloMessage("netconf539/client_hello_1.1_ns.xml");
    }

    private void testGetSessionForHelloMessage(final String fileName) throws Exception {
        final var helloDocument = XmlFileLoader.xmlFileToDocument(fileName);
        negotiator.startNegotiation();
        final var helloMessage = new HelloMessage(helloDocument);
        final var session = negotiator.getSessionForHelloMessage(helloMessage);
        assertNotNull(session);
        final var pipeline = channel.pipeline();
        assertInstanceOf(ChunkedFrameDecoder.class, pipeline.get(FrameDecoder.HANDLER_NAME),
            "NetconfChunkAggregator was not installed in the Netconf pipeline");
        assertEquals(FramingMechanism.CHUNK, pipeline.get(MessageEncoder.class).framing().mechanism(),
            "ChunkedFramingMechanismEncoder was not installed in the Netconf pipeline");
    }
}
