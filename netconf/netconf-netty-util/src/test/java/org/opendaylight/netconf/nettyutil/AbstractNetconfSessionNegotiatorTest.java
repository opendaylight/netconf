/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.nettyutil;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.opendaylight.netconf.nettyutil.AbstractChannelInitializer.NETCONF_MESSAGE_AGGREGATOR;
import static org.opendaylight.netconf.nettyutil.AbstractChannelInitializer.NETCONF_MESSAGE_FRAME_ENCODER;

import com.google.common.base.Optional;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timer;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.opendaylight.netconf.api.NetconfDocumentedException;
import org.opendaylight.netconf.api.NetconfSessionListener;
import org.opendaylight.netconf.api.NetconfSessionPreferences;
import org.opendaylight.netconf.api.messages.NetconfHelloMessage;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.nettyutil.handler.ChunkedFramingMechanismEncoder;
import org.opendaylight.netconf.nettyutil.handler.EOMFramingMechanismEncoder;
import org.opendaylight.netconf.nettyutil.handler.FramingMechanismHandlerFactory;
import org.opendaylight.netconf.nettyutil.handler.NetconfChunkAggregator;
import org.opendaylight.netconf.nettyutil.handler.NetconfEOMAggregator;
import org.opendaylight.netconf.nettyutil.handler.NetconfXMLToHelloMessageDecoder;
import org.opendaylight.netconf.util.messages.FramingMechanism;

public class AbstractNetconfSessionNegotiatorTest {

