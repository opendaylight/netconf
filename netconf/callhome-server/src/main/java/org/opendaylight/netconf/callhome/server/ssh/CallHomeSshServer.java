/*
 * Copyright (c) 2016 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.server.ssh;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import io.netty.util.HashedWheelTimer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.security.PublicKey;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.api.TransportConstants;
import org.opendaylight.netconf.callhome.server.CallHomeTransportChannelListener;
import org.opendaylight.netconf.callhome.server.StatusRecorder;
import org.opendaylight.netconf.client.NetconfClientSessionNegotiatorFactory;
import org.opendaylight.netconf.shaded.sshd.client.session.ClientSession;
import org.opendaylight.netconf.shaded.sshd.common.session.Session;
import org.opendaylight.netconf.shaded.sshd.common.session.SessionListener;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.netconf.transport.ssh.ClientFactoryManagerConfigurator;
import org.opendaylight.netconf.transport.ssh.SSHClient;
import org.opendaylight.netconf.transport.ssh.SSHTransportStackFactory;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IetfInetUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.client.rev230417.netconf.client.initiate.stack.grouping.transport.ssh.ssh.SshClientParametersBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.client.rev230417.netconf.client.listen.stack.grouping.transport.ssh.ssh.TcpServerParametersBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.client.rev230417.ssh.client.grouping.ClientIdentityBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.server.rev230417.TcpServerGrouping;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CallHomeSshServer implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(CallHomeSshServer.class);
    private static final long DEFAULT_TIMEOUT_MILLIS = 5000L;
    private static final int DEFAULT_PORT = 4334;

    private final CallHomeSshAuthProvider authProvider;
    private final StatusRecorder recorder;
    private final CallHomeSshSessionContextManager contextManager;
    private final SSHClient client;

    @VisibleForTesting
    CallHomeSshServer(final TcpServerGrouping tcpServerParams, final SSHTransportStackFactory transportStackFactory,
        final NetconfClientSessionNegotiatorFactory negotiatorFactory,
        final CallHomeSshSessionContextManager contextManager, final CallHomeSshAuthProvider authProvider,
        final StatusRecorder recorder) {
        this.authProvider = requireNonNull(authProvider);
        this.recorder = requireNonNull(recorder);
        this.contextManager = requireNonNull(contextManager);

        final var transportChannelListener = new CallHomeTransportChannelListener(negotiatorFactory, contextManager);
        final var sshClientParams = new SshClientParametersBuilder().setClientIdentity(
            new ClientIdentityBuilder().setUsername("ignored").build()).build();
        final ClientFactoryManagerConfigurator configurator = factoryMgr -> {
            factoryMgr.setServerKeyVerifier(this::verifyServerKey);
            factoryMgr.addSessionListener(createSessionListener());
        };
        try {
            client = transportStackFactory.listenClient(TransportConstants.SSH_SUBSYSTEM, transportChannelListener,
                tcpServerParams, sshClientParams, configurator).get();
        } catch (UnsupportedConfigurationException | InterruptedException | ExecutionException e) {
            throw new IllegalStateException("Could not start SSH Call-Home server", e);
        }
    }

    private SessionListener createSessionListener() {
        return new SessionListener() {
            @Override
            public void sessionEvent(final Session session, final Event event) {
                if (event == Event.KeyEstablished && session instanceof ClientSession clientSession) {
                    try {
                        // due to we have no auth factory set for ssh client, we need to trigger session auth explicitly
                        // when only server key is accepted (means auth settings are set to client session)
                        clientSession.auth().addListener(future -> {
                            if (future.isFailure()) {
                                recorder.reportFailedAuth(clientSession.getServerKey());
                                session.close(true);
                            } else if (future.isCanceled()) {
                                session.close(true);
                            }
                        });
                    } catch (IOException e) {
                        LOG.error("Exception on client session auth()", e);
                    }
                }
            }

            @Override
            public void sessionClosed(final Session session) {
                if (session instanceof ClientSession clientSession) {
                    final var context = contextManager.findBySshSession(clientSession);
                    if (context != null) {
                        contextManager.remove(context.id());
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
            recorder.reportUnknown(serverKey);
            LOG.info("No auth settings found. Connection from {} rejected.", remoteAddress);
            return false;
        }
        final var context = contextManager.createContext(authSettings.id(), clientSession);
        if (!contextManager.register(context)) {
            // Session context with same id already exists
            LOG.info("Session context with same id {} already exists. Connection from {} rejected.",
                authSettings.id(), remoteAddress);
            return false;
        }

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

    public static class Builder {
        private final CallHomeSshAuthProvider authProvider;
        private final CallHomeSshSessionContextManager contextManager;
        private final StatusRecorder recorder;

        private InetAddress address;
        private int port = DEFAULT_PORT;
        private SSHTransportStackFactory transportStackFactory;
        private NetconfClientSessionNegotiatorFactory negotiationFactory;

        public Builder(final CallHomeSshSessionContextManager contextManager,
            final CallHomeSshAuthProvider authProvider, final StatusRecorder recorder) {
            this.authProvider = authProvider;
            this.contextManager = contextManager;
            this.recorder = recorder;
        }

        public @NonNull CallHomeSshServer build() {
            return new CallHomeSshServer(
                toServerParams(address, port),
                transportStackFactory == null ? defaultTransportStackFactory() : transportStackFactory,
                negotiationFactory == null ? defaultNegotiationFactory() : negotiationFactory,
                contextManager, authProvider, recorder);
        }

        public Builder withAddress(InetAddress newAddress) {
            this.address = newAddress;
            return this;
        }

        public Builder withPort(final int newPort) {
            this.port = newPort;
            return this;
        }

        public Builder withTransportStackFactory(final SSHTransportStackFactory newTransportStackFactory) {
            this.transportStackFactory = newTransportStackFactory;
            return this;
        }

        public Builder withNegotiationFactory(final NetconfClientSessionNegotiatorFactory newNegotiationFactory) {
            this.negotiationFactory = newNegotiationFactory;
            return this;
        }
    }

    private static TcpServerGrouping toServerParams(InetAddress address, int port) {
        final var ipAddress = IetfInetUtil.ipAddressFor(
            address == null ? InetAddress.getLoopbackAddress() : address);
        final var portNumber = new PortNumber(Uint16.valueOf(port < 0 ? DEFAULT_PORT : port));
        return new TcpServerParametersBuilder().setLocalAddress(ipAddress).setLocalPort(portNumber).build();
    }

    private static SSHTransportStackFactory defaultTransportStackFactory() {
        return new SSHTransportStackFactory("ssh-call-home-server", 0);
    }

    private static NetconfClientSessionNegotiatorFactory defaultNegotiationFactory() {
        return new NetconfClientSessionNegotiatorFactory(new HashedWheelTimer(),
            Optional.empty(), DEFAULT_TIMEOUT_MILLIS);
    }
}
