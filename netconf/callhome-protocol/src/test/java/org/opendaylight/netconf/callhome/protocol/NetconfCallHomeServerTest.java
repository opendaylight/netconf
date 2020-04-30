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
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.netconf.shaded.sshd.client.SshClient;
import org.opendaylight.netconf.shaded.sshd.client.future.AuthFuture;
import org.opendaylight.netconf.shaded.sshd.client.session.ClientSession;
import org.opendaylight.netconf.shaded.sshd.client.session.ClientSessionImpl;
import org.opendaylight.netconf.shaded.sshd.common.future.SshFutureListener;
import org.opendaylight.netconf.shaded.sshd.common.io.IoAcceptor;
import org.opendaylight.netconf.shaded.sshd.common.io.IoHandler;
import org.opendaylight.netconf.shaded.sshd.common.io.IoServiceFactory;
import org.opendaylight.netconf.shaded.sshd.common.kex.KeyExchange;
import org.opendaylight.netconf.shaded.sshd.common.session.Session;
import org.opendaylight.netconf.shaded.sshd.common.session.SessionListener;

@RunWith(MockitoJUnitRunner.class)
public class NetconfCallHomeServerTest {
    private static EventLoopGroup EVENT_LOOP_GROUP;
    private static InetSocketAddress MOCK_ADDRESS;

    private SshClient mockSshClient;
    @Mock
    private CallHomeAuthorizationProvider mockCallHomeAuthProv;
    @Mock
    private CallHomeAuthorization mockAuth;
    @Mock
    private CallHomeSessionContext.Factory mockFactory;
    @Mock
    private ClientSession mockSession;
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
        mockSshClient = spy(SshClient.setUpDefaultClient());
        mockCallHomeAuthProv = mock(CallHomeAuthorizationProvider.class);
        mockAuth = mock(CallHomeAuthorization.class);
        mockFactory = mock(CallHomeSessionContext.Factory.class);
        mockSession = mock(ClientSession.class);
        mockStatusRecorder = mock(StatusRecorder.class);

        Map<String, String> props = new HashMap<>();
        props.put("nio-workers", "1");
        doReturn(EVENT_LOOP_GROUP).when(mockFactory).getNettyGroup();
        instance = new NetconfCallHomeServer(
            mockSshClient, mockCallHomeAuthProv, mockFactory, MOCK_ADDRESS, mockStatusRecorder);
    }

    @Test
    public void sessionListenerShouldHandleEventsOfKeyEstablishedAndAuthenticated() throws IOException {
        // Weird - IJ was ok but command line compile failed using the usual array initializer syntax ????
        SessionListener.Event[] evt = new SessionListener.Event[2];
        evt[0] = SessionListener.Event.KeyEstablished;
        evt[1] = SessionListener.Event.Authenticated;

        int[] hitOpen = new int[2];
        hitOpen[0] = 0;
        hitOpen[1] = 1;

        int[] hitAuth = new int[2];
        hitAuth[0] = 1;
        hitAuth[1] = 0;

        for (int pass = 0; pass < evt.length; pass++) {
            // given
            AuthFuture mockAuthFuture = mock(AuthFuture.class);
            doReturn(null).when(mockAuthFuture).addListener(any(SshFutureListener.class));
            CallHomeSessionContext mockContext = mock(CallHomeSessionContext.class);
            doNothing().when(mockContext).openNetconfChannel();
            doReturn(mockContext).when(mockSession).getAttribute(any(Session.AttributeKey.class));

            final KeyExchange kex = mock(KeyExchange.class);
            doReturn(kex).when(mockSession).getKex();
            final PublicKey serverKey = mock(PublicKey.class);
            doReturn(serverKey).when(kex).getServerKey();

            SessionListener listener = instance.createSessionListener();
            doReturn(mockAuthFuture).when(mockContext).authorize();
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

        ClientSessionImpl mockClientSession = mock(ClientSessionImpl.class);
        Mockito.doReturn("test").when(mockClientSession).toString();
        SocketAddress mockSocketAddr = mock(SocketAddress.class);
        PublicKey mockPublicKey = mock(PublicKey.class);

        Mockito.doReturn(true).when(mockAuth).isServerAllowed();
        Mockito.doReturn("some-session-name").when(mockAuth).getSessionName();

        Mockito.doReturn(mockAuth).when(mockCallHomeAuthProv).provideAuth(mockSocketAddr, mockPublicKey);

        Mockito.doReturn(null).when(mockFactory).createIfNotExists(mockClientSession, mockAuth, mockSocketAddr);

        // expect
        instance.verifyServerKey(mockClientSession, mockSocketAddr, mockPublicKey);
    }

    @Test
    public void verificationOfTheServerKeyShouldFailIfTheServerIsNotAllowed() {
        // given

        ClientSessionImpl mockClientSession = mock(ClientSessionImpl.class);
        SocketAddress mockSocketAddr = mock(SocketAddress.class);
        PublicKey mockPublicKey = mock(PublicKey.class);

        Mockito.doReturn(false).when(mockAuth).isServerAllowed();
        Mockito.doReturn(mockAuth).when(mockCallHomeAuthProv).provideAuth(mockSocketAddr, mockPublicKey);
        Mockito.doReturn("").when(mockClientSession).toString();

        // expect
        assertFalse(instance.verifyServerKey(mockClientSession, mockSocketAddr, mockPublicKey));
    }

    @Test
    public void bindShouldStartTheClientAndBindTheAddress() throws IOException {
        // given
        IoAcceptor mockAcceptor = mock(IoAcceptor.class);
        IoServiceFactory mockMinaFactory = mock(IoServiceFactory.class);

        Mockito.doReturn(mockAcceptor).when(mockMinaFactory).createAcceptor(any(IoHandler.class));
        Mockito.doNothing().when(mockAcceptor).bind(any(SocketAddress.class));
        instance = new NetconfCallHomeServer(
                mockSshClient, mockCallHomeAuthProv, mockFactory, MOCK_ADDRESS, mockStatusRecorder, mockMinaFactory);
        // when
        instance.bind();
        // then
        verify(mockSshClient, times(1)).start();
        verify(mockAcceptor, times(1)).bind(MOCK_ADDRESS);
    }
}
