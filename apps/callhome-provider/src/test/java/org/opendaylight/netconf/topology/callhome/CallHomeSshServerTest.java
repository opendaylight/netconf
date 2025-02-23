/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.callhome;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.opendaylight.netconf.api.TransportConstants.SSH_SUBSYSTEM;

import com.google.common.util.concurrent.SettableFuture;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.spec.RSAKeyGenParameterSpec;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.eclipse.jdt.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.netconf.client.NetconfClientSession;
import org.opendaylight.netconf.client.NetconfClientSessionListener;
import org.opendaylight.netconf.client.NetconfClientSessionNegotiatorFactory;
import org.opendaylight.netconf.common.di.DefaultNetconfTimer;
import org.opendaylight.netconf.server.NetconfServerSession;
import org.opendaylight.netconf.server.NetconfServerSessionNegotiatorFactory;
import org.opendaylight.netconf.server.ServerTransportInitializer;
import org.opendaylight.netconf.server.api.monitoring.NetconfMonitoringService;
import org.opendaylight.netconf.server.api.monitoring.SessionListener;
import org.opendaylight.netconf.server.impl.DefaultSessionIdProvider;
import org.opendaylight.netconf.server.osgi.AggregatedNetconfOperationServiceFactory;
import org.opendaylight.netconf.shaded.sshd.client.session.ClientSession;
import org.opendaylight.netconf.shaded.sshd.common.keyprovider.KeyPairProvider;
import org.opendaylight.netconf.shaded.sshd.server.auth.password.PasswordAuthenticator;
import org.opendaylight.netconf.shaded.sshd.server.auth.password.UserAuthPasswordFactory;
import org.opendaylight.netconf.shaded.sshd.server.auth.pubkey.PublickeyAuthenticator;
import org.opendaylight.netconf.shaded.sshd.server.auth.pubkey.UserAuthPublicKeyFactory;
import org.opendaylight.netconf.topology.callhome.CallHomeSshAuthSettings.DefaultAuthSettings;
import org.opendaylight.netconf.transport.ssh.SSHServer;
import org.opendaylight.netconf.transport.ssh.SSHTransportStackFactory;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IetfInetUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.client.rev240814.netconf.client.initiate.stack.grouping.transport.ssh.ssh.TcpClientParametersBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.Capabilities;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.monitoring.rev101004.netconf.state.CapabilitiesBuilder;
import org.opendaylight.yangtools.yang.common.Uint16;

@ExtendWith(MockitoExtension.class)
class CallHomeSshServerTest {
    private static final long TIMEOUT = 5000L;
    private static final Capabilities EMPTY_CAPABILITIES = new CapabilitiesBuilder().setCapability(Set.of()).build();
    private static final String USERNAME = "username";
    private static final String PASSWORD = "pa$$W0rd";

    @Mock
    private NetconfMonitoringService monitoringService;
    @Mock
    private SessionListener serverSessionListener;
    @Mock
    private NetconfClientSessionListener clientSessionListener;
    @Mock
    private CallHomeStatusRecorder statusRecorder;

