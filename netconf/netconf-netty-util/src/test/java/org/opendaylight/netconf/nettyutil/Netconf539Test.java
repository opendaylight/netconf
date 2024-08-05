/*
 * Copyright (c) 2018 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.opendaylight.netconf.nettyutil.AbstractChannelInitializer.NETCONF_MESSAGE_FRAME_DECODER;
import static org.opendaylight.netconf.nettyutil.AbstractChannelInitializer.NETCONF_MESSAGE_FRAME_ENCODER;

import io.netty.channel.ChannelInboundHandlerAdapter;
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
import org.opendaylight.netconf.api.messages.HelloMessage;
import org.opendaylight.netconf.common.impl.DefaultNetconfTimer;
import org.opendaylight.netconf.nettyutil.handler.ChunkedFramingMechanismEncoder;
import org.opendaylight.netconf.nettyutil.handler.EOMFramingMechanismDecoder;
import org.opendaylight.netconf.nettyutil.handler.EOMFramingMechanismEncoder;
import org.opendaylight.netconf.nettyutil.handler.ChunkedFramingMechanismDecoder;
import org.opendaylight.netconf.nettyutil.handler.NetconfXMLToHelloMessageDecoder;
import org.opendaylight.netconf.test.util.XmlFileLoader;
import org.w3c.dom.Document;

@ExtendWith(MockitoExtension.class)
class Netconf539Test {
    @Mock
    private NetconfSessionListener<TestingNetconfSession> listener;
    @Mock
    private Promise<TestingNetconfSession> promise;

    private EmbeddedChannel channel;
    private TestSessionNegotiator negotiator;

    @BeforeEach
    void setUp() {
        channel = new EmbeddedChannel();
        channel.pipeline().addLast(AbstractChannelInitializer.NETCONF_MESSAGE_ENCODER,
            new ChannelInboundHandlerAdapter());
        channel.pipeline().addLast(AbstractChannelInitializer.NETCONF_MESSAGE_DECODER,
            new NetconfXMLToHelloMessageDecoder());
        channel.pipeline().addLast(NETCONF_MESSAGE_FRAME_ENCODER, new EOMFramingMechanismEncoder());
        channel.pipeline().addLast(NETCONF_MESSAGE_FRAME_DECODER, new EOMFramingMechanismDecoder());
        final HelloMessage serverHello = HelloMessage.createClientHello(Set.of(CapabilityURN.BASE_1_1),
            Optional.empty());
        negotiator = new TestSessionNegotiator(serverHello, promise, channel, new DefaultNetconfTimer(), listener,
            100L);
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
        final Document helloDocument = XmlFileLoader.xmlFileToDocument(fileName);
        negotiator.startNegotiation();
        final HelloMessage helloMessage = new HelloMessage(helloDocument);
        final TestingNetconfSession session = negotiator.getSessionForHelloMessage(helloMessage);
        assertNotNull(session);
        assertInstanceOf(ChunkedFramingMechanismDecoder.class, channel.pipeline().get(NETCONF_MESSAGE_FRAME_DECODER),
            "NetconfChunkAggregator was not installed in the Netconf pipeline");
        assertInstanceOf(ChunkedFramingMechanismEncoder.class, channel.pipeline().get(NETCONF_MESSAGE_FRAME_ENCODER),
            "ChunkedFramingMechanismEncoder was not installed in the Netconf pipeline");
    }
}
