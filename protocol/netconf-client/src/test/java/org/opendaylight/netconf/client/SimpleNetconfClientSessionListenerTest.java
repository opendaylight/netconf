/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.netty.channel.Channel;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.api.messages.HelloMessage;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;
import org.opendaylight.yangtools.yang.common.Uint32;

public class SimpleNetconfClientSessionListenerTest {
    private Channel channel;
    private ChannelPromise channelFuture;
    private HelloMessage helloMessage;
    private NetconfMessage message;
    private NetconfClientSessionListener sessionListener;
    private NetconfClientSession clientSession;

    @Before
    public void setUp() {
        channel = mock(Channel.class);
        channelFuture = mock(ChannelPromise.class);
        mockEventLoop();
        doReturn(channelFuture).when(channel).newPromise();
        doReturn(channelFuture).when(channel).writeAndFlush(any());
        doReturn(channelFuture).when(channel).writeAndFlush(any(), any(ChannelPromise.class));
        doReturn(channelFuture).when(channelFuture).addListener(any(GenericFutureListener.class));
        final var caps = Set.of("a", "b");
        helloMessage = HelloMessage.createServerHello(caps, new SessionIdType(Uint32.TEN));
        message = new NetconfMessage(helloMessage.getDocument());
        sessionListener = mock(NetconfClientSessionListener.class);
        clientSession = new NetconfClientSession(sessionListener, channel, new SessionIdType(Uint32.valueOf(20)), caps);
    }

    private void mockEventLoop() {
        final EventLoop eventLoop = mock(EventLoop.class);
        doReturn(eventLoop).when(channel).eventLoop();
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(eventLoop).execute(any(Runnable.class));
    }

    @Test
    public void testSessionDown() {
        SimpleNetconfClientSessionListener simpleListener = new SimpleNetconfClientSessionListener();
        final Future<NetconfMessage> promise = simpleListener.sendRequest(message);
        simpleListener.onSessionUp(clientSession);
        verify(channel, times(1)).writeAndFlush(any(), any());

        simpleListener.onSessionDown(clientSession, new Exception());
        assertFalse(promise.isSuccess());
    }

    @Test
    public void testSendRequest() {
        SimpleNetconfClientSessionListener simpleListener = new SimpleNetconfClientSessionListener();
        final Future<NetconfMessage> promise = simpleListener.sendRequest(message);
        simpleListener.onSessionUp(clientSession);
        verify(channel, times(1)).writeAndFlush(any(), any());

        simpleListener.sendRequest(message);
        assertFalse(promise.isSuccess());
    }

    @Test
    public void testOnMessage() {
        SimpleNetconfClientSessionListener simpleListener = new SimpleNetconfClientSessionListener();
        final Future<NetconfMessage> promise = simpleListener.sendRequest(message);
        simpleListener.onSessionUp(clientSession);
        verify(channel, times(1)).writeAndFlush(any(), any());

        simpleListener.onMessage(clientSession, message);
        assertTrue(promise.isSuccess());
    }
}
