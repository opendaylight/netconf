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
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyObject;
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
import org.mockito.internal.util.collections.Sets;
import org.opendaylight.netconf.api.NetconfDocumentedException;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.api.messages.NetconfHelloMessage;

public class SimpleNetconfClientSessionListenerTest {

    private Channel channel;
    private ChannelPromise channelFuture;
    Set<String> caps;
    private NetconfHelloMessage helloMessage;
    private NetconfMessage message;
    private NetconfClientSessionListener sessionListener;
    private NetconfClientSession clientSession;

    @Before
    public void setUp() throws NetconfDocumentedException {
        channel = mock(Channel.class);
        channelFuture = mock(ChannelPromise.class);
        mockEventLoop();
        doReturn(channelFuture).when(channel).newPromise();
        doReturn(channelFuture).when(channel).writeAndFlush(anyObject());
        doReturn(channelFuture).when(channel).writeAndFlush(anyObject(), any(ChannelPromise.class));
        doReturn(channelFuture).when(channelFuture).addListener(any(GenericFutureListener.class));
        caps = Sets.newSet("a", "b");
        helloMessage = NetconfHelloMessage.createServerHello(caps, 10);
        message = new NetconfMessage(helloMessage.getDocument());
        sessionListener = mock(NetconfClientSessionListener.class);
        clientSession = new NetconfClientSession(sessionListener, channel, 20L, caps);
    }

    private void mockEventLoop() {
        final EventLoop eventLoop = mock(EventLoop.class);
        doReturn(eventLoop).when(channel).eventLoop();
        doAnswer(invocation -> {
            invocation.getArgumentAt(0, Runnable.class).run();
            return null;
        }).when(eventLoop).execute(any(Runnable.class));
    }

    @Test
    public void testSessionDown() throws Exception {
        SimpleNetconfClientSessionListener simpleListener = new SimpleNetconfClientSessionListener();
        final Future<NetconfMessage> promise = simpleListener.sendRequest(message);
        simpleListener.onSessionUp(clientSession);
        verify(channel, times(1)).writeAndFlush(anyObject(), anyObject());

        simpleListener.onSessionDown(clientSession, new Exception());
        assertFalse(promise.isSuccess());
    }

    @Test
    public void testSendRequest() {
        SimpleNetconfClientSessionListener simpleListener = new SimpleNetconfClientSessionListener();
        final Future<NetconfMessage> promise = simpleListener.sendRequest(message);
        simpleListener.onSessionUp(clientSession);
        verify(channel, times(1)).writeAndFlush(anyObject(), anyObject());

        simpleListener.sendRequest(message);
        assertFalse(promise.isSuccess());
    }

    @Test
    public void testOnMessage() {
        SimpleNetconfClientSessionListener simpleListener = new SimpleNetconfClientSessionListener();
        final Future<NetconfMessage> promise = simpleListener.sendRequest(message);
        simpleListener.onSessionUp(clientSession);
        verify(channel, times(1)).writeAndFlush(anyObject(), anyObject());

        simpleListener.onMessage(clientSession, message);
        assertTrue(promise.isSuccess());
    }
}
