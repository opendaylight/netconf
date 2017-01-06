/*
 * Copyright (c) 2016 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.callhome.protocol;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.PublicKey;
import org.apache.sshd.ClientChannel;
import org.apache.sshd.ClientChannel.Streaming;
import org.apache.sshd.ClientSession;
import org.apache.sshd.client.channel.ChannelSubsystem;
import org.apache.sshd.client.future.OpenFuture;
import org.apache.sshd.client.session.ClientSessionImpl;
import org.apache.sshd.common.KeyExchange;
import org.apache.sshd.common.Session.AttributeKey;
import org.apache.sshd.common.future.SshFutureListener;
import org.apache.sshd.common.io.IoInputStream;
import org.apache.sshd.common.io.IoOutputStream;
import org.apache.sshd.common.io.IoReadFuture;
import org.apache.sshd.common.io.IoSession;
import org.apache.sshd.common.util.Buffer;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mockito;
import org.opendaylight.netconf.client.NetconfClientSessionListener;
import org.opendaylight.netconf.client.NetconfClientSessionNegotiatorFactory;

public class CallHomeSessionContextTest {
    private ClientSessionImpl mockSession;
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
        mockSession = mock(ClientSessionImpl.class);
        mockAuth = mock(CallHomeAuthorization.class);
        mockChannel = mock(ClientChannel.class);
        address = mock(InetSocketAddress.class);

        mockNegotiatior = mock(NetconfClientSessionNegotiatorFactory.class);
        subListener = mock(CallHomeNetconfSubsystemListener.class);
        mockNettyGroup = mock(EventLoopGroup.class);

        realFactory = new CallHomeSessionContext.Factory(mockNettyGroup, mockNegotiatior, subListener);



        KeyExchange kexMock = Mockito.mock(KeyExchange.class);
        Mockito.doReturn(kexMock).when(mockSession).getKex();

        PublicKey keyMock = Mockito.mock(PublicKey.class);
        Mockito.doReturn(keyMock).when(kexMock).getServerKey();
        IoReadFuture mockFuture = mock(IoReadFuture.class);
        IoInputStream mockIn = mock(IoInputStream.class);
        Mockito.doReturn(mockFuture).when(mockIn).read(any(Buffer.class));
        IoOutputStream mockOut = mock(IoOutputStream.class);

        Mockito.doReturn(mockIn).when(mockChannel).getAsyncOut();
        Mockito.doReturn(mockOut).when(mockChannel).getAsyncIn();

        Mockito.doReturn(true).when(mockAuth).isServerAllowed();

        IoSession ioSession = mock(IoSession.class);
        Mockito.doReturn(ioSession).when(mockSession).getIoSession();
        Mockito.doReturn(address).when(ioSession).getRemoteAddress();
        Mockito.doReturn(null).when(mockSession).setAttribute(any(AttributeKey.class), any());
        Mockito.doReturn(null).when(mockSession).getAttribute(any(AttributeKey.class));
        Mockito.doReturn("testSession").when(mockSession).toString();

        Mockito.doNothing().when(mockAuth).applyTo(mockSession);
        instance = realFactory.createIfNotExists(mockSession, mockAuth, address);
    }

    @Test
    public void TheContextShouldBeSettableAndRetrievableAsASessionAttribute() {
        // redo instance below because previous constructor happened too early to capture behavior
        instance = realFactory.createIfNotExists(mockSession, mockAuth, address);
        // when
        CallHomeSessionContext.getFrom(mockSession);
        // then
        verify(mockSession, times(1)).setAttribute(CallHomeSessionContext.SESSION_KEY, instance);
        verify(mockSession, times(1)).getAttribute(CallHomeSessionContext.SESSION_KEY);
    }

    @Test
    public void AnAuthorizeActionShouldApplyToTheBoundSession() throws IOException {
        // when
        Mockito.doReturn(null).when(mockSession).auth();
        instance.authorize();
        // then
        verify(mockAuth, times(1)).applyTo(mockSession);
    }

    @Test
    public void CreatingAChannelSuccessfullyShouldResultInAnAttachedListener() throws IOException {
        // given
        OpenFuture mockFuture = mock(OpenFuture.class);
        ChannelSubsystem mockChannel = mock(ChannelSubsystem.class);
        Mockito.doReturn(mockFuture).when(mockChannel).open();
        Mockito.doReturn(mockChannel).when(mockSession).createSubsystemChannel(anyString());

        Mockito.doReturn(null).when(mockFuture).addListener(any(SshFutureListener.class));
        Mockito.doNothing().when(mockChannel).setStreaming(any(Streaming.class));
        // when
        instance.openNetconfChannel();
        // then
        verify(mockFuture, times(1)).addListener(any(SshFutureListener.class));
    }

    static class TestableContext extends CallHomeSessionContext {
        MinaSshNettyChannel minaMock;

        public TestableContext(ClientSession sshSession, CallHomeAuthorization authorization, InetSocketAddress address,
                CallHomeSessionContext.Factory factory, MinaSshNettyChannel minaMock) {
            super(sshSession, authorization, address, factory);
            this.minaMock = minaMock;
        }

        @Override
        protected MinaSshNettyChannel newMinaSshNettyChannel(ClientChannel netconfChannel) {
            return minaMock;
        }
    }

    @Ignore
    @Test
    public void OpeningTheChannelSuccessfullyShouldFireActiveChannel() {
        // given
        MinaSshNettyChannel mockMinaChannel = mock(MinaSshNettyChannel.class);
        CallHomeSessionContext.Factory mockFactory = mock(CallHomeSessionContext.Factory.class);

        ChannelFuture mockChanFuture = mock(ChannelFuture.class);
        Mockito.doReturn(mockChanFuture).when(mockNettyGroup).register(any(Channel.class));

        Mockito.doReturn(mockNettyGroup).when(mockFactory).getNettyGroup();
        Mockito.doReturn(mockChannelInitializer).when(mockFactory)
                .getChannelInitializer(any(NetconfClientSessionListener.class));

        ChannelPipeline mockPipeline = mock(ChannelPipeline.class);
        Mockito.doReturn(mockPipeline).when(mockMinaChannel).pipeline();

        OpenFuture mockFuture = mock(OpenFuture.class);
        Mockito.doReturn(true).when(mockFuture).isOpened();

        instance = new TestableContext(mockSession, mockAuth, address, mockFactory, mockMinaChannel);
        SshFutureListener<OpenFuture> listener = instance.newSshFutureListener(mockChannel);
        // when
        listener.operationComplete(mockFuture);
        // then
        verify(mockPipeline, times(1)).fireChannelActive();
    }

    @Test
    @Ignore
    public void FailureToOpenTheChannelShouldCauseTheSessionToClose() {
        // given
        SshFutureListener<OpenFuture> listener = instance.newSshFutureListener(mockChannel);
        OpenFuture mockFuture = mock(OpenFuture.class);
        Mockito.doReturn(false).when(mockFuture).isOpened();
        Mockito.doReturn(new RuntimeException("test")).when(mockFuture).getException();
        // when
        listener.operationComplete(mockFuture);
        // then
        verify(mockSession, times(1)).close(anyBoolean());
    }
}
