/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.DoNotCall;
import java.security.PublicKey;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import org.opendaylight.netconf.shaded.sshd.common.channel.ChannelFactory;
import org.opendaylight.netconf.shaded.sshd.common.keyprovider.KeyPairProvider;
import org.opendaylight.netconf.shaded.sshd.netty.NettyIoServiceFactoryFactory;
import org.opendaylight.netconf.shaded.sshd.server.ServerBuilder;
import org.opendaylight.netconf.shaded.sshd.server.SshServer;
import org.opendaylight.netconf.shaded.sshd.server.auth.UserAuthFactory;
import org.opendaylight.netconf.shaded.sshd.server.auth.hostbased.UserAuthHostBasedFactory;
import org.opendaylight.netconf.shaded.sshd.server.auth.password.UserAuthPasswordFactory;
import org.opendaylight.netconf.shaded.sshd.server.auth.pubkey.UserAuthPublicKeyFactory;
import org.opendaylight.netconf.shaded.sshd.server.forward.DirectTcpipFactory;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev231228.SshServerGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev231228.ssh.server.grouping.ClientAuthentication;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev231228.ssh.server.grouping.Keepalives;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev231228.ssh.server.grouping.ServerIdentity;

/**
 * Our internal-use {@link SshServer}. We reuse all the properties and logic of an {@link SshServer}, but we never allow
 * it to be started.
 */
final class TransportSshServer extends SshServer {
    private TransportSshServer() {
        // Hidden on purpose
    }

    /**
     * Guaranteed to throw an exception.
     *
     * @throws UnsupportedOperationException always
     */
    @Override
    @Deprecated(forRemoval = true)
    @DoNotCall("Always throws UnsupportedOperationException")
    public void start() {
        throw new UnsupportedOperationException();
    }

    /**
     * Guaranteed to throw an exception.
     *
     * @throws UnsupportedOperationException always
     */
    @Override
    @Deprecated(forRemoval = true)
    @DoNotCall("Always throws UnsupportedOperationException")
    public void stop() {
        throw new UnsupportedOperationException();
    }

    /**
     * A {@link ServerBuilder} producing {@link TransportSshServer}s. Also hosts adaptation from
     * {@code ietf-netconf-server.yang} configuration.
     */
    static final class Builder extends ServerBuilder {
        private static final List<ChannelFactory> CHANNEL_FACTORIES = List.of(
            TransportChannelSessionFactory.INSTANCE,
            DirectTcpipFactory.INSTANCE);

        private final NettyIoServiceFactoryFactory ioServiceFactory;
        private final ScheduledExecutorService executorService;

        private ServerFactoryManagerConfigurator configurator;
        private ClientAuthentication clientAuthentication;
        private ServerIdentity serverIdentity;
        private Keepalives keepAlives;

        Builder(final NettyIoServiceFactoryFactory ioServiceFactory, final ScheduledExecutorService executorService) {
            this.ioServiceFactory = requireNonNull(ioServiceFactory);
            this.executorService = requireNonNull(executorService);
        }

        Builder serverParams(final SshServerGrouping serverParams) throws UnsupportedConfigurationException {
            if (serverParams != null) {
                ConfigUtils.setTransportParams(this, serverParams.getTransportParams(),
                    TransportUtils::getServerKexFactories);
                keepAlives = serverParams.getKeepalives();
                serverIdentity = serverParams.getServerIdentity();
                if (serverIdentity == null) {
                    throw new UnsupportedConfigurationException("Server identity configuration is required");
                }
                clientAuthentication = serverParams.getClientAuthentication();
            }
            return this;
        }

        Builder configurator(final ServerFactoryManagerConfigurator newConfigurator) {
            configurator = newConfigurator;
            return this;
        }

        /**
         * Guaranteed to throw an exception.
         *
         * @throws UnsupportedOperationException always
         */
        @Override
        @Deprecated(forRemoval = true)
        @DoNotCall("Always throws UnsupportedOperationException")
        public TransportSshServer build() {
            throw new UnsupportedOperationException();
        }

        /**
         * Guaranteed to throw an exception.
         *
         * @throws UnsupportedOperationException always
         */
        @Override
        @Deprecated(forRemoval = true)
        @DoNotCall("Always throws UnsupportedOperationException")
        public TransportSshServer build(final boolean isFillWithDefaultValues) {
            throw new UnsupportedOperationException();
        }

