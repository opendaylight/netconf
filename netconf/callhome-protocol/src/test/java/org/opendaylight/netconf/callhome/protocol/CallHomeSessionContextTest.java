/*
 * Copyright (c) 2016 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.protocol;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.DefaultChannelPipeline;
import io.netty.channel.EventLoopGroup;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.PublicKey;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.netconf.client.NetconfClientSessionListener;
import org.opendaylight.netconf.client.NetconfClientSessionNegotiatorFactory;
import org.opendaylight.netconf.nettyutil.handler.ssh.client.NetconfClientSessionImpl;
import org.opendaylight.netconf.nettyutil.handler.ssh.client.NettyPipelineAwareChannelSubsystem;
import org.opendaylight.netconf.shaded.sshd.client.channel.ClientChannel;
import org.opendaylight.netconf.shaded.sshd.client.future.OpenFuture;
import org.opendaylight.netconf.shaded.sshd.common.AttributeRepository.AttributeKey;
import org.opendaylight.netconf.shaded.sshd.common.channel.StreamingChannel;
import org.opendaylight.netconf.shaded.sshd.common.future.SshFutureListener;
import org.opendaylight.netconf.shaded.sshd.common.io.IoInputStream;
import org.opendaylight.netconf.shaded.sshd.common.io.IoOutputStream;
import org.opendaylight.netconf.shaded.sshd.common.io.IoReadFuture;
import org.opendaylight.netconf.shaded.sshd.common.io.IoSession;
import org.opendaylight.netconf.shaded.sshd.common.kex.KeyExchange;
import org.opendaylight.netconf.shaded.sshd.common.util.buffer.Buffer;

public class CallHomeSessionContextTest {
    private NetconfClientSessionImpl mockSession;
    private CallHomeAuthorization mockAuth;
    private ClientChannel mockChannel;
    private InetSocketAddress address;

    private ReverseSshChannelInitializer mockChannelInitializer;
    private CallHomeNetconfSubsystemListener subListener;
    private EventLoopGroup mockNettyGroup;
    private CallHomeSessionContext.Factory realFactory;
    private CallHomeSessionContext instance;
    private NetconfClientSessionNegotiatorFactory mockNegotiatior;

    @Before
    public void setup() {
        mockSession = mock(NetconfClientSessionImpl.class);
        mockAuth = mock(CallHomeAuthorization.class);
        mockChannel = mock(ClientChannel.class);
        address = mock(InetSocketAddress.class);

        mockNegotiatior = mock(NetconfClientSessionNegotiatorFactory.class);
        subListener = mock(CallHomeNetconfSubsystemListener.class);
        mockNettyGroup = mock(EventLoopGroup.class);

        realFactory = new CallHomeSessionContext.Factory(mockNettyGroup, mockNegotiatior, subListener);

        KeyExchange kexMock = mock(KeyExchange.class);
        doReturn(kexMock).when(mockSession).getKex();

        PublicKey keyMock = mock(PublicKey.class);
        doReturn(keyMock).when(mockSession).getServerKey();
        IoReadFuture mockFuture = mock(IoReadFuture.class);
        IoInputStream mockIn = mock(IoInputStream.class);
        doReturn(mockFuture).when(mockIn).read(any(Buffer.class));
        IoOutputStream mockOut = mock(IoOutputStream.class);

        doReturn(mockIn).when(mockChannel).getAsyncOut();
        doReturn(mockOut).when(mockChannel).getAsyncIn();

        doReturn(true).when(mockAuth).isServerAllowed();

        IoSession ioSession = mock(IoSession.class);
        doReturn(ioSession).when(mockSession).getIoSession();
        doReturn(address).when(ioSession).getRemoteAddress();
        doReturn(null).when(mockSession).setAttribute(any(AttributeKey.class), any());
        doReturn(null).when(mockSession).getAttribute(any(AttributeKey.class));
        doReturn("testSession").when(mockSession).toString();

        doNothing().when(mockAuth).applyTo(mockSession);
        doReturn("test").when(mockAuth).getSessionName();
    }

    @Test
    public void theContextShouldBeSettableAndRetrievableAsASessionAttribute() {
        // when
        instance = realFactory.createIfNotExists(mockSession, mockAuth);
        // then
        assertNotNull(instance);
        verify(mockSession, times(1)).setAttribute(CallHomeSessionContext.SESSION_KEY, instance);
        verify(mockSession, times(0)).getAttribute(any());

        // when
        CallHomeSessionContext.getFrom(mockSession);
        // then
        verify(mockSession, times(1)).getAttribute(CallHomeSessionContext.SESSION_KEY);
    }

    @Test
    public void anAuthorizeActionShouldApplyToTheBoundSession() throws IOException {
        instance = realFactory.createIfNotExists(mockSession, mockAuth);
        // when
        doReturn(null).when(mockSession).auth();
        instance.authorize();
        // then
        verify(mockAuth, times(1)).applyTo(mockSession);
    }

    @Test
    public void creatingAChannelSuccessfullyShouldResultInAnAttachedListener() throws IOException {
        // given
        OpenFuture mockFuture = mock(OpenFuture.class);
        NettyPipelineAwareChannelSubsystem mockChannelSubsystem = mock(NettyPipelineAwareChannelSubsystem.class);
        doReturn(mockFuture).when(mockChannelSubsystem).open();
        doReturn(mockChannelSubsystem).when(mockSession).createSubsystemChannel(anyString(),
                any(DefaultChannelPipeline.class));

        doReturn(null).when(mockFuture).addListener(any(SshFutureListener.class));
        doNothing().when(mockChannelSubsystem).setStreaming(any(StreamingChannel.Streaming.class));
        instance = realFactory.createIfNotExists(mockSession, mockAuth);
        // when
        instance.openNetconfChannel();
        // then
        verify(mockFuture, times(1)).addListener(any(SshFutureListener.class));
    }

    @Test
    public void openingTheChannelSuccessfullyNotifyTheChannelListener() {
        // given
        MinaSshNettyChannel mockMinaChannel = mock(MinaSshNettyChannel.class);
        CallHomeSessionContext.Factory mockFactory = mock(CallHomeSessionContext.Factory.class);

        CallHomeNetconfSubsystemListener mockListener = mock(CallHomeNetconfSubsystemListener.class);
        doNothing().when(mockListener).onNetconfSubsystemOpened(any(CallHomeProtocolSessionContext.class),
                any(CallHomeChannelActivator.class));

        ChannelFuture mockChanFuture = mock(ChannelFuture.class);
        doReturn(mockChanFuture).when(mockNettyGroup).register(any(Channel.class));

        doReturn(mockNettyGroup).when(mockFactory).getNettyGroup();
        doReturn(mockChannelInitializer).when(mockFactory)
                .getChannelInitializer(any(NetconfClientSessionListener.class));
        doReturn(mockListener).when(mockFactory).getChannelOpenListener();

        ChannelPipeline mockPipeline = mock(ChannelPipeline.class);
        doReturn(mockPipeline).when(mockMinaChannel).pipeline();

        OpenFuture mockFuture = mock(OpenFuture.class);
        doReturn(true).when(mockFuture).isOpened();

        instance = spy(new CallHomeSessionContext(mockSession, mockAuth, mockFactory));
        doReturn(mockMinaChannel).when(instance).newMinaSshNettyChannel();
        SshFutureListener<OpenFuture> listener = instance.newSshFutureListener(mockChannel, mockMinaChannel);

        // when
        listener.operationComplete(mockFuture);
        // then
        verify(mockListener, times(1)).onNetconfSubsystemOpened(any(CallHomeProtocolSessionContext.class),
                any(CallHomeChannelActivator.class));
    }

    @Test
    public void failureToOpenTheChannelShouldCauseTheSessionToClose() {
        // given
        instance = realFactory.createIfNotExists(mockSession, mockAuth);
        OpenFuture mockFuture = mock(OpenFuture.class);
        doReturn(false).when(mockFuture).isOpened();
        doReturn(new RuntimeException("test")).when(mockFuture).getException();

        doReturn(null).when(mockSession).close(anyBoolean());

        // when
        SshFutureListener<OpenFuture> listener = instance.newSshFutureListener(mockChannel, null);
        listener.operationComplete(mockFuture);
        // then
        // You'll see an error message logged to the console - it is expected.
        verify(mockSession, times(1)).close(anyBoolean());
    }

    @Test
    public void theContextConstructorShouldNotModifySession() {
        instance = new CallHomeSessionContext(mockSession, mockAuth, realFactory);
        verify(mockSession, times(0)).setAttribute(any(), any());
        assertNull(CallHomeSessionContext.getFrom(mockSession));
    }
}
