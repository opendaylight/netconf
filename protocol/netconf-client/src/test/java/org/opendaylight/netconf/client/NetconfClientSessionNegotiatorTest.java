/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.common.collect.ImmutableSet;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelProgressivePromise;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timer;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.Promise;
import java.io.InputStream;
import java.util.Optional;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.api.messages.HelloMessage;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.nettyutil.handler.ChunkedFramingMechanismEncoder;
import org.opendaylight.netconf.nettyutil.handler.NetconfEXIToMessageDecoder;
import org.opendaylight.netconf.nettyutil.handler.NetconfXMLToHelloMessageDecoder;
import org.opendaylight.netconf.nettyutil.handler.NetconfXMLToMessageDecoder;
import org.opendaylight.netconf.nettyutil.handler.exi.EXIParameters;
import org.opendaylight.netconf.nettyutil.handler.exi.NetconfStartExiMessage;
import org.opendaylight.netconf.test.util.XmlFileLoader;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.w3c.dom.Document;

public class NetconfClientSessionNegotiatorTest {
    private HelloMessage helloMessage;
    private ChannelPipeline pipeline;
    private ChannelPromise future;
    private Channel channel;
    private ChannelInboundHandlerAdapter channelInboundHandlerAdapter;

    @Before
    public void setUp() {
        helloMessage = HelloMessage.createClientHello(Set.of("exi:1.0"), Optional.empty());
        pipeline = mockChannelPipeline();
        future = mockChannelFuture();
        channel = mockChannel();
        mockEventLoop();
    }

    private static ChannelHandler mockChannelHandler() {
        ChannelHandler handler = mock(ChannelHandler.class);
        return handler;
    }

    private Channel mockChannel() {
        Channel ret = mock(Channel.class);
        ChannelHandler channelHandler = mockChannelHandler();
        doReturn("").when(ret).toString();
        doReturn(future).when(ret).newPromise();
        doReturn(future).when(ret).close();
        doReturn(future).when(ret).writeAndFlush(any());
        doReturn(future).when(ret).writeAndFlush(any(), any());
        doReturn(true).when(ret).isOpen();
        doReturn(pipeline).when(ret).pipeline();
        doReturn("").when(pipeline).toString();
        doReturn(pipeline).when(pipeline).remove(any(ChannelHandler.class));
        doReturn(channelHandler).when(pipeline).remove(anyString());
        return ret;
    }

    private static ChannelPromise mockChannelFuture() {
        ChannelPromise future = mock(ChannelPromise.class);
        doReturn(future).when(future).addListener(any(GenericFutureListener.class));
        return future;
    }

    private static ChannelPipeline mockChannelPipeline() {
        ChannelPipeline pipeline = mock(ChannelPipeline.class);
        ChannelHandler handler = mock(ChannelHandler.class);
        doReturn(pipeline).when(pipeline).addAfter(anyString(), anyString(), any(ChannelHandler.class));
        doReturn(null).when(pipeline).get(SslHandler.class);
        doReturn(pipeline).when(pipeline).addLast(anyString(), any(ChannelHandler.class));
        doReturn(handler).when(pipeline).replace(anyString(), anyString(), any(ChunkedFramingMechanismEncoder.class));

        NetconfXMLToHelloMessageDecoder messageDecoder = new NetconfXMLToHelloMessageDecoder();
        doReturn(messageDecoder).when(pipeline).replace(anyString(), anyString(),
            any(NetconfXMLToMessageDecoder.class));
        doReturn(pipeline).when(pipeline).replace(any(ChannelHandler.class), anyString(),
            any(NetconfClientSession.class));
        doReturn(null).when(pipeline).replace(anyString(), anyString(),
            any(MessageToByteEncoder.class));
        doReturn(null).when(pipeline).replace(anyString(), anyString(),
            any(NetconfEXIToMessageDecoder.class));
        return pipeline;
    }

    private void mockEventLoop() {
        final EventLoop eventLoop = mock(EventLoop.class);
        doReturn(eventLoop).when(channel).eventLoop();
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(eventLoop).execute(any(Runnable.class));
    }

    private NetconfClientSessionNegotiator createNetconfClientSessionNegotiator(
            final Promise<NetconfClientSession> promise,
            final NetconfStartExiMessage startExi) {
        ChannelProgressivePromise progressivePromise = mock(ChannelProgressivePromise.class);
        doReturn(progressivePromise).when(promise).setFailure(any(Throwable.class));

        long timeout = 10L;
        NetconfClientSessionListener sessionListener = mock(NetconfClientSessionListener.class);
        Timer timer = new HashedWheelTimer();
        return new NetconfClientSessionNegotiator(helloMessage, startExi, promise, channel, timer, sessionListener,
            timeout, 16384);
    }