        TransportSshServer buildChecked() throws UnsupportedConfigurationException {
            final var ret = (TransportSshServer) super.build(true);
            if (keepAlives != null) {
                ConfigUtils.setKeepAlives(ret, keepAlives.getMaxWait(), keepAlives.getMaxAttempts());
            } else {
                ConfigUtils.setKeepAlives(ret, null, null);
            }
            if (serverIdentity != null) {
                setServerIdentity(ret, serverIdentity);
            }
            if (clientAuthentication != null) {
                setClientAuthentication(ret, clientAuthentication);
            }
            if (configurator != null) {
                configurator.configureServerFactoryManager(ret);
            }

            ret.setIoServiceFactoryFactory(ioServiceFactory);
            ret.setScheduledExecutorService(executorService);

            try {
                ret.checkConfig();
            } catch (IllegalArgumentException e) {
                throw new UnsupportedConfigurationException("Inconsistent client configuration", e);
            }

            ret.setSessionFactory(new TransportServerSessionFactory(ret));
            return ret;
        }

        @Override
        protected ServerBuilder fillWithDefaultValues() {
            if (channelFactories == null) {
                channelFactories = CHANNEL_FACTORIES;
            }
            if (factory == null) {
                factory = TransportSshServer::new;
            }
            return super.fillWithDefaultValues();
        }

        private static void setServerIdentity(final TransportSshServer server, final ServerIdentity serverIdentity)
                throws UnsupportedConfigurationException {
            final var hostKey = serverIdentity.getHostKey();
            if (hostKey == null || hostKey.isEmpty()) {
                throw new UnsupportedConfigurationException("Host keys is missing in server identity configuration");
            }
            final var serverHostKeyPairs = ConfigUtils.extractServerHostKeys(hostKey);
            if (!serverHostKeyPairs.isEmpty()) {
                server.setKeyPairProvider(KeyPairProvider.wrap(serverHostKeyPairs));
            }
        }

        private static void setClientAuthentication(final TransportSshServer server,
                final ClientAuthentication clientAuthentication) throws UnsupportedConfigurationException {
            final var users = clientAuthentication.getUsers();
            if (users == null) {
                return;
            }
            final var userMap = users.getUser();
            if (userMap != null) {
                final var passwordMapBuilder = ImmutableMap.<String, String>builder();
                final var hostBasedMapBuilder = ImmutableMap.<String, List<PublicKey>>builder();
                final var publicKeyMapBuilder = ImmutableMap.<String, List<PublicKey>>builder();
                for (var entry : userMap.entrySet()) {
                    final var username = entry.getKey().getName();
                    final var value = entry.getValue();
                    final var password = value.getPassword();
                    if (password != null) {
                        passwordMapBuilder.put(username, password.getValue());
                    }
                    final var hostBased = value.getHostbased();
                    if (hostBased != null) {
                        hostBasedMapBuilder.put(username,
                            ConfigUtils.extractPublicKeys(hostBased.getInlineOrTruststore()));
                    }
                    final var publicKey = value.getPublicKeys();
                    if (publicKey != null) {
                        publicKeyMapBuilder.put(username,
                            ConfigUtils.extractPublicKeys(publicKey.getInlineOrTruststore()));
                    }
                }
                final var authFactoriesBuilder = ImmutableList.<UserAuthFactory>builder();
                final var passwordMap = passwordMapBuilder.build();
                if (!passwordMap.isEmpty()) {
                    authFactoriesBuilder.add(new UserAuthPasswordFactory());
                    server.setPasswordAuthenticator(new CryptHashPasswordAuthenticator(passwordMap));
                }
                final var hostBasedMap = hostBasedMapBuilder.build();
                if (!hostBasedMap.isEmpty()) {
                    final var factory = new UserAuthHostBasedFactory();
                    factory.setSignatureFactories(server.getSignatureFactories());
                    authFactoriesBuilder.add(factory);
                    server.setHostBasedAuthenticator(new UserPublicKeyAuthenticator(hostBasedMap));
                }
                final var publicKeyMap = publicKeyMapBuilder.build();
                if (!publicKeyMap.isEmpty()) {
                    final var factory = new UserAuthPublicKeyFactory();
                    factory.setSignatureFactories(server.getSignatureFactories());
                    authFactoriesBuilder.add(factory);
                    server.setPublickeyAuthenticator(new UserPublicKeyAuthenticator(publicKeyMap));
                }
                server.setUserAuthFactories(authFactoriesBuilder.build());
            }
        }
    }
}
