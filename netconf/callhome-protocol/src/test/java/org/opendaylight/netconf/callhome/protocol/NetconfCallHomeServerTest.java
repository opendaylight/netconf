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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

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
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class NetconfCallHomeServerTest {
    private SshClient mockSshClient;
    private CallHomeAuthorizationProvider mockCallHomeAuthProv;
    private CallHomeAuthorization mockAuth;
    private CallHomeSessionContext.Factory mockFactory;
    private InetSocketAddress mockAddress;
    private ClientSession mockSession;
    private StatusRecorder mockStatusRecorder;

    private NetconfCallHomeServer instance;

    @Before
    public void setup() {
        mockSshClient = Mockito.spy(SshClient.setUpDefaultClient());
        mockCallHomeAuthProv = mock(CallHomeAuthorizationProvider.class);
        mockAuth = mock(CallHomeAuthorization.class);
        mockFactory = mock(CallHomeSessionContext.Factory.class);
        mockAddress = InetSocketAddress.createUnresolved("1.2.3.4", 123);
        mockSession = mock(ClientSession.class);
        mockStatusRecorder = mock(StatusRecorder.class);

        Map<String, String> props = new HashMap<>();
        props.put("nio-workers", "1");
        Mockito.doReturn(props).when(mockSshClient).getProperties();
        Mockito.doReturn("test").when(mockSession).toString();
        instance = new NetconfCallHomeServer(
            mockSshClient, mockCallHomeAuthProv, mockFactory, mockAddress, mockStatusRecorder);
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
            Mockito.doReturn(null).when(mockAuthFuture).addListener(any(SshFutureListener.class));
            CallHomeSessionContext mockContext = mock(CallHomeSessionContext.class);
            Mockito.doNothing().when(mockContext).openNetconfChannel();
            Mockito.doReturn(mockContext).when(mockSession).getAttribute(any(Session.AttributeKey.class));
            SessionListener listener = instance.createSessionListener();
            Mockito.doReturn(mockAuthFuture).when(mockContext).authorize();
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
        Mockito.doReturn("testAddr").when(mockSocketAddr).toString();
        PublicKey mockPublicKey = mock(PublicKey.class);

        Mockito.doReturn("test").when(mockAuth).toString();
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

    static class TestableCallHomeServer extends NetconfCallHomeServer {
        static IoServiceFactory minaServiceFactory;

        static SshClient factoryHook(final SshClient client, final IoServiceFactory minaFactory) {
            minaServiceFactory = minaFactory;
            return client;
        }

        SshClient client;

        TestableCallHomeServer(final SshClient sshClient, final CallHomeAuthorizationProvider authProvider,
                               final CallHomeSessionContext.Factory factory, final InetSocketAddress socketAddress,
                               final IoServiceFactory minaFactory, final StatusRecorder recorder) {
            super(factoryHook(sshClient, minaFactory), authProvider, factory, socketAddress, recorder);
            client = sshClient;
        }

        @Override
        protected IoServiceFactory createMinaServiceFactory(final SshClient sshClient) {
            return minaServiceFactory;
        }
    }

    @Test
    public void bindShouldStartTheClientAndBindTheAddress() throws IOException {
        // given
        IoAcceptor mockAcceptor = mock(IoAcceptor.class);
        IoServiceFactory mockMinaFactory = mock(IoServiceFactory.class);

        Mockito.doReturn(mockAcceptor).when(mockMinaFactory).createAcceptor(any(IoHandler.class));
        Mockito.doReturn(mockAcceptor).when(mockMinaFactory).createAcceptor(any(IoHandler.class));
        Mockito.doNothing().when(mockAcceptor).bind(mockAddress);
        instance = new TestableCallHomeServer(
            mockSshClient, mockCallHomeAuthProv, mockFactory, mockAddress, mockMinaFactory, mockStatusRecorder);
        // when
        instance.bind();
        // then
        verify(mockSshClient, times(1)).start();
        verify(mockAcceptor, times(1)).bind(mockAddress);
    }
}
