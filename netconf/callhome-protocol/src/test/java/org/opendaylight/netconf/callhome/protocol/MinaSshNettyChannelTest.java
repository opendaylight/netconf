/*
 * Copyright (c) 2016 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.protocol;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
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
import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.netconf.callhome.protocol.CallHomeSessionContext.SshWriteAsyncHandlerAdapter;
import org.opendaylight.netconf.shaded.sshd.client.channel.ClientChannel;
import org.opendaylight.netconf.shaded.sshd.client.session.ClientSession;
import org.opendaylight.netconf.shaded.sshd.common.io.IoInputStream;
import org.opendaylight.netconf.shaded.sshd.common.io.IoOutputStream;
import org.opendaylight.netconf.shaded.sshd.common.io.IoReadFuture;
import org.opendaylight.netconf.shaded.sshd.common.io.IoWriteFuture;
import org.opendaylight.netconf.shaded.sshd.common.util.buffer.Buffer;

public class MinaSshNettyChannelTest {
    private CallHomeSessionContext mockContext;
    private ClientSession mockSession;
    private ClientChannel mockChannel;
    private MinaSshNettyChannel instance;

    @Before
    public void setup() throws IOException {
        IoReadFuture mockFuture = mock(IoReadFuture.class);
        IoInputStream mockIn = mock(IoInputStream.class);
        doReturn(mockFuture).when(mockIn).read(any(Buffer.class));
        mockContext = mock(CallHomeSessionContext.class);
        mockSession = mock(ClientSession.class);
        mockChannel = mock(ClientChannel.class);
        doReturn(mockIn).when(mockChannel).getAsyncOut();

        IoOutputStream mockOut = mock(IoOutputStream.class);
        doReturn(mockOut).when(mockChannel).getAsyncIn();

        IoWriteFuture mockWrFuture = mock(IoWriteFuture.class);
        doReturn(false).when(mockOut).isClosed();
        doReturn(false).when(mockOut).isClosing();
        doReturn(mockWrFuture).when(mockOut).writeBuffer(any(Buffer.class));
        doReturn(null).when(mockWrFuture).addListener(any());

        doReturn(mockFuture).when(mockFuture).addListener(any());

        instance = new MinaSshNettyChannel(mockContext, mockSession);
    }

    @Test
    public void ourChannelHandlerShouldForwardWrites() throws Exception {
        ChannelHandler mockHandler = mock(ChannelHandler.class);
        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
        doReturn(mockHandler).when(ctx).handler();
        ChannelPromise promise = mock(ChannelPromise.class);

        // we would really like to just verify that the async handler write() was
        // called but it is a final class, so no mocking. instead we set up the
        // mock channel to have no async input, which will cause a failure later
        // on the write promise that we use as a cheap way to tell that write()
        // got called. ick.

        doReturn(null).when(mockChannel).getAsyncIn();
        doReturn(null).when(promise).setFailure(any(Throwable.class));

        // Need to reconstruct instance to pick up null async in above
        instance = new MinaSshNettyChannel(mockContext, mockSession);
        instance.pipeline().addFirst(new SshWriteAsyncHandlerAdapter(mockChannel));

        // when
        ChannelOutboundHandlerAdapter outadapter = (ChannelOutboundHandlerAdapter) instance.pipeline().first();
        ByteBufAllocator mockAlloc = mock(ByteBufAllocator.class);
        ByteBuf bytes = new EmptyByteBuf(mockAlloc);
        outadapter.write(ctx, bytes, promise);

        // then
        verify(promise, times(1)).setFailure(any(Throwable.class));
    }
}
