/*
 * Copyright (c) 2023 PANTHEON.tech s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.server.ssh;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.security.PublicKey;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.api.TransportConstants;
import org.opendaylight.netconf.callhome.server.CallHomeStatusRecorder;
import org.opendaylight.netconf.callhome.server.CallHomeTransportChannelListener;
import org.opendaylight.netconf.client.NetconfClientSessionNegotiatorFactory;
import org.opendaylight.netconf.shaded.sshd.client.auth.password.UserAuthPasswordFactory;
import org.opendaylight.netconf.shaded.sshd.client.auth.pubkey.UserAuthPublicKeyFactory;
import org.opendaylight.netconf.shaded.sshd.client.session.ClientSession;
import org.opendaylight.netconf.shaded.sshd.common.session.Session;
import org.opendaylight.netconf.shaded.sshd.common.session.SessionListener;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.netconf.transport.ssh.ClientFactoryManagerConfigurator;
import org.opendaylight.netconf.transport.ssh.SSHClient;
import org.opendaylight.netconf.transport.ssh.SSHTransportStackFactory;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IetfInetUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.client.rev231228.netconf.client.initiate.stack.grouping.transport.ssh.ssh.SshClientParametersBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.client.rev231228.netconf.client.listen.stack.grouping.transport.ssh.ssh.TcpServerParametersBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.client.rev231228.ssh.client.grouping.ClientIdentityBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.server.rev231228.TcpServerGrouping;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CallHomeSshServer implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(CallHomeSshServer.class);
    private static final long DEFAULT_TIMEOUT_MILLIS = 10000L;
    private static final int DEFAULT_PORT = 4334;

    private final CallHomeSshAuthProvider authProvider;
    private final CallHomeStatusRecorder statusRecorder;
    private final CallHomeSshSessionContextManager contextManager;
    private final SSHClient client;

    @VisibleForTesting
    CallHomeSshServer(final TcpServerGrouping tcpServerParams,
            final SSHTransportStackFactory transportStackFactory,
            final NetconfClientSessionNegotiatorFactory negotiatorFactory,
            final CallHomeSshSessionContextManager contextManager,
            final CallHomeSshAuthProvider authProvider,
            final CallHomeStatusRecorder statusRecorder) {
        this.authProvider = requireNonNull(authProvider);
        this.statusRecorder = requireNonNull(statusRecorder);
        this.contextManager = requireNonNull(contextManager);

        // netconf layer
        final var transportChannelListener =
            new CallHomeTransportChannelListener(negotiatorFactory, contextManager, statusRecorder);

        // SSH transport layer configuration
        // NB actual username will be assigned dynamically but predefined one is required for transport initialization
        final var sshClientParams = new SshClientParametersBuilder().setClientIdentity(
            new ClientIdentityBuilder().setUsername("ignored").build()).build();
        final ClientFactoryManagerConfigurator configurator = factoryMgr -> {
            factoryMgr.setServerKeyVerifier(this::verifyServerKey);
            factoryMgr.addSessionListener(createSessionListener());
            // supported auth factories
            factoryMgr.setUserAuthFactories(List.of(new UserAuthPasswordFactory(), new UserAuthPublicKeyFactory()));
        };
        try {
            client = transportStackFactory.listenClient(TransportConstants.SSH_SUBSYSTEM, transportChannelListener,
                tcpServerParams, sshClientParams, configurator).get(DEFAULT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (UnsupportedConfigurationException | InterruptedException | ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Could not start SSH Call-Home server", e);
        }
    }

    private SessionListener createSessionListener() {
        return new SessionListener() {
            @Override
            public void sessionClosed(final Session session) {
                if (session instanceof ClientSession clientSession) {
                    final var context = contextManager.findBySshSession(clientSession);
                    if (context != null) {
                        contextManager.remove(context.id());
                        if (!clientSession.isAuthenticated()) {
                            // threat unauthenticated session closure as authentication failure
                            // in case there was context object created for the session
                            statusRecorder.reportFailedAuth(context.id());
                        } else if (context.settableFuture().isDone()) {
                            // disconnected after netconf session established
                            statusRecorder.reportDisconnected(context.id());
                        }
                    }
                }
            }
        };
    }

    private boolean verifyServerKey(final ClientSession clientSession, final SocketAddress remoteAddress,
            final PublicKey serverKey) {
        final CallHomeSshAuthSettings authSettings = authProvider.provideAuth(remoteAddress, serverKey);
        if (authSettings == null) {
            // no auth for server key
            statusRecorder.reportUnknown(remoteAddress, serverKey);
            LOG.info("No auth settings found. Connection from {} rejected.", remoteAddress);
            return false;
        }
        if (contextManager.exists(authSettings.id())) {
            LOG.info("Session context with same id {} already exists. Connection from {} rejected.",
                authSettings.id(), remoteAddress);
            return false;
        }
        final var context = contextManager.createContext(authSettings.id(), clientSession);
        if (context == null) {
            // if there is an issue creating context then the cause expected to be
            // logged within overridden createContext() method
            return false;
        }
        contextManager.register(context);

        // Session context is ok, apply auth settings to current session
        authSettings.applyTo(clientSession);
        LOG.debug("Session context is created for SSH session: {}", context);
        return true;
    }

    @Override
    public void close() throws Exception {
        contextManager.close();
        client.shutdown().get();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private InetAddress address;
        private int port = DEFAULT_PORT;
        private SSHTransportStackFactory transportStackFactory;
        private NetconfClientSessionNegotiatorFactory negotiationFactory;
        private CallHomeSshAuthProvider authProvider;
        private CallHomeSshSessionContextManager contextManager;
        private CallHomeStatusRecorder statusRecorder;

        private Builder() {
            // on purpose
        }

        public @NonNull CallHomeSshServer build() {
            return new CallHomeSshServer(
                toServerParams(address, port),
                transportStackFactory == null ? defaultTransportStackFactory() : transportStackFactory,
                negotiationFactory,
                contextManager == null ? new CallHomeSshSessionContextManager() : contextManager,
                authProvider, statusRecorder);
        }

        public Builder withAuthProvider(final CallHomeSshAuthProvider newAuthProvider) {
            authProvider = newAuthProvider;
            return this;
        }

        public Builder withSessionContextManager(final CallHomeSshSessionContextManager newContextManager) {
            contextManager = newContextManager;
            return this;
        }

        public Builder withStatusRecorder(final CallHomeStatusRecorder newStatusRecorder) {
            statusRecorder = newStatusRecorder;
            return this;
        }

        public Builder withAddress(final InetAddress newAddress) {
            address = newAddress;
            return this;
        }

        public Builder withPort(final int newPort) {
            port = newPort;
            return this;
        }

        public Builder withTransportStackFactory(final SSHTransportStackFactory newTransportStackFactory) {
            transportStackFactory = newTransportStackFactory;
            return this;
        }

        public Builder withNegotiationFactory(final NetconfClientSessionNegotiatorFactory newNegotiationFactory) {
            negotiationFactory = newNegotiationFactory;
            return this;
        }
    }

    private static TcpServerGrouping toServerParams(final InetAddress address, final int port) {
        final var ipAddress = IetfInetUtil.ipAddressFor(
            address == null ? InetAddress.getLoopbackAddress() : address);
        final var portNumber = new PortNumber(Uint16.valueOf(port < 0 ? DEFAULT_PORT : port));
        return new TcpServerParametersBuilder().setLocalAddress(ipAddress).setLocalPort(portNumber).build();
    }

    private static SSHTransportStackFactory defaultTransportStackFactory() {
        return new SSHTransportStackFactory("ssh-call-home-server", 0);
    }
}