    private static HelloMessage createHelloMsg(final String name) throws Exception {
        final InputStream stream = NetconfClientSessionNegotiatorTest.class.getResourceAsStream(name);
        return new HelloMessage(XmlUtil.readXmlToDocument(stream));
    }

    private static Set<String> createCapabilities(final String name) throws Exception {
        return ImmutableSet.copyOf(NetconfMessageUtil.extractCapabilitiesFromHello(createHelloMsg(name).getDocument()));
    }

    @Test
    public void testNetconfClientSessionNegotiator() throws Exception {
        Promise<NetconfClientSession> promise = mock(Promise.class);
        doReturn(promise).when(promise).setSuccess(any());
        NetconfClientSessionNegotiator negotiator = createNetconfClientSessionNegotiator(promise, null);

        negotiator.channelActive(null);
        doReturn(null).when(future).cause();
        negotiator.handleMessage(HelloMessage.createServerHello(Set.of("a", "b"), new SessionIdType(Uint32.TEN)));
        verify(promise).setSuccess(any());
    }

    @Test
    public void testNegotiatorWhenChannelActiveHappenAfterHandleMessage() throws Exception {
        Promise<NetconfClientSession> promise = mock(Promise.class);
        doReturn(false).when(promise).isDone();
        doReturn(promise).when(promise).setSuccess(any());
        NetconfClientSessionNegotiator negotiator = createNetconfClientSessionNegotiator(promise, null);

        doReturn(null).when(future).cause();
        negotiator.handleMessage(HelloMessage.createServerHello(Set.of("a", "b"), new SessionIdType(Uint32.TEN)));
        negotiator.channelActive(null);
        verify(promise).setSuccess(any());
    }

    @Test
    public void testNetconfClientSessionNegotiatorWithEXI() throws Exception {
        Promise<NetconfClientSession> promise = mock(Promise.class);
        NetconfStartExiMessage exiMessage = NetconfStartExiMessage.create(EXIParameters.empty(), "msg-id");
        doReturn(promise).when(promise).setSuccess(any());
        NetconfClientSessionNegotiator negotiator = createNetconfClientSessionNegotiator(promise, exiMessage);

        doReturn(null).when(future).cause();
        negotiator.channelActive(null);

        doAnswer(invocationOnMock -> {
            channelInboundHandlerAdapter = invocationOnMock.getArgument(2);
            return null;
        }).when(pipeline).addAfter(anyString(), anyString(), any(ChannelHandler.class));

        ChannelHandlerContext handlerContext = mock(ChannelHandlerContext.class);
        doReturn(pipeline).when(handlerContext).pipeline();
        negotiator.handleMessage(HelloMessage.createServerHello(Set.of("exi:1.0"), new SessionIdType(Uint32.TEN)));
        Document expectedResult = XmlFileLoader.xmlFileToDocument("netconfMessages/rpc-reply_ok.xml");
        channelInboundHandlerAdapter.channelRead(handlerContext, new NetconfMessage(expectedResult));

        verify(promise).setSuccess(any());

        // two calls for exiMessage, 2 for hello message
        verify(pipeline, times(4)).replace(anyString(), anyString(), any(ChannelHandler.class));
    }

    @Test
    public void testNetconfClientSessionNegotiatorGetCached() throws Exception {
        Promise<NetconfClientSession> promise = mock(Promise.class);
        doReturn(promise).when(promise).setSuccess(any());
        NetconfClientSessionListener sessionListener = mock(NetconfClientSessionListener.class);
        NetconfClientSessionNegotiator negotiator = createNetconfClientSessionNegotiator(promise, null);

        Set<String> set = createCapabilities("/helloMessage3.xml");

        final Set<String> cachedS1 = (Set<String>) negotiator.getSession(sessionListener, channel,
                createHelloMsg("/helloMessage1.xml")).getServerCapabilities();

        //helloMessage2 and helloMessage3 are the same with different order
        final Set<String> cachedS2 = (Set<String>) negotiator.getSession(sessionListener, channel,
                createHelloMsg("/helloMessage2.xml")).getServerCapabilities();
        final Set<String> cachedS3 = (Set<String>) negotiator.getSession(sessionListener, channel,
                createHelloMsg("/helloMessage3.xml")).getServerCapabilities();

        assertEquals(cachedS3, set);
        assertNotEquals(cachedS1, set);
        assertEquals(cachedS2, set);
        assertEquals(cachedS3, cachedS2);
        assertNotEquals(cachedS3, cachedS1);
        assertNotEquals(cachedS2, cachedS1);
        assertTrue(cachedS2 == cachedS3);
    }
}
