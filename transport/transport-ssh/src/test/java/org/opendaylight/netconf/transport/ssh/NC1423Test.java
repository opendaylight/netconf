/*
 * Copyright (c) 2024 PANTHEON.tech s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.EventLoopGroup;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.opendaylight.netconf.shaded.sshd.common.io.IoSession;
import org.opendaylight.netconf.shaded.sshd.common.session.Session;
import org.opendaylight.netconf.shaded.sshd.common.session.SessionListener;
import org.opendaylight.netconf.shaded.sshd.netty.NettyIoServiceFactoryFactory;
import org.opendaylight.netconf.transport.tcp.NettyTransportSupport;

public class NC1423Test extends AbstractClientServerTest {
    private final EventLoopGroup group = NettyTransportSupport.newEventLoopGroup("SessionFailure", 0);
    private final NettyIoServiceFactoryFactory serviceFactory = new NettyIoServiceFactoryFactory(group);
    private final ServerBootstrap serverBootstrap = NettyTransportSupport.newServerBootstrap().group(group);
    private final Bootstrap clientBootstrap = NettyTransportSupport.newBootstrap().group(group);

    private TransportSshClient transportSshSpy;

    @BeforeEach
    public void beforeMethod() throws Exception {
        final var clientIdentity = buildClientIdentityWithPassword(getUsername(), PASSWORD);
        final var clientAuth = buildClientAuthWithPassword(getUsernameAndUpdate(), "$0$" + PASSWORD);
        final var serverIdentity = buildServerIdentityWithKeyPair(generateKeyPairWithCertificate(RSA));
        when(sshClientConfig.getClientIdentity()).thenReturn(clientIdentity);
        when(sshClientConfig.getServerAuthentication()).thenReturn(null);
        when(sshServerConfig.getServerIdentity()).thenReturn(serverIdentity);
        when(sshServerConfig.getClientAuthentication()).thenReturn(clientAuth);

        final var transportSshClient = new TransportSshClient.Builder(serviceFactory, group)
            .transportParams(sshClientConfig.getTransportParams())
            .keepAlives(sshClientConfig.getKeepalives())
            .clientIdentity(sshClientConfig.getClientIdentity())
            .serverAuthentication(sshClientConfig.getServerAuthentication())
            .configurator(null)
            .buildChecked();
        transportSshSpy = spy(transportSshClient);
    }

    @Test
    void testAuthenticationSessionEventFailure() throws Exception {
        // Prepares a use case where Authenticated sessionEvent is called and fails to register a channel
        // into the ConnectionService.
        final var sessionRegisterExc = new IOException("Failed to initialize and register channel");
        doAnswer(sessionListenerInv -> {
            final var sessionListenerSpy = spy(sessionListenerInv.<SessionListener>getArgument(0));
            // Capture sessionEvent on spy SessionListener and modifies its parameters to throw an IOException.
            doAnswer(sessionEventInv -> {
                final var sessionArg = sessionEventInv.<Session>getArgument(0);

                // Prepare mocked session with correct ID.
                final var sessionMock = mock(TransportClientSession.class);
                final var ioSessionMock = mock(IoSession.class);
                doReturn(sessionArg.getIoSession().getId()).when(ioSessionMock).getId();
                doReturn(ioSessionMock).when(sessionMock).getIoSession();
                // Throw exception when createSubsystemChannel method is called.
                doThrow(sessionRegisterExc).when(sessionMock).createSubsystemChannel(eq(SUBSYSTEM));

                // Replace original arguments with mock session and Authenticated Event.
                final var arguments = sessionEventInv.getArguments();
                arguments[0] = sessionMock;
                arguments[1] = Authenticated;
                // Invoke real sessionEvent method with modified parameters.
                return sessionEventInv.callRealMethod();
            }).when(sessionListenerSpy).sessionEvent(any(), any());

            // Replace original arguments with prepared spy Listener.
            final var arguments = sessionListenerInv.getArguments();
            arguments[0] = sessionListenerSpy;
            // Invoke real addSessionListener method with modified parameters.
            return sessionListenerInv.callRealMethod();
        }).when(transportSshSpy).addSessionListener(any());

        final var sshClient = SSHClient.of(SUBSYSTEM, clientListener, transportSshSpy);
        final var sshServer = SSHServer.of(serviceFactory, group, SUBSYSTEM, serverListener, sshServerConfig, null);
        try {
            // Execute connect.
            sshServer.listen(serverBootstrap, tcpServerConfig).get(2, TimeUnit.SECONDS);
            sshClient.connect(clientBootstrap, tcpClientConfig).get(2, TimeUnit.SECONDS);

            // Verify thrown IOException exception.
            final var exceptionCapture = ArgumentCaptor.forClass(IOException.class);
            verify(clientListener, timeout(200000)).onTransportChannelFailed(exceptionCapture.capture());
            final var receivedException = exceptionCapture.getValue();

            // Verify correct exception message.
            assertEquals(sessionRegisterExc, receivedException);
        } finally {
            // Close resources after test.
            sshServer.shutdown().get(20, TimeUnit.SECONDS);
            sshClient.shutdown().get(20, TimeUnit.SECONDS);
        }
    }

    @Test
    void testSessionDisconnectWhileClientProcessingConnect() throws Exception {
        // Prepares a use case where sessionDisconnect is invoked when sessionEvent is processed.
        doAnswer(sessionListenerInv -> {
            final var sessionListenerSpy = spy(sessionListenerInv.<SessionListener>getArgument(0));
            // Capture sessionEvent on spy SessionListener and invoke sessionDisconnect.
            doAnswer(sessionEventInv -> {
                final var sessionArg = sessionEventInv.<Session>getArgument(0);
                // SSH2_DISCONNECT_BY_APPLICATION == 11
                sessionListenerSpy.sessionDisconnect(sessionArg, 11, "disconnect", "lang", false);
                return null;
            }).when(sessionListenerSpy).sessionEvent(any(), any());

            // Replace original arguments with prepared spy Listener.
            final var arguments = sessionListenerInv.getArguments();
            arguments[0] = sessionListenerSpy;
            // Invoke real addSessionListener method with modified parameters.
            return sessionListenerInv.callRealMethod();
        }).when(transportSshSpy).addSessionListener(any());

        final var sshClient = SSHClient.of(SUBSYSTEM, clientListener, transportSshSpy);
        final var sshServer = SSHServer.of(serviceFactory, group, SUBSYSTEM, serverListener, sshServerConfig, null);
        try {
            // Execute connect.
            sshServer.listen(serverBootstrap, tcpServerConfig).get(2, TimeUnit.SECONDS);
            sshClient.connect(clientBootstrap, tcpClientConfig).get(2, TimeUnit.SECONDS);

            // Verify thrown IllegalStateException exception.
            final var exceptionCapture = ArgumentCaptor.forClass(IllegalStateException.class);
            verify(clientListener, timeout(2000)).onTransportChannelFailed(exceptionCapture.capture());
            final var receivedException = exceptionCapture.getValue();

            // Verify correct exception message.
            assertEquals("Session 1 disconnected: SSH2_DISCONNECT_BY_APPLICATION", receivedException.getMessage());
        } finally {
            // Close resources after test.
            sshServer.shutdown().get(20, TimeUnit.SECONDS);
            sshClient.shutdown().get(20, TimeUnit.SECONDS);
        }
    }

    @Test
    void testSessionExceptionWhileClientProcessingConnect() throws Exception {
        // Prepares a use case where sessionException is invoked when sessionEvent is processed.
        final var testException = new RuntimeException("Test session exception");
        doAnswer(sessionListenerInv -> {
            final var sessionListenerSpy = spy(sessionListenerInv.<SessionListener>getArgument(0));
            // Capture sessionEvent on spy SessionListener and invoke sessionException.
            doAnswer(sessionEventInv -> {
                sessionListenerSpy.sessionException(sessionEventInv.getArgument(0), testException);
                return null;
            }).when(sessionListenerSpy).sessionEvent(any(), any());

            // Replace original arguments with prepared spy SessionListener.
            final var arguments = sessionListenerInv.getArguments();
            arguments[0] = sessionListenerSpy;
            // Invoke real addSessionListener method with modified parameters.
            return sessionListenerInv.callRealMethod();
        }).when(transportSshSpy).addSessionListener(any());

        final var sshClient = SSHClient.of(SUBSYSTEM, clientListener, transportSshSpy);
        final var sshServer = SSHServer.of(serviceFactory, group, SUBSYSTEM, serverListener, sshServerConfig, null);
        try {
            // Execute connect.
            sshServer.listen(serverBootstrap, tcpServerConfig).get(2, TimeUnit.SECONDS);
            sshClient.connect(clientBootstrap, tcpClientConfig).get(2, TimeUnit.SECONDS);

            // Verify that RuntimeException is thrown.
            final var exceptionCapture = ArgumentCaptor.forClass(RuntimeException.class);
            verify(clientListener, timeout(2000)).onTransportChannelFailed(exceptionCapture.capture());
            final var exception = exceptionCapture.getValue();

            // Verify correct exception.
            assertEquals(testException, exception);
        } finally {
            // Close resources after test.
            sshServer.shutdown().get(2, TimeUnit.SECONDS);
            sshClient.shutdown().get(2, TimeUnit.SECONDS);
        }
    }

    @Test
    void testSessionCloseWhileClientProcessingConnect() throws Exception {
        // Prepares a use case where sessionClosed is invoked when sessionEvent is processed.
        doAnswer(sessionListenerInv -> {
            final var sessionListenerSpy = spy(sessionListenerInv.<SessionListener>getArgument(0));
            // Capture sessionEvent on spy SessionListener and invoke sessionClosed.
            doAnswer(sessionEventInv -> {
                sessionListenerSpy.sessionClosed(sessionEventInv.getArgument(0));
                return null;
            }).when(sessionListenerSpy).sessionEvent(any(), any());

            // Replace original arguments with prepared spy SessionListener.
            final var arguments = sessionListenerInv.getArguments();
            arguments[0] = sessionListenerSpy;
            // Invoke real addSessionListener method with modified parameters.
            return sessionListenerInv.callRealMethod();
        }).when(transportSshSpy).addSessionListener(any());

        final var sshClient = SSHClient.of(SUBSYSTEM, clientListener, transportSshSpy);
        final var sshServer = SSHServer.of(serviceFactory, group, SUBSYSTEM, serverListener, sshServerConfig, null);
        try {
            // Execute connect.
            sshServer.listen(serverBootstrap, tcpServerConfig).get(2, TimeUnit.SECONDS);
            sshClient.connect(clientBootstrap, tcpClientConfig).get(2, TimeUnit.SECONDS);

            // Verify that IllegalStateException is thrown.
            final var exceptionCapture = ArgumentCaptor.forClass(IllegalStateException.class);
            verify(clientListener, timeout(2000)).onTransportChannelFailed(exceptionCapture.capture());
            final var exception = exceptionCapture.getValue();

            // Verify correct exception message.
            assertEquals("Session 1 closed", exception.getMessage());
        } finally {
            // Close resources after test.
            sshServer.shutdown().get(2, TimeUnit.SECONDS);
            sshClient.shutdown().get(2, TimeUnit.SECONDS);
        }
    }
}
