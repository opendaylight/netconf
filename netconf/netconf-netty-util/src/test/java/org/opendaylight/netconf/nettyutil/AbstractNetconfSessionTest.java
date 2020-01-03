/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.MessageToByteEncoder;
import java.util.Collections;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.netconf.api.NetconfChunkException;
import org.opendaylight.netconf.api.NetconfDocumentedException;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.api.NetconfSessionListener;
import org.opendaylight.netconf.api.NetconfTerminationReason;
import org.opendaylight.netconf.api.messages.NetconfHelloMessage;
import org.opendaylight.netconf.nettyutil.handler.exi.EXIParameters;
import org.opendaylight.netconf.nettyutil.handler.exi.NetconfStartExiMessage;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class AbstractNetconfSessionTest {

    @Mock
    private NetconfSessionListener<TestingNetconfSession> listener;
    @Mock
    private Channel channel;
    @Mock
    private ChannelPipeline pipeline;
    @Mock
    private EventLoop eventLoop;
    @Mock
    private ChannelPromise writeFuture;
    @Mock
    private ChannelHandlerContext channelContext;

    private NetconfHelloMessage clientHello;

    @Before
    public void setUp() throws Exception {
        doNothing().when(listener).onMessage(any(TestingNetconfSession.class), any(NetconfMessage.class));
        doNothing().when(listener).onSessionUp(any(TestingNetconfSession.class));
        doNothing().when(listener).onSessionDown(any(TestingNetconfSession.class), any(Exception.class));
        doNothing().when(listener).onSessionTerminated(any(TestingNetconfSession.class),
                any(NetconfTerminationReason.class));
        doNothing().when(listener).processMalformedRpc(any(String.class), any(NetconfDocumentedException.class));

        doReturn(writeFuture).when(channel).newPromise();
        doReturn(writeFuture).when(channel).writeAndFlush(any(NetconfMessage.class), any(ChannelPromise.class));
        doReturn(pipeline).when(channel).pipeline();
        doReturn(mock(ChannelFuture.class)).when(channel).close();

        doReturn(null).when(pipeline).replace(anyString(), anyString(), any(ChannelHandler.class));

        doReturn(eventLoop).when(channel).eventLoop();
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(eventLoop).execute(any(Runnable.class));
        doReturn(channelContext).when(channelContext).fireExceptionCaught(any(Throwable.class));

        clientHello = NetconfHelloMessage.createClientHello(Collections.emptySet(), Optional.empty());
    }

    @Test
    public void testHandleMessage() {
        final TestingNetconfSession testingNetconfSession = new TestingNetconfSession(listener, channel, 1L);
        testingNetconfSession.handleMessage(clientHello);
        verify(listener).onMessage(testingNetconfSession, clientHello);
    }

    @Test
    public void testSessionUp() {
        final TestingNetconfSession testingNetconfSession = new TestingNetconfSession(listener, channel, 1L);
        testingNetconfSession.sessionUp();
        verify(listener).onSessionUp(testingNetconfSession);
        assertEquals(1L, testingNetconfSession.getSessionId());
    }

    @Test
    public void testClose() {
        final TestingNetconfSession testingNetconfSession = new TestingNetconfSession(listener, channel, 1L);
        testingNetconfSession.sessionUp();
        testingNetconfSession.close();
        verify(channel).close();
        verify(listener).onSessionTerminated(any(TestingNetconfSession.class), any(NetconfTerminationReason.class));
    }

    @Test
    public void testReplaceHandlers() {
        final TestingNetconfSession testingNetconfSession = new TestingNetconfSession(listener, channel, 1L);
        final ChannelHandler mock = mock(ChannelHandler.class);

        testingNetconfSession.replaceMessageDecoder(mock);
        verify(pipeline).replace(AbstractChannelInitializer.NETCONF_MESSAGE_DECODER,
                AbstractChannelInitializer.NETCONF_MESSAGE_DECODER, mock);
        testingNetconfSession.replaceMessageEncoder(mock);
        verify(pipeline).replace(AbstractChannelInitializer.NETCONF_MESSAGE_ENCODER,
                AbstractChannelInitializer.NETCONF_MESSAGE_ENCODER, mock);
        testingNetconfSession.replaceMessageEncoderAfterNextMessage(mock);
        verifyNoMoreInteractions(pipeline);

        testingNetconfSession.sendMessage(clientHello);
        verify(pipeline, times(2)).replace(AbstractChannelInitializer.NETCONF_MESSAGE_ENCODER,
                AbstractChannelInitializer.NETCONF_MESSAGE_ENCODER, mock);
    }

    @Test
    public void testStartExi() {
        TestingNetconfSession testingNetconfSession = new TestingNetconfSession(listener, channel, 1L);
        testingNetconfSession = spy(testingNetconfSession);

        testingNetconfSession.startExiCommunication(NetconfStartExiMessage.create(EXIParameters.empty(), "4"));
        verify(testingNetconfSession).addExiHandlers(any(ByteToMessageDecoder.class), any(MessageToByteEncoder.class));
    }

    @Test
    public void testEndOfInput() {
        final TestingNetconfSession testingNetconfSession = new TestingNetconfSession(listener, channel, 1L);
        testingNetconfSession.endOfInput();
        verifyNoMoreInteractions(listener);
        testingNetconfSession.sessionUp();
        testingNetconfSession.endOfInput();
        verify(listener).onSessionDown(any(TestingNetconfSession.class), any(Exception.class));
    }

    @Test
    public void testSendMessage() {
        final TestingNetconfSession testingNetconfSession = new TestingNetconfSession(listener, channel, 1L);
        final NetconfHelloMessage hello = NetconfHelloMessage.createClientHello(Collections.emptySet(),
            Optional.empty());
        testingNetconfSession.sendMessage(hello);
        verify(channel).writeAndFlush(hello, writeFuture);
    }

    @Test
    public void testChunkExceptionWithMessageIdCaught() {
        final String malformedNetconfRpc = "<?xml version=\"1.0\"?>\n"
                + "<rpc message-id=\"10\" a=\"64\""
                + " xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n"
                + "<rpc-error>transport throttling error</rpc-error>\n"
                + "    <get/>\n"
                + "</rpc>";
        final NetconfChunkException chunkException = NetconfChunkException.create(
                malformedNetconfRpc.getBytes(), "Malformed chunk header encountered (byte 0)");
        final DecoderException decoderException = new DecoderException(chunkException);

        final TestingNetconfSession testingNetconfSession = new TestingNetconfSession(listener, channel, 1L);
        testingNetconfSession.exceptionCaught(channelContext, decoderException);

        verify(listener).processMalformedRpc("10", chunkException);
        verify(channelContext, never()).fireExceptionCaught(any(Throwable.class));
    }

    @Test
    public void testChunkExceptionWithoutMessageIdCaught() {
        final String malformedNetconfRpc = "a=\"64\" xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\">\n"
                + "<rpc-error>transport throttling error</rpc-error>\n"
                + "    <get/>\n"
                + "</rpc>";
        final NetconfChunkException chunkException = NetconfChunkException.create(
                malformedNetconfRpc.getBytes(), "Malformed chunk header encountered (byte 0)");
        final DecoderException decoderException = new DecoderException(chunkException);
        final TestingNetconfSession testingNetconfSession = new TestingNetconfSession(listener, channel, 1L);
        testingNetconfSession.exceptionCaught(channelContext, decoderException);

        verify(listener, never()).processMalformedRpc(any(String.class), any(NetconfDocumentedException.class));
        verify(channelContext, never()).fireExceptionCaught(any(Throwable.class));
    }

    @Test
    public void testUnknownExceptionCaptured() {
        final Exception exception = new Exception("unknown exception");
        final TestingNetconfSession testingNetconfSession = new TestingNetconfSession(listener, channel, 1L);
        testingNetconfSession.exceptionCaught(channelContext, exception);

        verify(listener, never()).processMalformedRpc(any(String.class), any(NetconfDocumentedException.class));
        verify(channelContext).fireExceptionCaught(exception);
    }
}