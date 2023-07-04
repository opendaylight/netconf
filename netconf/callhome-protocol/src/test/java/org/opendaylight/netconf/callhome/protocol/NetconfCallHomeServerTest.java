/*
 * Copyright (c) 2016 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.protocol;

import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.PublicKey;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.netconf.nettyutil.handler.ssh.client.NetconfClientBuilder;
import org.opendaylight.netconf.nettyutil.handler.ssh.client.NetconfSshClient;
import org.opendaylight.netconf.nettyutil.handler.ssh.client.NettyAwareClientSession;
import org.opendaylight.netconf.shaded.sshd.client.future.AuthFuture;
import org.opendaylight.netconf.shaded.sshd.client.session.ClientSessionImpl;
import org.opendaylight.netconf.shaded.sshd.common.future.SshFutureListener;
import org.opendaylight.netconf.shaded.sshd.common.io.IoAcceptor;
import org.opendaylight.netconf.shaded.sshd.common.io.IoHandler;
import org.opendaylight.netconf.shaded.sshd.common.io.IoServiceFactory;
import org.opendaylight.netconf.shaded.sshd.common.session.Session;
import org.opendaylight.netconf.shaded.sshd.common.session.SessionListener;

@RunWith(MockitoJUnitRunner.class)
public class NetconfCallHomeServerTest {
    private static EventLoopGroup EVENT_LOOP_GROUP;
    private static InetSocketAddress MOCK_ADDRESS;

    @Mock
    private CallHomeAuthorizationProvider mockCallHomeAuthProv;
    @Mock
    private CallHomeAuthorization mockAuth;
    @Mock
    private CallHomeSessionContext.Factory mockFactory;
    @Mock
    private NettyAwareClientSession mockSession;
    @Mock
    private StatusRecorder mockStatusRecorder;

    private NetconfCallHomeServer instance;

    @BeforeClass
    public static void beforeClass() {
        EVENT_LOOP_GROUP = new NioEventLoopGroup();
        MOCK_ADDRESS = InetSocketAddress.createUnresolved("127.0.0.1", 123);
    }

    @AfterClass
    public static void afterClass() {
        EVENT_LOOP_GROUP.shutdownGracefully();
        EVENT_LOOP_GROUP = null;
        MOCK_ADDRESS = null;
    }

    @Before
    public void setup() {
        mockCallHomeAuthProv = mock(CallHomeAuthorizationProvider.class);
        mockAuth = mock(CallHomeAuthorization.class);
        mockFactory = mock(CallHomeSessionContext.Factory.class);
        mockSession = mock(NettyAwareClientSession.class);
        mockStatusRecorder = mock(StatusRecorder.class);

        doReturn(EVENT_LOOP_GROUP).when(mockFactory).getNettyGroup();
        instance = new NetconfCallHomeServer(new NetconfClientBuilder().build(), mockCallHomeAuthProv, mockFactory,
            MOCK_ADDRESS, mockStatusRecorder);
    }

    @Test
    public void sessionListenerShouldHandleEventsOfKeyEstablishedAndAuthenticated() throws Exception {
        // Weird - IJ was ok but command line compile failed using the usual array initializer syntax ????
        final var evt = new SessionListener.Event[2];
        evt[0] = SessionListener.Event.KeyEstablished;
        evt[1] = SessionListener.Event.Authenticated;

        final var hitOpen = new int[2];
        hitOpen[0] = 0;
        hitOpen[1] = 1;

        final var hitAuth = new int[2];
        hitAuth[0] = 1;
        hitAuth[1] = 0;

        for (var pass = 0; pass < evt.length; pass++) {
            // given
            final var mockAuthFuture = mock(AuthFuture.class);
            doReturn(null).when(mockAuthFuture).addListener(any(SshFutureListener.class));
            final var mockContext = mock(CallHomeSessionContext.class);
            doNothing().when(mockContext).openNetconfChannel();
            doReturn(mockContext).when(mockSession).getAttribute(any(Session.AttributeKey.class));

            final var serverKey = mock(PublicKey.class);
            doReturn(serverKey).when(mockSession).getServerKey();

            final var listener = instance.createSessionListener();
            doReturn(mockAuthFuture).when(mockContext).authorize();
            doReturn(false).when(mockSession).isAuthenticated();
            // when
            listener.sessionEvent(mockSession, evt[pass]);
            // then
            verify(mockContext, times(hitOpen[pass])).openNetconfChannel();
            verify(mockContext, times(hitAuth[pass])).authorize();
        }
    }

    @Test
    public void verificationOfTheServerKeyShouldBeSuccessfulForServerIsAllowed() {
        // given
        final var mockClientSession = mock(ClientSessionImpl.class);
        doReturn("test").when(mockClientSession).toString();
        final var mockSocketAddr = mock(SocketAddress.class);
        final var mockPublicKey = mock(PublicKey.class);

        doReturn(true).when(mockAuth).isServerAllowed();
        doReturn("some-session-name").when(mockAuth).getSessionName();
        doReturn(mockAuth).when(mockCallHomeAuthProv).provideAuth(mockSocketAddr, mockPublicKey);
        doReturn(null).when(mockFactory).createIfNotExists(mockClientSession, mockAuth);

        // expect
        assertFalse(instance.verifyServerKey(mockClientSession, mockSocketAddr, mockPublicKey));
    }

    @Test
    public void verificationOfTheServerKeyShouldFailIfTheServerIsNotAllowed() {
        // given

        final var mockClientSession = mock(ClientSessionImpl.class);
        final var mockSocketAddr = mock(SocketAddress.class);
        final var mockPublicKey = mock(PublicKey.class);

        doReturn(false).when(mockAuth).isServerAllowed();
        doReturn(mockAuth).when(mockCallHomeAuthProv).provideAuth(mockSocketAddr, mockPublicKey);
        doReturn("").when(mockClientSession).toString();

        // expect
        assertFalse(instance.verifyServerKey(mockClientSession, mockSocketAddr, mockPublicKey));
    }

    @Test
    public void bindShouldStartTheClientAndBindTheAddress() throws Exception {
        // given
        final var mockAcceptor = mock(IoAcceptor.class);
        final var mockMinaFactory = mock(IoServiceFactory.class);
        doReturn(mockAcceptor).when(mockMinaFactory).createAcceptor(any(IoHandler.class));
        doNothing().when(mockAcceptor).bind(any(SocketAddress.class));
        final var mockClient = mock(NetconfSshClient.class);
        doNothing().when(mockClient).start();
        doNothing().when(mockClient).setServerKeyVerifier(any());
        doNothing().when(mockClient).addSessionListener(any());
        final var server = new NetconfCallHomeServer(mockClient, mockCallHomeAuthProv, mockFactory, MOCK_ADDRESS,
            mockStatusRecorder, mockMinaFactory);
        // when
        server.bind();
        // then
        verify(mockClient, times(1)).start();
        verify(mockAcceptor, times(1)).bind(MOCK_ADDRESS);
    }
}
