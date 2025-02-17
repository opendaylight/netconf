/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opendaylight.netconf.shaded.sshd.common.session.SessionListener.Event.Authenticated;
import static org.opendaylight.netconf.transport.ssh.TestUtils.buildClientAuthWithPassword;
import static org.opendaylight.netconf.transport.ssh.TestUtils.buildClientIdentityWithPassword;
import static org.opendaylight.netconf.transport.ssh.TestUtils.buildServerIdentityWithKeyPair;
import static org.opendaylight.netconf.transport.ssh.TestUtils.generateKeyPairWithCertificate;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.opendaylight.netconf.shaded.sshd.common.io.IoSession;
import org.opendaylight.netconf.shaded.sshd.common.session.Session;
import org.opendaylight.netconf.shaded.sshd.common.session.SessionListener;
import org.opendaylight.netconf.shaded.sshd.netty.NettyIoServiceFactoryFactory;
import org.opendaylight.netconf.transport.api.TransportChannel;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.tcp.NettyTransportSupport;

public class NC1423Test extends AbstractClientServerTest {

    @Test
    void testAuthenticationSessionEventFailure() throws Exception {
        // Prepare environment.
        final var clientIdentity = buildClientIdentityWithPassword(getUsername(), PASSWORD);
        final var clientAuth = buildClientAuthWithPassword(getUsernameAndUpdate(), "$0$" + PASSWORD);
        final var serverIdentity = buildServerIdentityWithKeyPair(generateKeyPairWithCertificate(RSA));
        when(sshClientConfig.getClientIdentity()).thenReturn(clientIdentity);
        when(sshClientConfig.getServerAuthentication()).thenReturn(null);
        when(sshServerConfig.getServerIdentity()).thenReturn(serverIdentity);
        when(sshServerConfig.getClientAuthentication()).thenReturn(clientAuth);

        // Creates a spy TransportSshClient to help us access the SessionListener class.
        final var group = NettyTransportSupport.newEventLoopGroup("SessionFailure", 0);
        final var serviceFactory = new NettyIoServiceFactoryFactory(group);

        final var transportSshClient = new TransportSshClient.Builder(serviceFactory, group)
            .transportParams(sshClientConfig.getTransportParams())
            .keepAlives(sshClientConfig.getKeepalives())
            .clientIdentity(sshClientConfig.getClientIdentity())
            .serverAuthentication(sshClientConfig.getServerAuthentication())
            .configurator(null)
            .buildChecked();
        final var transportSshSpy = spy(transportSshClient);

        // Prepares a use case where Authenticated sessionEvent is called and fails to register a channel
        // into the ConnectionService.
        final var sessionRegisterExc = new IOException("Failed to initialize and register channel");
        doAnswer(sessionListenerINV -> {
            final var sessionListenerSpy = spy(sessionListenerINV.<SessionListener>getArgument(0));
            // Capture sessionEvent on spy SessionListener and modifies its parameters to throw an IOException.
            doAnswer(sessionEventINV -> {
                final var sessionArg = sessionEventINV.<Session>getArgument(0);
                // Prepare mocked session with correct ID.
                final var sessionMock = mock(TransportClientSession.class);
                final var ioSessionMock = mock(IoSession.class);
                doReturn(sessionArg.getIoSession().getId()).when(ioSessionMock).getId();
                doReturn(ioSessionMock).when(sessionMock).getIoSession();
                // Throw exception when createSubsystemChannel method is called.
                doThrow(sessionRegisterExc).when(sessionMock)
                    .createSubsystemChannel(eq(SUBSYSTEM));
                // Replace original arguments with mock session and Authenticated Event.
                final var arguments = sessionEventINV.getArguments();
                arguments[0] = sessionMock;
                arguments[1] = Authenticated;
                // Invoke real sessionEvent method with modified parameters.
                return sessionEventINV.callRealMethod();
            }).when(sessionListenerSpy).sessionEvent(any(), any());

            // Replace original arguments with prepared spy Listener.
            final var arguments = sessionListenerINV.getArguments();
            arguments[0] = sessionListenerSpy;
            // Invoke real addSessionListener method with modified parameters.
            return sessionListenerINV.callRealMethod();
        }).when(transportSshSpy).addSessionListener(any());

        // Create connection with client and server.
        final var sshClient = SSHClient.of(SUBSYSTEM, clientListener, transportSshSpy);
        final var server = FACTORY.listenServer(SUBSYSTEM, serverListener, tcpServerConfig, sshServerConfig)
            .get(2, TimeUnit.SECONDS);

        try {
            final var bootstrap = NettyTransportSupport.newBootstrap().group(group);
            sshClient.connect(bootstrap, tcpClientConfig).get(2, TimeUnit.SECONDS);

            // Verify thrown IOException exception.
            final var exceptionCapture = ArgumentCaptor.forClass(IOException.class);
            verify(clientListener, timeout(200000)).onTransportChannelFailed(exceptionCapture.capture());
            final var receivedException = exceptionCapture.getValue();

            // Verify correct exception message.
            assertEquals(sessionRegisterExc, receivedException);
        } finally {
            // Close resources after test.
            server.shutdown().get(20, TimeUnit.SECONDS);
            sshClient.shutdown().get(20, TimeUnit.SECONDS);
        }
    }