    @Mock
    private NetconfSessionListener<TestingNetconfSession> listener;
    @Mock
    private Promise<TestingNetconfSession> promise;
    @Mock
    private SslHandler sslHandler;
    private EmbeddedChannel channel;
    private AbstractNetconfSessionNegotiator negotiator;
    private NetconfHelloMessage hello;
    private NetconfHelloMessage helloBase11;
    private NetconfXMLToHelloMessageDecoder xmlToHello;
    private NetconfSessionPreferences prefs;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        channel = new EmbeddedChannel();
        xmlToHello = new NetconfXMLToHelloMessageDecoder();
        channel.pipeline().addLast(AbstractChannelInitializer.NETCONF_MESSAGE_ENCODER,
                new ChannelInboundHandlerAdapter());
        channel.pipeline().addLast(AbstractChannelInitializer.NETCONF_MESSAGE_DECODER, xmlToHello);
        channel.pipeline().addLast(NETCONF_MESSAGE_FRAME_ENCODER,
                FramingMechanismHandlerFactory.createHandler(FramingMechanism.EOM));
        channel.pipeline().addLast(NETCONF_MESSAGE_AGGREGATOR, new NetconfEOMAggregator());
        hello = NetconfHelloMessage.createClientHello(Collections.emptySet(), Optional.absent());
        helloBase11 = NetconfHelloMessage.createClientHello(Collections
                .singleton(XmlNetconfConstants.URN_IETF_PARAMS_NETCONF_BASE_1_1), Optional.absent());
        prefs = new NetconfSessionPreferences(helloBase11);
        doReturn(promise).when(promise).setFailure(any());
        doReturn(promise).when(promise).setSuccess(any());
        negotiator = new TestSessionNegotiator(prefs, promise, channel, new HashedWheelTimer(), listener, 100L);
    }

    @Test
    public void testStartNegotiation() throws Exception {
        negotiator.startNegotiation();
        Assert.assertEquals(helloBase11, channel.readOutbound());
    }

    @Test
    public void testStartNegotiationSsl() throws Exception {
        doReturn(true).when(sslHandler).isSharable();
        doNothing().when(sslHandler).handlerAdded(any());
        doNothing().when(sslHandler).write(any(), any(), any());
        final Future<EmbeddedChannel> handshakeFuture = channel.eventLoop().newSucceededFuture(channel);
        doReturn(handshakeFuture).when(sslHandler).handshakeFuture();
        channel.pipeline().addLast(sslHandler);
        negotiator.startNegotiation();
        verify(sslHandler, timeout(1000)).write(any(), eq(helloBase11), any());

    }

    @Test
    public void testStartNegotiationNotEstablished() throws Exception {
        final ChannelOutboundHandler closedDetector = Mockito.spy(new ChannelOutboundHandlerAdapter());
        channel.pipeline().addLast("closedDetector", closedDetector);
        doReturn(false).when(promise).isDone();
        doReturn(false).when(promise).isCancelled();
        negotiator.startNegotiation();
        verify(closedDetector, timeout(2000)).close(any(), any());
    }

    @Test
    public void testGetSessionPreferences() throws Exception {
        Assert.assertEquals(prefs, negotiator.getSessionPreferences());
    }

    @Test
    public void testGetSessionForHelloMessage() throws Exception {
        negotiator.startNegotiation();
        final AbstractNetconfSession session = negotiator.getSessionForHelloMessage(hello);
        Assert.assertNotNull(session);
        Assert.assertTrue(channel.pipeline().get(NETCONF_MESSAGE_AGGREGATOR) instanceof NetconfEOMAggregator);
        Assert.assertTrue(channel.pipeline().get(NETCONF_MESSAGE_FRAME_ENCODER) instanceof EOMFramingMechanismEncoder);
    }

    @Test
    public void testGetSessionForHelloMessageBase11() throws Exception {
        negotiator.startNegotiation();
        final AbstractNetconfSession session = negotiator.getSessionForHelloMessage(helloBase11);
        Assert.assertNotNull(session);
        Assert.assertTrue(channel.pipeline().get(NETCONF_MESSAGE_AGGREGATOR) instanceof NetconfChunkAggregator);
        Assert.assertTrue(channel.pipeline().get(NETCONF_MESSAGE_FRAME_ENCODER)
                instanceof ChunkedFramingMechanismEncoder);
    }

    @Test
    public void testReplaceHelloMessageInboundHandler() throws Exception {
        final List<Object> out = new ArrayList<>();
        final byte[] msg = "<rpc/>".getBytes();
        final ByteBuf msgBuf = Unpooled.wrappedBuffer(msg);
        final ByteBuf helloBuf = Unpooled.wrappedBuffer(XmlUtil.toString(hello.getDocument()).getBytes());
        negotiator.startNegotiation();
        xmlToHello.decode(null, helloBuf, out);
        xmlToHello.decode(null, msgBuf, out);
        final AbstractNetconfSession session = mock(AbstractNetconfSession.class);
        doNothing().when(session).handleMessage(any());
        negotiator.replaceHelloMessageInboundHandler(session);
        verify(session, times(1)).handleMessage(any());
    }

    @Test
    public void testNegotiationFail() throws Exception {
        negotiator.startNegotiation();
        final RuntimeException cause = new RuntimeException("failure cause");
        channel.pipeline().fireExceptionCaught(cause);
        verify(promise).setFailure(cause);
    }

    private static class TestSessionNegotiator extends
            AbstractNetconfSessionNegotiator<NetconfSessionPreferences,
                    TestingNetconfSession, NetconfSessionListener<TestingNetconfSession>> {


        TestSessionNegotiator(final NetconfSessionPreferences sessionPreferences,
                              final Promise<TestingNetconfSession> promise, final Channel channel,
                              final Timer timer,
                              final NetconfSessionListener<TestingNetconfSession> sessionListener,
                              final long connectionTimeoutMillis) {
            super(sessionPreferences, promise, channel, timer, sessionListener, connectionTimeoutMillis);
        }

        @Override
        protected TestingNetconfSession getSession(final NetconfSessionListener sessionListener, final Channel channel,
                                               final NetconfHelloMessage message) throws NetconfDocumentedException {
            return new TestingNetconfSession(sessionListener, channel, 0L);
        }

        @Override
        protected void handleMessage(final NetconfHelloMessage netconfHelloMessage) throws Exception {

        }
    }


}
