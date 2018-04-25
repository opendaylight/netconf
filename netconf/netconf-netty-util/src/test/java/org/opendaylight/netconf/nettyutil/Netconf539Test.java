/*
 * Copyright (c) 2018 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.nettyutil;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.opendaylight.netconf.nettyutil.AbstractChannelInitializer.NETCONF_MESSAGE_AGGREGATOR;
import static org.opendaylight.netconf.nettyutil.AbstractChannelInitializer.NETCONF_MESSAGE_FRAME_ENCODER;

import com.google.common.base.Optional;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.HashedWheelTimer;
import io.netty.util.concurrent.Promise;
import java.util.Collections;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.netconf.api.NetconfSessionListener;
import org.opendaylight.netconf.api.NetconfSessionPreferences;
import org.opendaylight.netconf.api.messages.NetconfHelloMessage;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.nettyutil.handler.ChunkedFramingMechanismEncoder;
import org.opendaylight.netconf.nettyutil.handler.FramingMechanismHandlerFactory;
import org.opendaylight.netconf.nettyutil.handler.NetconfChunkAggregator;
import org.opendaylight.netconf.nettyutil.handler.NetconfEOMAggregator;
import org.opendaylight.netconf.nettyutil.handler.NetconfXMLToHelloMessageDecoder;
import org.opendaylight.netconf.util.messages.FramingMechanism;
import org.opendaylight.netconf.util.test.XmlFileLoader;
import org.w3c.dom.Document;

public class Netconf539Test {
    @Mock
    private NetconfSessionListener<TestingNetconfSession> listener;
    @Mock
    private Promise<TestingNetconfSession> promise;

    private EmbeddedChannel channel;
    private AbstractNetconfSessionNegotiator negotiator;
    private NetconfSessionPreferences prefs;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        channel = new EmbeddedChannel();
        channel.pipeline().addLast(AbstractChannelInitializer.NETCONF_MESSAGE_ENCODER,
            new ChannelInboundHandlerAdapter());
        channel.pipeline().addLast(AbstractChannelInitializer.NETCONF_MESSAGE_DECODER,
            new NetconfXMLToHelloMessageDecoder());
        channel.pipeline().addLast(NETCONF_MESSAGE_FRAME_ENCODER,
            FramingMechanismHandlerFactory.createHandler(FramingMechanism.EOM));
        channel.pipeline().addLast(NETCONF_MESSAGE_AGGREGATOR, new NetconfEOMAggregator());
        final NetconfHelloMessage serverHello = NetconfHelloMessage.createClientHello(Collections
            .singleton(XmlNetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_1), Optional.absent());
        doReturn(promise).when(promise).setFailure(any());
        doReturn(promise).when(promise).setSuccess(any());
        negotiator = new TestSessionNegotiator(new NetconfSessionPreferences(serverHello), promise, channel,
            new HashedWheelTimer(), listener, 100L);
    }

    @Test
    public void testGetSessionForHelloMessageDefaultNs() throws Exception {
        testGetSessionForHelloMessage("netconf539/client_hello_1.1.xml");
    }

    @Test
    public void testGetSessionForHelloMessageNsPrefix() throws Exception {
        testGetSessionForHelloMessage("netconf539/client_hello_1.1_ns.xml");
    }

    private void testGetSessionForHelloMessage(final String fileName) throws Exception {
        final Document helloDocument = XmlFileLoader.xmlFileToDocument(fileName);
        negotiator.startNegotiation();
        final NetconfHelloMessage helloMessage = new NetconfHelloMessage(helloDocument);
        final AbstractNetconfSession session = negotiator.getSessionForHelloMessage(helloMessage);
        Assert.assertNotNull(session);
        Assert.assertTrue("NetconfChunkAggregator was not installed in the Netconf pipeline",
            channel.pipeline().get(NETCONF_MESSAGE_AGGREGATOR) instanceof NetconfChunkAggregator);
        Assert.assertTrue("ChunkedFramingMechanismEncoder was not installed in the Netconf pipeline",
            channel.pipeline().get(NETCONF_MESSAGE_FRAME_ENCODER) instanceof ChunkedFramingMechanismEncoder);
    }
}