    @Test
    void testSessionException() throws Exception {
        // Prepare environment.
        final var clientIdentity = buildClientIdentityWithPassword(getUsername(), PASSWORD);
        final var clientAuth = buildClientAuthWithPassword(getUsernameAndUpdate(), "$0$" + PASSWORD);
        final var serverIdentity = buildServerIdentityWithKeyPair(generateKeyPairWithCertificate(RSA));
        when(sshClientConfig.getClientIdentity()).thenReturn(clientIdentity);
        when(sshClientConfig.getServerAuthentication()).thenReturn(null);
        when(sshServerConfig.getServerIdentity()).thenReturn(serverIdentity);
        when(sshServerConfig.getClientAuthentication()).thenReturn(clientAuth);

        final var group = NettyTransportSupport.newEventLoopGroup("SessionFailure", 0);
        final var serviceFactory = new NettyIoServiceFactoryFactory(group);
        final var sshClient = SSHClient.of(serviceFactory, group, SUBSYSTEM, clientListener, sshClientConfig, null);
        final var spyClient = spy(sshClient);

        // Set up the behaviour with spied TransportChannelListener which automatically close session before calling
        // onTransportChannelEstablished method.
        doAnswer(clientInv -> {
            final var channelListener = clientInv.<TransportChannelListener<TransportChannel>>getArgument(2);
            // Create a spy of the original listener.
            final var spiedListener = spy(channelListener);
            doAnswer(listenerInv -> {
                final var argumentChannel = listenerInv.<TransportChannel>getArgument(0);

                // Call the close method on the channel parameter.
                clientListener.onTransportChannelFailed(new RuntimeException("Exception in the session"));
                // Call the real method afterward.
                return listenerInv.callRealMethod();
            }).when(spiedListener).onTransportChannelEstablished(any(TransportChannel.class));

            // Call the real method using the spied listener.
            return sshClient.connect(clientInv.getArgument(0), clientInv.getArgument(1), spiedListener);
        }).when(spyClient).connect(any(), any(), any());

        final var sshServerFuture = FACTORY.listenServer(SUBSYSTEM, serverListener, tcpServerConfig, sshServerConfig);
        final var server = sshServerFuture.get(2, TimeUnit.SECONDS);
        try {
            // Execute connect on prepared spyClient.
            final var bootstrap = NettyTransportSupport.newBootstrap().group(group);
            final var clientConnect = spyClient.connect(bootstrap, tcpClientConfig).get(2, TimeUnit.SECONDS);
            assertNotNull(clientConnect);

            // Verify that IllegalStateException is thrown.
            final var exceptionCapture = ArgumentCaptor.forClass(RuntimeException.class);
            verify(clientListener, timeout(2000)).onTransportChannelFailed(exceptionCapture.capture());
            final var exception = exceptionCapture.getValue();

            // Verify correct exception message.
            assertEquals("Exception in the session", exception.getMessage());
        } finally {
            // Close resources after test.
            server.shutdown().get(2, TimeUnit.SECONDS);
            sshClient.shutdown().get(2, TimeUnit.SECONDS);
        }
    }

