/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.opendaylight.netconf.nettyutil.AbstractChannelInitializer.NETCONF_MESSAGE_AGGREGATOR;
import static org.opendaylight.netconf.nettyutil.AbstractChannelInitializer.NETCONF_MESSAGE_FRAME_ENCODER;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.Timeout;
import io.netty.util.Timer;
import io.netty.util.TimerTask;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.netconf.api.CapabilityURN;
import org.opendaylight.netconf.api.NetconfSessionListener;
import org.opendaylight.netconf.api.messages.FramingMechanism;
import org.opendaylight.netconf.api.messages.HelloMessage;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.nettyutil.handler.ChunkedFramingMechanismEncoder;
import org.opendaylight.netconf.nettyutil.handler.EOMFramingMechanismEncoder;
import org.opendaylight.netconf.nettyutil.handler.FramingMechanismHandlerFactory;
import org.opendaylight.netconf.nettyutil.handler.NetconfChunkAggregator;
import org.opendaylight.netconf.nettyutil.handler.NetconfEOMAggregator;
import org.opendaylight.netconf.nettyutil.handler.NetconfXMLToHelloMessageDecoder;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class AbstractNetconfSessionNegotiatorTest {
    @Mock
    private NetconfSessionListener<TestingNetconfSession> listener;
    @Mock
    private Promise<TestingNetconfSession> promise;
    @Mock
    private SslHandler sslHandler;
    @Mock
    private Timer timer;
    @Mock
    private Timeout timeout;
    private EmbeddedChannel channel;
    private TestSessionNegotiator negotiator;
    private HelloMessage hello;
    private HelloMessage helloBase11;
    private NetconfXMLToHelloMessageDecoder xmlToHello;

    @Before
    public void setUp() {
        channel = new EmbeddedChannel();
        xmlToHello = new NetconfXMLToHelloMessageDecoder();
        channel.pipeline().addLast(AbstractChannelInitializer.NETCONF_MESSAGE_ENCODER,
                new ChannelInboundHandlerAdapter());
        channel.pipeline().addLast(AbstractChannelInitializer.NETCONF_MESSAGE_DECODER, xmlToHello);
        channel.pipeline().addLast(NETCONF_MESSAGE_FRAME_ENCODER,
                FramingMechanismHandlerFactory.createHandler(FramingMechanism.EOM));
        channel.pipeline().addLast(NETCONF_MESSAGE_AGGREGATOR, new NetconfEOMAggregator());
        hello = HelloMessage.createClientHello(Set.of(), Optional.empty());
        helloBase11 = HelloMessage.createClientHello(Set.of(CapabilityURN.BASE_1_1), Optional.empty());
        doReturn(promise).when(promise).setFailure(any());
        negotiator = new TestSessionNegotiator(helloBase11, promise, channel, timer, listener, 100L);
    }

    @Test
    public void testStartNegotiation() {
        enableTimerTask();
        negotiator.startNegotiation();
        assertEquals(helloBase11, channel.readOutbound());
    }

    @Test
    public void testStartNegotiationSsl() throws Exception {
        doReturn(true).when(sslHandler).isSharable();
        doNothing().when(sslHandler).handlerAdded(any());
        doNothing().when(sslHandler).write(any(), any(), any());
        final Future<EmbeddedChannel> handshakeFuture = channel.eventLoop().newSucceededFuture(channel);
        doReturn(handshakeFuture).when(sslHandler).handshakeFuture();
        doNothing().when(sslHandler).flush(any());
        channel.pipeline().addLast(sslHandler);

        enableTimerTask();
        negotiator.startNegotiation();
        verify(sslHandler).write(any(), eq(helloBase11), any());
    }

    @Test
    public void testStartNegotiationNotEstablished() throws Exception {
        final ChannelOutboundHandler closedDetector = spy(new CloseDetector());
        channel.pipeline().addLast("closedDetector", closedDetector);
        doReturn(false).when(promise).isDone();
        doReturn(false).when(promise).isCancelled();

        final ArgumentCaptor<TimerTask> captor = ArgumentCaptor.forClass(TimerTask.class);
        doReturn(timeout).when(timer).newTimeout(captor.capture(), eq(100L), eq(TimeUnit.MILLISECONDS));
        negotiator.startNegotiation();

        captor.getValue().run(timeout);
        channel.runPendingTasks();
        verify(closedDetector).close(any(), any());
    }

    @Test
    public void testGetSessionForHelloMessage() throws Exception {
        enableTimerTask();
        negotiator.startNegotiation();
        final TestingNetconfSession session = negotiator.getSessionForHelloMessage(hello);
        assertNotNull(session);
        assertThat(channel.pipeline().get(NETCONF_MESSAGE_AGGREGATOR), instanceOf(NetconfEOMAggregator.class));
        assertThat(channel.pipeline().get(NETCONF_MESSAGE_FRAME_ENCODER), instanceOf(EOMFramingMechanismEncoder.class));
    }

    @Test
    public void testGetSessionForHelloMessageBase11() throws Exception {
        enableTimerTask();
        negotiator.startNegotiation();
        final TestingNetconfSession session = negotiator.getSessionForHelloMessage(helloBase11);
        assertNotNull(session);
        assertThat(channel.pipeline().get(NETCONF_MESSAGE_AGGREGATOR), instanceOf(NetconfChunkAggregator.class));
        assertThat(channel.pipeline().get(NETCONF_MESSAGE_FRAME_ENCODER),
            instanceOf(ChunkedFramingMechanismEncoder.class));
    }

    @Test
    public void testReplaceHelloMessageInboundHandler() throws Exception {
        final List<Object> out = new ArrayList<>();
        final byte[] msg = "<rpc/>".getBytes();
        final ByteBuf msgBuf = Unpooled.wrappedBuffer(msg);
        final ByteBuf helloBuf = Unpooled.wrappedBuffer(XmlUtil.toString(hello.getDocument()).getBytes());

        enableTimerTask();
        negotiator.startNegotiation();

        xmlToHello.decode(null, helloBuf, out);
        xmlToHello.decode(null, msgBuf, out);
        final TestingNetconfSession session = mock(TestingNetconfSession.class);
        doNothing().when(session).handleMessage(any());
        negotiator.replaceHelloMessageInboundHandler(session);
        verify(session, times(1)).handleMessage(any());
    }

    @Test
    public void testNegotiationFail() {
        enableTimerTask();
        doReturn(true).when(timeout).cancel();
        negotiator.startNegotiation();
        final RuntimeException cause = new RuntimeException("failure cause");
        channel.pipeline().fireExceptionCaught(cause);
        verify(promise).setFailure(cause);
    }

    private void enableTimerTask() {
        doReturn(timeout).when(timer).newTimeout(any(), eq(100L), eq(TimeUnit.MILLISECONDS));
    }

    private static class CloseDetector extends ChannelOutboundHandlerAdapter {
        @Override
        public void close(final ChannelHandlerContext ctx, final ChannelPromise promise) {
            // Override needed so @Skip from superclass is not effective
        }
    }
}