    @Test
    void integrationTest() throws Exception {

        // key pairs
        final var serverKeys = generateKeyPair();
        final var client1Keys = generateKeyPair();
        final var client2Keys = generateKeyPair();
        final var client3Keys = generateKeyPair();
        final var client4Keys = generateKeyPair();

        // Auth provider
        final var authProvider = (CallHomeSshAuthProvider) (remoteAddress, publicKey) -> {
            // identify client 2 by password (invalid)
            if (client2Keys.getPublic().equals(publicKey)) {
                return new DefaultAuthSettings("client2-id", USERNAME, Set.of("invalid-password"), null);
            }
            // identify client 3 by password (valid)
            if (client3Keys.getPublic().equals(publicKey)) {
                return new DefaultAuthSettings("client3-id", USERNAME, Set.of(PASSWORD), null);
            }
            // identify client 4 by public key
            if (client4Keys.getPublic().equals(publicKey)) {
                return new DefaultAuthSettings("client4-id", USERNAME, null, Set.of(serverKeys));
            }
            // client 1 is not identified
            return null;
        };
        // client side authenticators
        final PasswordAuthenticator passwordAuthenticator =
            (username, password, session) -> USERNAME.equals(username) && PASSWORD.equals(password);
        final PublickeyAuthenticator publicKeyAuthenticator =
            (username, publicKey, session) -> serverKeys.getPublic().equals(publicKey);

        // Netconf layer for clients
        doReturn(serverSessionListener).when(monitoringService).getSessionListener();
        doReturn(EMPTY_CAPABILITIES).when(monitoringService).getCapabilities();

        final var timer = new DefaultNetconfTimer();

        final var negotiatorFactory = NetconfServerSessionNegotiatorFactory.builder()
            .setTimer(timer)
            .setAggregatedOpService(new AggregatedNetconfOperationServiceFactory())
            .setIdProvider(new DefaultSessionIdProvider())
            .setConnectionTimeoutMillis(TIMEOUT)
            .setMonitoringService(monitoringService)
            .build();
        final var netconfTransportListener = new ServerTransportInitializer(negotiatorFactory);

        // tcp layer for clients
        final var sshTransportFactory = new SSHTransportStackFactory("call-home-test-client", 0);
        final var serverPort = serverPort();
        final var tcpConnectParams = new TcpClientParametersBuilder()
            .setRemoteAddress(new Host(IetfInetUtil.ipAddressFor(InetAddress.getLoopbackAddress())))
            .setRemotePort(new PortNumber(Uint16.valueOf(serverPort))).build();

        // Session context manager
        final var contextManager = new CallHomeSshSessionContextManager() {
            // inject netconf session listener
            @Override
            public CallHomeSshSessionContext createContext(final String id, final ClientSession clientSession) {
                return new CallHomeSshSessionContext(id, clientSession.getRemoteAddress(), clientSession,
                    clientSessionListener, SettableFuture.create());
            }
        };

        // start Call-Home server
        final var server = CallHomeSshServer.builder()
            .withPort(serverPort)
            .withAuthProvider(authProvider)
            .withSessionContextManager(contextManager)
            .withStatusRecorder(statusRecorder)
            .withNegotiationFactory(new NetconfClientSessionNegotiatorFactory(timer, Optional.empty(), TIMEOUT,
                NetconfClientSessionNegotiatorFactory.DEFAULT_CLIENT_CAPABILITIES))
            .build();

        SSHServer client1 = null;
        SSHServer client2 = null;
        SSHServer client3 = null;
        SSHServer client4 = null;

        try {
            // client 1: rejected due to public key is not identified
            client1 = sshTransportFactory.connectServer(SSH_SUBSYSTEM, netconfTransportListener, tcpConnectParams,
                null, factoryMgr -> {
                    factoryMgr.setKeyPairProvider(KeyPairProvider.wrap(client1Keys));
                    factoryMgr.setPasswordAuthenticator(passwordAuthenticator);
                    factoryMgr.setUserAuthFactories(List.of(new UserAuthPasswordFactory()));
                }).get(TIMEOUT, TimeUnit.MILLISECONDS);
            // verify unknown key reported
            verify(statusRecorder, timeout(TIMEOUT).times(1))
                .reportUnknown(any(InetSocketAddress.class), eq(client1Keys.getPublic()));

            // client 2: rejected due to auth failure (wrong password)
            client2 = sshTransportFactory.connectServer(SSH_SUBSYSTEM, netconfTransportListener, tcpConnectParams,
                null, factoryMgr -> {
                    factoryMgr.setKeyPairProvider(KeyPairProvider.wrap(client2Keys));
                    factoryMgr.setPasswordAuthenticator(passwordAuthenticator);
                    factoryMgr.setUserAuthFactories(List.of(new UserAuthPasswordFactory()));
                }).get(TIMEOUT, TimeUnit.MILLISECONDS);
            // verify auth failure reported for known key
            verify(statusRecorder, timeout(TIMEOUT).times(1)).reportFailedAuth("client2-id");

            // client 3: success with password auth
            client3 = sshTransportFactory.connectServer(SSH_SUBSYSTEM, netconfTransportListener, tcpConnectParams,
                null, factoryMgr -> {
                    factoryMgr.setKeyPairProvider(KeyPairProvider.wrap(client3Keys));
                    factoryMgr.setPasswordAuthenticator(passwordAuthenticator);
                    factoryMgr.setUserAuthFactories(List.of(new UserAuthPasswordFactory()));
                }).get(TIMEOUT, TimeUnit.MILLISECONDS);
            // verify netconf sessions established
            verify(clientSessionListener, timeout(TIMEOUT).times(1)).onSessionUp(any(NetconfClientSession.class));
            verify(serverSessionListener, timeout(TIMEOUT).times(1)).onSessionUp(any(NetconfServerSession.class));
            verify(statusRecorder, times(1)).reportSuccess("client3-id");

            // client 4: success with public key auth
            client4 = sshTransportFactory.connectServer(SSH_SUBSYSTEM, netconfTransportListener, tcpConnectParams,
                null, factoryMgr -> {
                    factoryMgr.setKeyPairProvider(KeyPairProvider.wrap(client4Keys));
                    final var pkFactory = new UserAuthPublicKeyFactory();
                    pkFactory.setSignatureFactories(factoryMgr.getSignatureFactories());
                    factoryMgr.setPublickeyAuthenticator(publicKeyAuthenticator);
                    factoryMgr.setUserAuthFactories(List.of(pkFactory));
                }).get(TIMEOUT, TimeUnit.MILLISECONDS);
            // verify netconf sessions established
            verify(clientSessionListener, timeout(TIMEOUT).times(2)).onSessionUp(any(NetconfClientSession.class));
            verify(serverSessionListener, timeout(TIMEOUT).times(2)).onSessionUp(any(NetconfServerSession.class));
            verify(statusRecorder, times(1)).reportSuccess("client4-id");

        } finally {
            server.close();
            shutdownClient(client1);
            shutdownClient(client2);
            shutdownClient(client3);
            shutdownClient(client4);
            timer.close();
        }

        // verify disconnect reported
        verify(serverSessionListener, timeout(TIMEOUT).times(2)).onSessionDown(any(NetconfServerSession.class));
        verify(clientSessionListener, timeout(TIMEOUT).times(2))
            .onSessionDown(any(NetconfClientSession.class), nullable(Exception.class));
        verify(statusRecorder, times(1)).reportDisconnected("client3-id");
        verify(statusRecorder, times(1)).reportDisconnected("client4-id");
    }

    private static void shutdownClient(final @Nullable SSHServer client) throws Exception {
        if (client != null) {
            client.shutdown().get(TIMEOUT, TimeUnit.MILLISECONDS);
        }
    }

    private static int serverPort() throws Exception {
        try (var socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static KeyPair generateKeyPair() throws Exception {
        final var keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(new RSAKeyGenParameterSpec(2048, RSAKeyGenParameterSpec.F4), new SecureRandom());
        return keyPairGenerator.generateKeyPair();
    }
}
