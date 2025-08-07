/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import java.io.EOFException;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.netconf.api.NetconfSessionListener;
import org.opendaylight.netconf.api.NetconfTerminationReason;
import org.opendaylight.netconf.api.messages.HelloMessage;
import org.opendaylight.netconf.api.messages.NetconfMessage;
import org.opendaylight.netconf.codec.MessageDecoder;
import org.opendaylight.netconf.codec.MessageEncoder;
import org.opendaylight.netconf.codec.MessageWriter;
import org.opendaylight.netconf.nettyutil.handler.exi.EXIParameters;
import org.opendaylight.netconf.nettyutil.handler.exi.NetconfStartExiMessageProvider;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;
import org.opendaylight.yangtools.yang.common.Uint32;

@ExtendWith(MockitoExtension.class)
class AbstractNetconfSessionTest {
    private static final SessionIdType SESSION_ID = new SessionIdType(Uint32.ONE);

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
    private ChannelHandlerContext handlerContext;

    private HelloMessage clientHello;

    @BeforeEach
    void setUp() {
        clientHello = HelloMessage.createClientHello(Set.of(), Optional.empty());
    }

    @Test
    void testHandleMessage() {
        doNothing().when(listener).onMessage(any(TestingNetconfSession.class), any(NetconfMessage.class));

        final TestingNetconfSession testingNetconfSession = new TestingNetconfSession(listener, channel, SESSION_ID);
        testingNetconfSession.handleMessage(clientHello);
        verify(listener).onMessage(testingNetconfSession, clientHello);
    }

    @Test
    void testSessionUp() {
        doNothing().when(listener).onSessionUp(any(TestingNetconfSession.class));

        final TestingNetconfSession testingNetconfSession = new TestingNetconfSession(listener, channel, SESSION_ID);
        testingNetconfSession.sessionUp();
        verify(listener).onSessionUp(testingNetconfSession);
        assertEquals(SESSION_ID, testingNetconfSession.sessionId());
    }

    @Test
    void testClose() {
        doReturn(mock(ChannelFuture.class)).when(channel).close();
        doNothing().when(listener).onSessionUp(any(TestingNetconfSession.class));
        doNothing().when(listener).onSessionTerminated(any(TestingNetconfSession.class),
            any(NetconfTerminationReason.class));

        final TestingNetconfSession testingNetconfSession = new TestingNetconfSession(listener, channel, SESSION_ID);
        testingNetconfSession.sessionUp();
        testingNetconfSession.close();
        verify(channel).close();
        verify(listener).onSessionTerminated(any(TestingNetconfSession.class), any(NetconfTerminationReason.class));
    }

    @Test
    void testReplaceHandlers() throws Exception {
        doReturn(writeFuture).when(channel).newPromise();
        doReturn(pipeline).when(channel).pipeline();
        doReturn(null).when(pipeline).replace(eq(MessageDecoder.class), anyString(), any());
        doReturn(eventLoop).when(channel).eventLoop();
        doAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return null;
        }).when(eventLoop).execute(any(Runnable.class));

        final var testingNetconfSession = new TestingNetconfSession(listener, channel, SESSION_ID);

        final var mockDecoder = mock(MessageDecoder.class);
        testingNetconfSession.replaceMessageDecoder(mockDecoder);
        verify(pipeline).replace(MessageDecoder.class, MessageDecoder.HANDLER_NAME, mockDecoder);

        final var encoder = new MessageEncoder(mock(MessageWriter.class));
        doReturn(encoder).when(pipeline).get(MessageEncoder.class);

        final var first = mock(MessageWriter.class);
        testingNetconfSession.setMessageWriter(first);
        assertSame(first, encoder.writer());
        assertNull(encoder.nextWriter());
        verifyNoMoreInteractions(pipeline);

        final var second = mock(MessageWriter.class);
        testingNetconfSession.setMessageWriterAfterNextMessage(second);
        assertSame(first, encoder.writer());
        assertSame(second, encoder.nextWriter());
        verifyNoMoreInteractions(pipeline);

        doReturn(ByteBufAllocator.DEFAULT).when(handlerContext).alloc();
        doNothing().when(first).writeMessage(any(), any());
        doReturn(writeFuture).when(handlerContext).write(any(), any());

        doAnswer(invocation -> {
            final NetconfMessage message = invocation.getArgument(0);
            final ChannelPromise promise = invocation.getArgument(1);
            encoder.write(handlerContext, message, promise);
            return writeFuture;
        }).when(channel).writeAndFlush(any(NetconfMessage.class), any(ChannelPromise.class));

        testingNetconfSession.sendMessage(clientHello);
        assertSame(second, encoder.writer());
        assertNull(encoder.nextWriter());
    }

    @Test
    void testStartExi() {
        TestingNetconfSession testingNetconfSession = new TestingNetconfSession(listener, channel, SESSION_ID);
        testingNetconfSession = spy(testingNetconfSession);

        testingNetconfSession.startExiCommunication(NetconfStartExiMessageProvider.create(EXIParameters.empty(), "4"));
        verify(testingNetconfSession).addExiHandlers(any(MessageDecoder.class), any(MessageWriter.class));
    }

    @Test
    void testEndOfInput() {
        doNothing().when(listener).onSessionUp(any(TestingNetconfSession.class));
        doNothing().when(listener).onSessionDown(any(TestingNetconfSession.class), any(Exception.class));

        final TestingNetconfSession testingNetconfSession = new TestingNetconfSession(listener, channel, SESSION_ID);
        testingNetconfSession.endOfInput();
        verifyNoMoreInteractions(listener);
        testingNetconfSession.sessionUp();
        testingNetconfSession.endOfInput();
        verify(listener).onSessionDown(any(TestingNetconfSession.class), any(EOFException.class));
    }

    @Test
    void testSendMessage() {
        doReturn(writeFuture).when(channel).newPromise();
        doReturn(writeFuture).when(channel).writeAndFlush(any(NetconfMessage.class), any(ChannelPromise.class));
        doReturn(eventLoop).when(channel).eventLoop();
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(eventLoop).execute(any(Runnable.class));

        final TestingNetconfSession testingNetconfSession = new TestingNetconfSession(listener, channel, SESSION_ID);
        final HelloMessage hello = HelloMessage.createClientHello(Set.of(), Optional.empty());
        testingNetconfSession.sendMessage(hello);
        verify(channel).writeAndFlush(hello, writeFuture);
    }
}
