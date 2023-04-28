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
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.MessageToByteEncoder;
import java.io.EOFException;
import java.util.Optional;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.api.NetconfSessionListener;
import org.opendaylight.netconf.api.NetconfTerminationReason;
import org.opendaylight.netconf.api.messages.HelloMessage;
import org.opendaylight.netconf.nettyutil.handler.exi.EXIParameters;
import org.opendaylight.netconf.nettyutil.handler.exi.NetconfStartExiMessage;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;
import org.opendaylight.yangtools.yang.common.Uint32;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class AbstractNetconfSessionTest {
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

    private HelloMessage clientHello;

    @Before
    public void setUp() throws Exception {
        doNothing().when(listener).onMessage(any(TestingNetconfSession.class), any(NetconfMessage.class));
        doNothing().when(listener).onSessionUp(any(TestingNetconfSession.class));
        doNothing().when(listener).onSessionDown(any(TestingNetconfSession.class), any(Exception.class));
        doNothing().when(listener).onSessionTerminated(any(TestingNetconfSession.class),
                any(NetconfTerminationReason.class));

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

        clientHello = HelloMessage.createClientHello(Set.of(), Optional.empty());
    }

    @Test
    public void testHandleMessage() throws Exception {
        final TestingNetconfSession testingNetconfSession = new TestingNetconfSession(listener, channel, SESSION_ID);
        testingNetconfSession.handleMessage(clientHello);
        verify(listener).onMessage(testingNetconfSession, clientHello);
    }

    @Test
    public void testSessionUp() throws Exception {
        final TestingNetconfSession testingNetconfSession = new TestingNetconfSession(listener, channel, SESSION_ID);
        testingNetconfSession.sessionUp();
        verify(listener).onSessionUp(testingNetconfSession);
        assertEquals(SESSION_ID, testingNetconfSession.sessionId());
    }

    @Test
    public void testClose() throws Exception {
        final TestingNetconfSession testingNetconfSession = new TestingNetconfSession(listener, channel, SESSION_ID);
        testingNetconfSession.sessionUp();
        testingNetconfSession.close();
        verify(channel).close();
        verify(listener).onSessionTerminated(any(TestingNetconfSession.class), any(NetconfTerminationReason.class));
    }

    @Test
    public void testReplaceHandlers() throws Exception {
        final TestingNetconfSession testingNetconfSession = new TestingNetconfSession(listener, channel, SESSION_ID);
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
    public void testStartExi() throws Exception {
        TestingNetconfSession testingNetconfSession = new TestingNetconfSession(listener, channel, SESSION_ID);
        testingNetconfSession = spy(testingNetconfSession);

        testingNetconfSession.startExiCommunication(NetconfStartExiMessage.create(EXIParameters.empty(), "4"));
        verify(testingNetconfSession).addExiHandlers(any(ByteToMessageDecoder.class), any(MessageToByteEncoder.class));
    }

    @Test
    public void testEndOfInput() throws Exception {
        final TestingNetconfSession testingNetconfSession = new TestingNetconfSession(listener, channel, SESSION_ID);
        testingNetconfSession.endOfInput();
        verifyNoMoreInteractions(listener);
        testingNetconfSession.sessionUp();
        testingNetconfSession.endOfInput();
        verify(listener).onSessionDown(any(TestingNetconfSession.class), any(EOFException.class));
    }

    @Test
    public void testSendMessage() throws Exception {
        final TestingNetconfSession testingNetconfSession = new TestingNetconfSession(listener, channel, SESSION_ID);
        final HelloMessage hello = HelloMessage.createClientHello(Set.of(), Optional.empty());
        testingNetconfSession.sendMessage(hello);
        verify(channel).writeAndFlush(hello, writeFuture);
    }
}
