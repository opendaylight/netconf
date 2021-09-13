/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nettyutil.handler.ssh.client;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelPromise;
import java.io.IOException;
import java.net.SocketAddress;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.netconf.nettyutil.handler.ssh.authentication.AuthenticationHandler;
import org.opendaylight.netconf.shaded.sshd.client.channel.ClientChannel;
import org.opendaylight.netconf.shaded.sshd.client.future.AuthFuture;
import org.opendaylight.netconf.shaded.sshd.client.future.ConnectFuture;
import org.opendaylight.netconf.shaded.sshd.client.future.OpenFuture;
import org.opendaylight.netconf.shaded.sshd.client.session.ClientSession;
import org.opendaylight.netconf.shaded.sshd.common.future.CloseFuture;
import org.opendaylight.netconf.shaded.sshd.common.future.SshFuture;
import org.opendaylight.netconf.shaded.sshd.common.future.SshFutureListener;
import org.opendaylight.netconf.shaded.sshd.common.io.IoInputStream;
import org.opendaylight.netconf.shaded.sshd.common.io.IoOutputStream;
import org.opendaylight.netconf.shaded.sshd.common.io.IoReadFuture;
import org.opendaylight.netconf.shaded.sshd.common.io.IoWriteFuture;
import org.opendaylight.netconf.shaded.sshd.common.io.WritePendingException;
import org.opendaylight.netconf.shaded.sshd.common.util.buffer.Buffer;
import org.opendaylight.netconf.shaded.sshd.common.util.buffer.ByteArrayBuffer;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class AsyncSshHandlerTest {

    @Mock
    private NetconfSshClient sshClient;
    @Mock
    private AuthenticationHandler authHandler;
    @Mock
    private ChannelHandlerContext ctx;
    @Mock
    private Channel channel;
    @Mock
    private SocketAddress remoteAddress;
    @Mock
    private SocketAddress localAddress;
    @Mock
    private ChannelConfig channelConfig;

    private AsyncSshHandler asyncSshHandler;

    private SshFutureListener<ConnectFuture> sshConnectListener;
    private SshFutureListener<AuthFuture> sshAuthListener;
    private SshFutureListener<OpenFuture> sshChannelOpenListener;
    private ChannelPromise promise;

    @Before
    public void setUp() throws Exception {
        stubAuth();
        stubSshClient();
        stubChannel();
        stubCtx();

        promise = getMockedPromise();

        asyncSshHandler = new AsyncSshHandler(authHandler, sshClient, "test-node");
    }

    @After
    public void tearDown() throws Exception {
        sshConnectListener = null;
        sshAuthListener = null;
        sshChannelOpenListener = null;
        promise = null;
        asyncSshHandler.close(ctx, getMockedPromise());
    }

    private void stubAuth() throws IOException {
        doReturn("usr").when(authHandler).getUsername();

        final AuthFuture authFuture = mock(AuthFuture.class);
        Futures.addCallback(stubAddListener(authFuture), new SuccessFutureListener<AuthFuture>() {
            @Override
            public void onSuccess(final SshFutureListener<AuthFuture> result) {
                sshAuthListener = result;
            }
        }, MoreExecutors.directExecutor());
        doReturn(authFuture).when(authHandler).authenticate(any(ClientSession.class));
    }

    @SuppressWarnings("unchecked")
    private static <T extends SshFuture<T>> ListenableFuture<SshFutureListener<T>> stubAddListener(final T future) {
        final SettableFuture<SshFutureListener<T>> listenerSettableFuture = SettableFuture.create();

        doAnswer(invocation -> {
            listenerSettableFuture.set((SshFutureListener<T>) invocation.getArguments()[0]);
            return null;
        }).when(future).addListener(any(SshFutureListener.class));

        return listenerSettableFuture;
    }

    private void stubCtx() {
        doReturn(channel).when(ctx).channel();
        doReturn(ctx).when(ctx).fireChannelActive();
        doReturn(ctx).when(ctx).fireChannelInactive();
        doReturn(mock(ChannelFuture.class)).when(ctx).disconnect(any(ChannelPromise.class));
        doReturn(getMockedPromise()).when(ctx).newPromise();
    }

    private void stubChannel() {
        doReturn("channel").when(channel).toString();
    }

    private void stubSshClient() throws IOException {
        final ConnectFuture connectFuture = mock(ConnectFuture.class);
        Futures.addCallback(stubAddListener(connectFuture), new SuccessFutureListener<ConnectFuture>() {
            @Override
            public void onSuccess(final SshFutureListener<ConnectFuture> result) {
                sshConnectListener = result;
            }
        }, MoreExecutors.directExecutor());
        doReturn(connectFuture).when(sshClient).connect("usr", remoteAddress);
        doReturn(channelConfig).when(channel).config();
        doReturn(1).when(channelConfig).getConnectTimeoutMillis();
        doReturn(connectFuture).when(connectFuture).verify(1,TimeUnit.MILLISECONDS);
    }

    @Test
    public void testConnectSuccess() throws Exception {
        asyncSshHandler.connect(ctx, remoteAddress, localAddress, promise);

        final IoInputStream asyncOut = getMockedIoInputStream();
        final IoOutputStream asyncIn = getMockedIoOutputStream();
        final NettyAwareChannelSubsystem subsystemChannel = getMockedSubsystemChannel(asyncOut, asyncIn);
        final ClientSession sshSession = getMockedSshSession(subsystemChannel);
        final ConnectFuture connectFuture = getSuccessConnectFuture(sshSession);

        sshConnectListener.operationComplete(connectFuture);
        sshAuthListener.operationComplete(getSuccessAuthFuture());
        sshChannelOpenListener.operationComplete(getSuccessOpenFuture());

        verify(subsystemChannel).setStreaming(ClientChannel.Streaming.Async);

        verify(promise).setSuccess();
        verify(ctx).fireChannelActive();
        asyncSshHandler.close(ctx, getMockedPromise());
        verify(ctx).fireChannelInactive();
    }

    @Test
    public void testWrite() throws Exception {
        asyncSshHandler.connect(ctx, remoteAddress, localAddress, promise);

        final IoInputStream asyncOut = getMockedIoInputStream();
        final IoOutputStream asyncIn = getMockedIoOutputStream();
        final NettyAwareChannelSubsystem subsystemChannel = getMockedSubsystemChannel(asyncOut, asyncIn);
        final ClientSession sshSession = getMockedSshSession(subsystemChannel);
        final ConnectFuture connectFuture = getSuccessConnectFuture(sshSession);

        sshConnectListener.operationComplete(connectFuture);
        sshAuthListener.operationComplete(getSuccessAuthFuture());
        sshChannelOpenListener.operationComplete(getSuccessOpenFuture());

        final ChannelPromise writePromise = getMockedPromise();
        asyncSshHandler.write(ctx, Unpooled.copiedBuffer(new byte[]{0, 1, 2, 3, 4, 5}), writePromise);

        verify(writePromise).setSuccess();
    }

    @Test
    public void testWriteClosed() throws Exception {
        asyncSshHandler.connect(ctx, remoteAddress, localAddress, promise);

        final IoInputStream asyncOut = getMockedIoInputStream();
        final IoOutputStream asyncIn = getMockedIoOutputStream();

        final IoWriteFuture ioWriteFuture = asyncIn.writeBuffer(new ByteArrayBuffer());

        Futures.addCallback(stubAddListener(ioWriteFuture), new SuccessFutureListener<IoWriteFuture>() {
            @Override
            public void onSuccess(final SshFutureListener<IoWriteFuture> result) {
                doReturn(false).when(ioWriteFuture).isWritten();
                doReturn(new IllegalStateException()).when(ioWriteFuture).getException();
                result.operationComplete(ioWriteFuture);
            }
        }, MoreExecutors.directExecutor());

        final NettyAwareChannelSubsystem subsystemChannel = getMockedSubsystemChannel(asyncOut, asyncIn);
        final ClientSession sshSession = getMockedSshSession(subsystemChannel);
        final ConnectFuture connectFuture = getSuccessConnectFuture(sshSession);

        sshConnectListener.operationComplete(connectFuture);
        sshAuthListener.operationComplete(getSuccessAuthFuture());
        sshChannelOpenListener.operationComplete(getSuccessOpenFuture());

        final ChannelPromise writePromise = getMockedPromise();
        asyncSshHandler.write(ctx, Unpooled.copiedBuffer(new byte[]{0,1,2,3,4,5}), writePromise);

        verify(writePromise).setFailure(any(Throwable.class));
    }

    @Test
    public void testWritePendingOne() throws Exception {
        asyncSshHandler.connect(ctx, remoteAddress, localAddress, promise);

        final IoInputStream asyncOut = getMockedIoInputStream();
        final IoOutputStream asyncIn = getMockedIoOutputStream();
        final IoWriteFuture ioWriteFuture = asyncIn.writeBuffer(new ByteArrayBuffer());

        final NettyAwareChannelSubsystem subsystemChannel = getMockedSubsystemChannel(asyncOut, asyncIn);
        final ClientSession sshSession = getMockedSshSession(subsystemChannel);
        final ConnectFuture connectFuture = getSuccessConnectFuture(sshSession);

        sshConnectListener.operationComplete(connectFuture);
        sshAuthListener.operationComplete(getSuccessAuthFuture());
        sshChannelOpenListener.operationComplete(getSuccessOpenFuture());

        final ChannelPromise firstWritePromise = getMockedPromise();

        // intercept listener for first write,
        // so we can invoke successful write later thus simulate pending of the first write
        final ListenableFuture<SshFutureListener<IoWriteFuture>> firstWriteListenerFuture =
                stubAddListener(ioWriteFuture);
        asyncSshHandler.write(ctx, Unpooled.copiedBuffer(new byte[]{0,1,2,3,4,5}), firstWritePromise);
        final SshFutureListener<IoWriteFuture> firstWriteListener = firstWriteListenerFuture.get();
        // intercept second listener,
        // this is the listener for pending write for the pending write to know when pending state ended
        final ListenableFuture<SshFutureListener<IoWriteFuture>> pendingListener = stubAddListener(ioWriteFuture);

        final ChannelPromise secondWritePromise = getMockedPromise();
        asyncSshHandler.write(ctx, Unpooled.copiedBuffer(new byte[]{0, 1, 2, 3, 4, 5}), secondWritePromise);

        doReturn(ioWriteFuture).when(asyncIn).writeBuffer(any(Buffer.class));

        verifyNoMoreInteractions(firstWritePromise, secondWritePromise);

        // make first write stop pending
        firstWriteListener.operationComplete(ioWriteFuture);

        // notify listener for second write that pending has ended
        pendingListener.get().operationComplete(ioWriteFuture);

        // verify both write promises successful
        verify(firstWritePromise).setSuccess();
        verify(secondWritePromise).setSuccess();
    }

    @Ignore("Pending queue is not limited")
    @Test
    public void testWritePendingMax() throws Exception {
        asyncSshHandler.connect(ctx, remoteAddress, localAddress, promise);

        final IoInputStream asyncOut = getMockedIoInputStream();
        final IoOutputStream asyncIn = getMockedIoOutputStream();
        final IoWriteFuture ioWriteFuture = asyncIn.writeBuffer(new ByteArrayBuffer());

        final NettyAwareChannelSubsystem subsystemChannel = getMockedSubsystemChannel(asyncOut, asyncIn);
        final ClientSession sshSession = getMockedSshSession(subsystemChannel);
        final ConnectFuture connectFuture = getSuccessConnectFuture(sshSession);

        sshConnectListener.operationComplete(connectFuture);
        sshAuthListener.operationComplete(getSuccessAuthFuture());
        sshChannelOpenListener.operationComplete(getSuccessOpenFuture());

        final ChannelPromise firstWritePromise = getMockedPromise();

        // intercept listener for first write,
        // so we can invoke successful write later thus simulate pending of the first write
        final ListenableFuture<SshFutureListener<IoWriteFuture>> firstWriteListenerFuture =
                stubAddListener(ioWriteFuture);
        asyncSshHandler.write(ctx, Unpooled.copiedBuffer(new byte[]{0,1,2,3,4,5}), firstWritePromise);

        final ChannelPromise secondWritePromise = getMockedPromise();
        // now make write throw pending exception
        doThrow(WritePendingException.class).when(asyncIn).writeBuffer(any(Buffer.class));
        for (int i = 0; i < 1001; i++) {
            asyncSshHandler.write(ctx, Unpooled.copiedBuffer(new byte[]{0, 1, 2, 3, 4, 5}), secondWritePromise);
        }

        verify(secondWritePromise, times(1)).setFailure(any(Throwable.class));
    }

    @Test
    public void testDisconnect() throws Exception {
        asyncSshHandler.connect(ctx, remoteAddress, localAddress, promise);

        final IoInputStream asyncOut = getMockedIoInputStream();
        final IoOutputStream asyncIn = getMockedIoOutputStream();
        final NettyAwareChannelSubsystem subsystemChannel = getMockedSubsystemChannel(asyncOut, asyncIn);
        final ClientSession sshSession = getMockedSshSession(subsystemChannel);
        final ConnectFuture connectFuture = getSuccessConnectFuture(sshSession);

        sshConnectListener.operationComplete(connectFuture);
        sshAuthListener.operationComplete(getSuccessAuthFuture());
        sshChannelOpenListener.operationComplete(getSuccessOpenFuture());

        final ChannelPromise disconnectPromise = getMockedPromise();
        asyncSshHandler.disconnect(ctx, disconnectPromise);

        verify(sshSession).close(anyBoolean());
        verify(disconnectPromise).setSuccess();
        //verify(ctx).fireChannelInactive();
    }

    private static OpenFuture getSuccessOpenFuture() {
        final OpenFuture failedOpenFuture = mock(OpenFuture.class);
        doReturn(true).when(failedOpenFuture).isOpened();
        return failedOpenFuture;
    }

    private static AuthFuture getSuccessAuthFuture() {
        final AuthFuture authFuture = mock(AuthFuture.class);
        doReturn(true).when(authFuture).isSuccess();
        return authFuture;
    }

    private static ConnectFuture getSuccessConnectFuture(final ClientSession sshSession) {
        final ConnectFuture connectFuture = mock(ConnectFuture.class);
        doReturn(true).when(connectFuture).isConnected();

        doReturn(sshSession).when(connectFuture).getSession();
        return connectFuture;
    }

    private static NettyAwareClientSession getMockedSshSession(final NettyAwareChannelSubsystem subsystemChannel)
            throws IOException {
        final NettyAwareClientSession sshSession = mock(NettyAwareClientSession.class);

        doReturn("serverVersion").when(sshSession).getServerVersion();
        doReturn(false).when(sshSession).isClosed();
        doReturn(false).when(sshSession).isClosing();
        final CloseFuture closeFuture = mock(CloseFuture.class);
        Futures.addCallback(stubAddListener(closeFuture), new SuccessFutureListener<CloseFuture>() {
            @Override
            public void onSuccess(final SshFutureListener<CloseFuture> result) {
                doReturn(true).when(closeFuture).isClosed();
                result.operationComplete(closeFuture);
            }
        }, MoreExecutors.directExecutor());
        doReturn(closeFuture).when(sshSession).close(false);

        doReturn(subsystemChannel).when(sshSession).createSubsystemChannel(eq("netconf"), any());

        return sshSession;
    }

    private NettyAwareChannelSubsystem getMockedSubsystemChannel(final IoInputStream asyncOut,
                                                       final IoOutputStream asyncIn) throws IOException {
        final NettyAwareChannelSubsystem subsystemChannel = mock(NettyAwareChannelSubsystem.class);

        doNothing().when(subsystemChannel).setStreaming(any(ClientChannel.Streaming.class));
        final OpenFuture openFuture = mock(OpenFuture.class);

        Futures.addCallback(stubAddListener(openFuture), new SuccessFutureListener<OpenFuture>() {
            @Override
            public void onSuccess(final SshFutureListener<OpenFuture> result) {
                sshChannelOpenListener = result;
            }
        }, MoreExecutors.directExecutor());

        doReturn(openFuture).when(subsystemChannel).open();
        doReturn(asyncIn).when(subsystemChannel).getAsyncIn();
        doNothing().when(subsystemChannel).onClose(any());
        doNothing().when(subsystemChannel).close();
        return subsystemChannel;
    }

    private static IoOutputStream getMockedIoOutputStream() throws IOException {
        final IoOutputStream mock = mock(IoOutputStream.class);
        final IoWriteFuture ioWriteFuture = mock(IoWriteFuture.class);
        doReturn(true).when(ioWriteFuture).isWritten();

        Futures.addCallback(stubAddListener(ioWriteFuture), new SuccessFutureListener<IoWriteFuture>() {
            @Override
            public void onSuccess(final SshFutureListener<IoWriteFuture> result) {
                result.operationComplete(ioWriteFuture);
            }
        }, MoreExecutors.directExecutor());

        doReturn(ioWriteFuture).when(mock).writeBuffer(any(Buffer.class));
        doReturn(false).when(mock).isClosed();
        doReturn(false).when(mock).isClosing();
        return mock;
    }

    private static IoInputStream getMockedIoInputStream() {
        final IoInputStream mock = mock(IoInputStream.class);
        final IoReadFuture ioReadFuture = mock(IoReadFuture.class);
        // Always success for read
        Futures.addCallback(stubAddListener(ioReadFuture), new SuccessFutureListener<IoReadFuture>() {
            @Override
            public void onSuccess(final SshFutureListener<IoReadFuture> result) {
                result.operationComplete(ioReadFuture);
            }
        }, MoreExecutors.directExecutor());
        return mock;
    }

    @Test
    public void testConnectFailOpenChannel() throws Exception {
        asyncSshHandler.connect(ctx, remoteAddress, localAddress, promise);

        final IoInputStream asyncOut = getMockedIoInputStream();
        final IoOutputStream asyncIn = getMockedIoOutputStream();
        final NettyAwareChannelSubsystem subsystemChannel = getMockedSubsystemChannel(asyncOut, asyncIn);
        final ClientSession sshSession = getMockedSshSession(subsystemChannel);
        final ConnectFuture connectFuture = getSuccessConnectFuture(sshSession);

        sshConnectListener.operationComplete(connectFuture);

        sshAuthListener.operationComplete(getSuccessAuthFuture());

        verify(subsystemChannel).setStreaming(ClientChannel.Streaming.Async);

        sshChannelOpenListener.operationComplete(getFailedOpenFuture());
        verify(promise).setFailure(any(Throwable.class));
    }

    @Test
    public void testConnectFailAuth() throws Exception {
        asyncSshHandler.connect(ctx, remoteAddress, localAddress, promise);

        final NettyAwareClientSession sshSession = mock(NettyAwareClientSession.class);
        doReturn(true).when(sshSession).isClosed();
        final ConnectFuture connectFuture = getSuccessConnectFuture(sshSession);

        sshConnectListener.operationComplete(connectFuture);

        final AuthFuture authFuture = getFailedAuthFuture();

        sshAuthListener.operationComplete(authFuture);
        verify(promise).setFailure(any(Throwable.class));
        asyncSshHandler.close(ctx, getMockedPromise());
        verify(ctx, times(0)).fireChannelInactive();
    }

    private static AuthFuture getFailedAuthFuture() {
        final AuthFuture authFuture = mock(AuthFuture.class);
        doReturn(false).when(authFuture).isSuccess();
        doReturn(new IllegalStateException()).when(authFuture).getException();
        return authFuture;
    }

    private static OpenFuture getFailedOpenFuture() {
        final OpenFuture authFuture = mock(OpenFuture.class);
        doReturn(false).when(authFuture).isOpened();
        doReturn(new IllegalStateException()).when(authFuture).getException();
        return authFuture;
    }

    @Test
    public void testConnectFail() throws Exception {
        asyncSshHandler.connect(ctx, remoteAddress, localAddress, promise);

        final ConnectFuture connectFuture = getFailedConnectFuture();
        sshConnectListener.operationComplete(connectFuture);
        verify(promise).setFailure(any(Throwable.class));
    }

    private static ConnectFuture getFailedConnectFuture() {
        final ConnectFuture connectFuture = mock(ConnectFuture.class);
        doReturn(false).when(connectFuture).isConnected();
        doReturn(new IllegalStateException()).when(connectFuture).getException();
        return connectFuture;
    }

    private ChannelPromise getMockedPromise() {
        return spy(new DefaultChannelPromise(channel));
    }

    private abstract static class SuccessFutureListener<T extends SshFuture<T>>
            implements FutureCallback<SshFutureListener<T>> {

        @Override
        public abstract void onSuccess(SshFutureListener<T> result);

        @Override
        public void onFailure(final Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }
}
