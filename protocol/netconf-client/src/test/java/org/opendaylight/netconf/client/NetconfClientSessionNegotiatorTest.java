/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
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
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.Promise;
import java.io.InputStream;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opendaylight.netconf.api.messages.HelloMessage;
import org.opendaylight.netconf.api.messages.NetconfMessage;
import org.opendaylight.netconf.api.messages.RpcMessage;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.codec.MessageDecoder;
import org.opendaylight.netconf.codec.MessageEncoder;
import org.opendaylight.netconf.codec.MessageWriter;
import org.opendaylight.netconf.codec.XMLMessageDecoder;
import org.opendaylight.netconf.common.di.DefaultNetconfTimer;
import org.opendaylight.netconf.nettyutil.handler.HelloXMLMessageDecoder;
import org.opendaylight.netconf.nettyutil.handler.NetconfEXICodec;
import org.opendaylight.netconf.nettyutil.handler.exi.EXIParameters;
import org.opendaylight.netconf.nettyutil.handler.exi.NetconfStartExiMessageProvider;
import org.opendaylight.netconf.shaded.exificient.core.exceptions.EXIException;
import org.opendaylight.netconf.test.util.XmlFileLoader;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.w3c.dom.Document;

class NetconfClientSessionNegotiatorTest {
    private HelloMessage helloMessage;
    private ChannelPipeline pipeline;
    private ChannelPromise future;
    private Channel channel;
    private ChannelInboundHandlerAdapter channelInboundHandlerAdapter;

    @BeforeEach
    void setUp() {
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
        doReturn(pipeline).when(pipeline).addAfter(anyString(), anyString(), any(ChannelHandler.class));
        doReturn(null).when(pipeline).get(SslHandler.class);
        doReturn(pipeline).when(pipeline).addLast(anyString(), any(ChannelHandler.class));

        HelloXMLMessageDecoder messageDecoder = new HelloXMLMessageDecoder();
        doReturn(messageDecoder).when(pipeline).replace(anyString(), anyString(), any(XMLMessageDecoder.class));
        doReturn(pipeline).when(pipeline).replace(any(ChannelHandler.class), anyString(),
            any(NetconfClientSession.class));
        doReturn(null).when(pipeline).replace(anyString(), anyString(), any(MessageToByteEncoder.class));

        final var encoder = new MessageEncoder(mock(MessageWriter.class));
        doReturn(encoder).when(pipeline).get(MessageEncoder.class);

        Class<? extends MessageDecoder> exiClass;
        try {
            exiClass = NetconfEXICodec.forParameters(EXIParameters.empty()).newMessageDecoder().getClass();
        } catch (EXIException e) {
            throw new AssertionError(e);
        }

        doReturn(null).when(pipeline).replace(any(Class.class), anyString(), any(exiClass));
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
            final RpcMessage startExi) {
        ChannelProgressivePromise progressivePromise = mock(ChannelProgressivePromise.class);
        doReturn(progressivePromise).when(promise).setFailure(any(Throwable.class));

        long timeout = 10L;
        NetconfClientSessionListener sessionListener = mock(NetconfClientSessionListener.class);
        var timer = new DefaultNetconfTimer();
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
    void testNetconfClientSessionNegotiator() throws Exception {
        Promise<NetconfClientSession> promise = mock(Promise.class);
        doReturn(promise).when(promise).setSuccess(any());
        NetconfClientSessionNegotiator negotiator = createNetconfClientSessionNegotiator(promise, null);

        negotiator.channelActive(null);
        doReturn(null).when(future).cause();
        negotiator.handleMessage(HelloMessage.createServerHello(Set.of("a", "b"), new SessionIdType(Uint32.TEN)));
        verify(promise).setSuccess(any());
    }

    @Test
    void testNegotiatorWhenChannelActiveHappenAfterHandleMessage() throws Exception {
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
    void testNetconfClientSessionNegotiatorWithEXI() throws Exception {
        Promise<NetconfClientSession> promise = mock(Promise.class);
        RpcMessage exiMessage = NetconfStartExiMessageProvider.create(EXIParameters.empty(), "msg-id");
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

        // for hello message
        verify(pipeline).replace(anyString(), anyString(), any(MessageDecoder.class));
        // for exiMessage
        verify(pipeline).replace(any(Class.class), anyString(), any(MessageDecoder.class));
    }

    @Test
    void testNetconfClientSessionNegotiatorGetCached() throws Exception {
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