    @Test
    void testSessionCloseFailure() throws Exception {
        // Prepare environment.
        final var clientIdentity = buildClientIdentityWithPassword(getUsername(), PASSWORD);
        final var clientAuth = buildClientAuthWithPassword(getUsernameAndUpdate(), "$0$" + PASSWORD);
        final var serverIdentity = buildServerIdentityWithKeyPair(generateKeyPairWithCertificate(RSA));
        when(sshClientConfig.getClientIdentity()).thenReturn(clientIdentity);
        when(sshClientConfig.getServerAuthentication()).thenReturn(null);
        when(sshServerConfig.getServerIdentity()).thenReturn(serverIdentity);
        when(sshServerConfig.getClientAuthentication()).thenReturn(clientAuth);

        final var group = NettyTransportSupport.newEventLoopGroup("SessionFailure", 0);
        final var serviceFactory = new NettyIoServiceFactoryFactory(group);
        final var sshClient = SSHClient.of(serviceFactory, group, SUBSYSTEM, clientListener, sshClientConfig, null);
        final var spyClient = spy(sshClient);

        // Set up the behaviour with spied TransportChannelListener which automatically close session before calling
        // onTransportChannelEstablished method.
        doAnswer(clientInv -> {
            final var channelListener = clientInv.<TransportChannelListener<TransportChannel>>getArgument(2);
            // Create a spy of the original listener.
            final var spiedListener = spy(channelListener);
            doAnswer(listenerInv -> {
                final var argumentChannel = listenerInv.<TransportChannel>getArgument(0);

                // Call the close method on the channel parameter.
                argumentChannel.channel().close();

                // Call the real method afterward.
                return listenerInv.callRealMethod();
            }).when(spiedListener).onTransportChannelEstablished(any(TransportChannel.class));

            // Call the real method using the spied listener.
            return sshClient.connect(clientInv.getArgument(0), clientInv.getArgument(1), spiedListener);
        }).when(spyClient).connect(any(), any(), any());

        final var sshServerFuture = FACTORY.listenServer(SUBSYSTEM, serverListener, tcpServerConfig, sshServerConfig);
        final var server = sshServerFuture.get(2, TimeUnit.SECONDS);
        try {
            // Execute connect on prepared spyClient.
            final var bootstrap = NettyTransportSupport.newBootstrap().group(group);
            final var clientConnect = spyClient.connect(bootstrap, tcpClientConfig).get(2, TimeUnit.SECONDS);
            assertNotNull(clientConnect);

            // Verify that IllegalStateException is thrown.
            final var exceptionCapture = ArgumentCaptor.forClass(IllegalStateException.class);
            verify(clientListener, timeout(2000)).onTransportChannelFailed(exceptionCapture.capture());
            final var exception = exceptionCapture.getValue();

            // Verify correct exception message.
            assertEquals("Session 1 closed", exception.getMessage());
        } finally {
            // Close resources after test.
            server.shutdown().get(2, TimeUnit.SECONDS);
            sshClient.shutdown().get(2, TimeUnit.SECONDS);
        }
    }

    @Test
    void testCallHomeSessionCloseFailure() throws Exception {
        // Prepare environment.
        final var clientIdentity = buildClientIdentityWithPassword(getUsername(), PASSWORD);
        final var clientAuth = buildClientAuthWithPassword(getUsernameAndUpdate(), "$0$" + PASSWORD);
        final var serverIdentity = buildServerIdentityWithKeyPair(generateKeyPairWithCertificate(RSA));
        when(sshClientConfig.getClientIdentity()).thenReturn(clientIdentity);
        when(sshClientConfig.getServerAuthentication()).thenReturn(null);
        when(sshServerConfig.getServerIdentity()).thenReturn(serverIdentity);
        when(sshServerConfig.getClientAuthentication()).thenReturn(clientAuth);

        final var group = NettyTransportSupport.newEventLoopGroup("SessionFailure", 0);
        final var serviceFactory = new NettyIoServiceFactoryFactory(group);
        final var sshServer = SSHServer.of(serviceFactory, group, SUBSYSTEM, serverListener, sshServerConfig, null);
        final var spyServer = spy(sshServer);

        // Set up the behaviour with spied TransportChannelListener which automatically close session before calling
        // onTransportChannelEstablished method.
        doAnswer(serverInv -> {
            final var channelListener = serverInv.<TransportChannelListener<TransportChannel>>getArgument(2);

            // Create a spy of the original listener.
            final var spiedListener = spy(channelListener);
            doAnswer(listenerInv -> {
                final var argumentChannel = listenerInv.<TransportChannel>getArgument(0);

                // Call the close method on the channel parameter.
                argumentChannel.channel().close();

                // Call the real method afterward.
                return listenerInv.callRealMethod();
            }).when(spiedListener).onTransportChannelEstablished(any(TransportChannel.class));

            // Call the real method using the spied listener.
            return sshServer.connect(serverInv.getArgument(0), serverInv.getArgument(1), spiedListener);
        }).when(spyServer).connect(any(), any(), any());

        // Prepare call-home connection.
        final var client = FACTORY.listenClient(SUBSYSTEM, clientListener, tcpServerConfig, sshClientConfig,
            clientConfigurator(getUsername())).get(2, TimeUnit.SECONDS);
        try {
            final var bootstrap = NettyTransportSupport.newBootstrap().group(group);
            final var serverConnect = spyServer.connect(bootstrap, tcpClientConfig).get();
            assertNotNull(serverConnect);

            // Verify that IllegalStateException is thrown.
            final var exceptionCapture = ArgumentCaptor.forClass(IllegalStateException.class);
            verify(serverListener, timeout(2000)).onTransportChannelFailed(exceptionCapture.capture());
            final var exception = exceptionCapture.getValue();

            // Verify correct exception message.
            assertEquals("Session 1 closed", exception.getMessage());
        } finally {
            // Close resources after test.
            client.shutdown().get(2, TimeUnit.SECONDS);
            sshServer.shutdown().get(2, TimeUnit.SECONDS);
        }
    }
}
