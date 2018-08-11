/*
 * Copyright (c) 2016 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.protocol;

import static org.junit.Assert.assertFalse;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoopGroup;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.future.AuthFuture;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.client.session.ClientSessionImpl;
import org.apache.sshd.common.future.SshFutureListener;
import org.apache.sshd.common.io.IoAcceptor;
import org.apache.sshd.common.io.IoHandler;
import org.apache.sshd.common.io.IoServiceFactory;
import org.apache.sshd.common.session.Session;
import org.apache.sshd.common.session.SessionListener;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

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
        EVENT_LOOP_GROUP = new DefaultEventLoopGroup();
        MOCK_ADDRESS = InetSocketAddress.createUnresolved("1.2.3.4", 123);
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
        doReturn(EVENT_LOOP_GROUP).when(mockFactory).getNettyGroup();

        Map<String, String> props = new HashMap<>();
        props.put("nio-workers", "1");
        doReturn(props).when(mockSshClient).getProperties();
        doReturn("test").when(mockSession).toString();
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
        doReturn("test").when(mockClientSession).toString();
        SocketAddress mockSocketAddr = mock(SocketAddress.class);
        doReturn("testAddr").when(mockSocketAddr).toString();
        PublicKey mockPublicKey = mock(PublicKey.class);

        doReturn("test").when(mockAuth).toString();
        doReturn(true).when(mockAuth).isServerAllowed();
        doReturn("some-session-name").when(mockAuth).getSessionName();

        doReturn(mockAuth).when(mockCallHomeAuthProv).provideAuth(mockSocketAddr, mockPublicKey);

        doReturn(null).when(mockFactory).createIfNotExists(mockClientSession, mockAuth, mockSocketAddr);

        // expect
        instance.verifyServerKey(mockClientSession, mockSocketAddr, mockPublicKey);
    }

    @Test
    public void verificationOfTheServerKeyShouldFailIfTheServerIsNotAllowed() {
        // given

        ClientSessionImpl mockClientSession = mock(ClientSessionImpl.class);
        SocketAddress mockSocketAddr = mock(SocketAddress.class);
        PublicKey mockPublicKey = mock(PublicKey.class);

        doReturn(false).when(mockAuth).isServerAllowed();
        doReturn(mockAuth).when(mockCallHomeAuthProv).provideAuth(mockSocketAddr, mockPublicKey);
        doReturn("").when(mockClientSession).toString();

        // expect
        assertFalse(instance.verifyServerKey(mockClientSession, mockSocketAddr, mockPublicKey));
    }

    @Test
    public void bindShouldStartTheClientAndBindTheAddress() throws IOException {
        // given
        IoAcceptor mockAcceptor = mock(IoAcceptor.class);
        IoServiceFactory mockMinaFactory = mock(IoServiceFactory.class);

        doReturn(mockAcceptor).when(mockMinaFactory).createAcceptor(any(IoHandler.class));
        doReturn(mockAcceptor).when(mockMinaFactory).createAcceptor(any(IoHandler.class));
        doNothing().when(mockAcceptor).bind(MOCK_ADDRESS);
        instance = new NetconfCallHomeServer(
            mockSshClient, mockCallHomeAuthProv, mockFactory, MOCK_ADDRESS, mockStatusRecorder, mockMinaFactory);
        // when
        instance.bind();
        // then
        verify(mockSshClient, times(1)).start();
        verify(mockAcceptor, times(1)).bind(MOCK_ADDRESS);
    }
}
