/*
 * Copyright (c) 2016 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.callhome.protocol;

import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.EmptyByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import org.apache.sshd.client.channel.ClientChannel;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.io.IoInputStream;
import org.apache.sshd.common.io.IoOutputStream;
import org.apache.sshd.common.io.IoReadFuture;
import org.apache.sshd.common.io.IoWriteFuture;
import org.apache.sshd.common.util.buffer.Buffer;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class MinaSshNettyChannelTest {
    private CallHomeSessionContext mockContext;
    private ClientSession mockSession;
    private ClientChannel mockChannel;
    private MinaSshNettyChannel instance;

    @Before
    public void setup() {
        IoReadFuture mockFuture = mock(IoReadFuture.class);
        IoInputStream mockIn = mock(IoInputStream.class);
        Mockito.doReturn(mockFuture).when(mockIn).read(any(Buffer.class));
        mockContext = mock(CallHomeSessionContext.class);
        mockSession = mock(ClientSession.class);
        mockChannel = mock(ClientChannel.class);
        Mockito.doReturn(mockIn).when(mockChannel).getAsyncOut();

        IoOutputStream mockOut = mock(IoOutputStream.class);
        Mockito.doReturn(mockOut).when(mockChannel).getAsyncIn();

        IoWriteFuture mockWrFuture = mock(IoWriteFuture.class);
        Mockito.doReturn(false).when(mockOut).isClosed();
        Mockito.doReturn(false).when(mockOut).isClosing();
        Mockito.doReturn(mockWrFuture).when(mockOut).write(any(Buffer.class));
        Mockito.doReturn(null).when(mockWrFuture).addListener(any());

        Mockito.doReturn(mockFuture).when(mockFuture).addListener(Mockito.any());

        instance = new MinaSshNettyChannel(mockContext, mockSession, mockChannel);
    }

    @Test
    public void ourChannelHandlerShouldBeFirstInThePipeline() {
        // given
        ChannelHandler firstHandler = instance.pipeline().first();
        String firstName = firstHandler.getClass().getName();
        // expect
        assertTrue(firstName.contains("callhome"));
    }

    @Test
    public void ourChannelHandlerShouldForwardWrites() throws Exception {
        ChannelHandler mockHandler = mock(ChannelHandler.class);
        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
        Mockito.doReturn(mockHandler).when(ctx).handler();
        ChannelPromise promise = mock(ChannelPromise.class);

        // we would really like to just verify that the async handler write() was
        // called but it is a final class, so no mocking. instead we set up the
        // mock channel to have no async input, which will cause a failure later
        // on the write promise that we use as a cheap way to tell that write()
        // got called. ick.

        Mockito.doReturn(null).when(mockChannel).getAsyncIn();
        Mockito.doReturn(null).when(promise).setFailure(any(Throwable.class));

        // Need to reconstruct instance to pick up null async in above
        instance = new MinaSshNettyChannel(mockContext, mockSession, mockChannel);

        // when
        ChannelOutboundHandlerAdapter outadapter = (ChannelOutboundHandlerAdapter) instance.pipeline().first();
        ByteBufAllocator mockAlloc = mock(ByteBufAllocator.class);
        ByteBuf bytes = new EmptyByteBuf(mockAlloc);
        outadapter.write(ctx, bytes, promise);

        // then
        verify(promise, times(1)).setFailure(any(Throwable.class));
    }
}
