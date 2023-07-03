/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ListenableFuture;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.GlobalEventExecutor;
import java.security.PublicKey;
import java.util.List;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.shaded.sshd.common.io.IoHandler;
import org.opendaylight.netconf.shaded.sshd.common.keyprovider.KeyPairProvider;
import org.opendaylight.netconf.shaded.sshd.common.util.threads.ThreadUtils;
import org.opendaylight.netconf.shaded.sshd.server.ServerFactoryManager;
import org.opendaylight.netconf.shaded.sshd.server.SshServer;
import org.opendaylight.netconf.shaded.sshd.server.auth.UserAuthFactory;
import org.opendaylight.netconf.shaded.sshd.server.auth.hostbased.UserAuthHostBasedFactory;
import org.opendaylight.netconf.shaded.sshd.server.auth.password.UserAuthPasswordFactory;
import org.opendaylight.netconf.shaded.sshd.server.auth.pubkey.UserAuthPublicKeyFactory;
import org.opendaylight.netconf.shaded.sshd.server.session.SessionFactory;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.api.TransportStack;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.netconf.transport.tcp.TCPClient;
import org.opendaylight.netconf.transport.tcp.TCPServer;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev230417.SshServerGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev230417.ssh.server.grouping.ClientAuthentication;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev230417.ssh.server.grouping.ServerIdentity;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.client.rev230417.TcpClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.server.rev230417.TcpServerGrouping;

/**
 * A {@link TransportStack} acting as an SSH server.
 */
public final class SSHServer extends SSHTransportStack {

    private final ServerFactoryManager serverFactoryManager;
    private final SessionFactory serverSessionFactory;

    private SSHServer(final TransportChannelListener listener, final ServerFactoryManager serverFactoryManager) {
        super(listener);
        this.serverFactoryManager = requireNonNull(serverFactoryManager);
        this.serverFactoryManager.addSessionListener(new UserAuthSessionListener(sessionAuthHandlers, sessions));
        serverSessionFactory = new SessionFactory(serverFactoryManager);
        ioService = new SshIoService(this.serverFactoryManager,
                new DefaultChannelGroup("sshd-server-channels", GlobalEventExecutor.INSTANCE),
                serverSessionFactory);
    }

    @Override
    protected IoHandler getSessionFactory() {
        return serverSessionFactory;
    }

    public static @NonNull ListenableFuture<SSHServer> connect(final TransportChannelListener listener,
            final Bootstrap bootstrap, final TcpClientGrouping connectParams, final SshServerGrouping serverParams)
            throws UnsupportedConfigurationException {
        final var server = new SSHServer(listener, newFactoryManager(requireNonNull(serverParams), null));
        return transformUnderlay(server, TCPClient.connect(server.asListener(), bootstrap, connectParams));
    }

    public static @NonNull ListenableFuture<SSHServer> listen(final TransportChannelListener listener,
            final ServerBootstrap bootstrap, final TcpServerGrouping connectParams,
            final SshServerGrouping serverParams) throws UnsupportedConfigurationException {
        requireNonNull(serverParams);
        return listen(listener, bootstrap, connectParams, serverParams, null);
    }

    /**
     * Builds and starts SSH Server.
     *
     * @param listener server channel listener, required
     * @param bootstrap server bootstrap instance, required
     * @param connectParams tcp transport configuration, required
     * @param serverParams ssh overlay configuration, optional if configurator is defined, required otherwise
     * @param configurator server factory manager configurator, optional if serverParams is defined, required otherwise
     * @return server instance as listenable future
     * @throws UnsupportedConfigurationException if any of configurations is invalid or incomplete
     * @throws NullPointerException if any of required parameters is null
     * @throws IllegalArgumentException if both configurator and serverParams are null
     */
    public static @NonNull ListenableFuture<SSHServer> listen(final TransportChannelListener listener,
            final ServerBootstrap bootstrap, final TcpServerGrouping connectParams,
            final SshServerGrouping serverParams, final ServerFactoryManagerConfigurator configurator)
            throws UnsupportedConfigurationException {
        checkArgument(serverParams != null || configurator != null,
            "Neither server parameters nor factory configurator is defined");
        final var factoryMgr = newFactoryManager(serverParams, configurator);
        final var server = new SSHServer(listener, factoryMgr);
        return transformUnderlay(server, TCPServer.listen(server.asListener(), bootstrap, connectParams));
    }

    private static ServerFactoryManager newFactoryManager(final @Nullable SshServerGrouping serverParams,
            final @Nullable ServerFactoryManagerConfigurator configurator) throws UnsupportedConfigurationException {
        final var factoryMgr = SshServer.setUpDefaultServer();
        if (serverParams != null) {
            ConfigUtils.setTransportParams(factoryMgr, serverParams.getTransportParams());
            ConfigUtils.setKeepAlives(factoryMgr, serverParams.getKeepalives());
            setServerIdentity(factoryMgr, serverParams.getServerIdentity());
            setClientAuthentication(factoryMgr, serverParams.getClientAuthentication());
        }
        if (configurator != null) {
            configurator.configureServerFactoryManager(factoryMgr);
        }
        factoryMgr.setServiceFactories(SshServer.DEFAULT_SERVICE_FACTORIES);
        factoryMgr.setScheduledExecutorService(ThreadUtils.newSingleThreadScheduledExecutor(""));
        return factoryMgr;
    }

    private static void setServerIdentity(final @NonNull ServerFactoryManager factoryMgr,
            final @Nullable ServerIdentity serverIdentity) throws UnsupportedConfigurationException {
        if (serverIdentity == null) {
            throw new UnsupportedConfigurationException("Server identity configuration is required");
        }
        final var hostKey = serverIdentity.getHostKey();
        if (hostKey == null || hostKey.isEmpty()) {
            throw new UnsupportedConfigurationException("Host keys is missing in server identity configuration");
        }
        final var serverHostKeyPairs = ConfigUtils.extractServerHostKeys(hostKey);
        if (!serverHostKeyPairs.isEmpty()) {
            factoryMgr.setKeyPairProvider(KeyPairProvider.wrap(serverHostKeyPairs));
        }
    }

    private static void setClientAuthentication(final @NonNull ServerFactoryManager factoryMgr,
            final @Nullable ClientAuthentication clientAuthentication) throws UnsupportedConfigurationException {
        if (clientAuthentication == null) {
            return;
        }
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
                final String username = entry.getKey().getName();
                final var value = entry.getValue();
                final var password = value.getPassword();
                if (password != null) {
                    passwordMapBuilder.put(username, password.getValue());
                }
                final var hostBased = value.getHostbased();
                if (hostBased != null) {
                    hostBasedMapBuilder.put(username, ConfigUtils.extractPublicKeys(hostBased.getInlineOrTruststore()));
                }
                final var publicKey = value.getPublicKeys();
                if (publicKey != null) {
                    publicKeyMapBuilder.put(username, ConfigUtils.extractPublicKeys(publicKey.getInlineOrTruststore()));
                }
            }
            final var authFactoriesBuilder = ImmutableList.<UserAuthFactory>builder();
            final var passwordMap = passwordMapBuilder.build();
            if (!passwordMap.isEmpty()) {
                authFactoriesBuilder.add(new UserAuthPasswordFactory());
                factoryMgr.setPasswordAuthenticator(new CryptHashPasswordAuthenticator(passwordMap));
            }
            final var hostBasedMap = hostBasedMapBuilder.build();
            if (!hostBasedMap.isEmpty()) {
                final var factory = new UserAuthHostBasedFactory();
                factory.setSignatureFactories(factoryMgr.getSignatureFactories());
                authFactoriesBuilder.add(factory);
                factoryMgr.setHostBasedAuthenticator(new UserPublicKeyAuthenticator(hostBasedMap));
            }
            final var publicKeyMap = publicKeyMapBuilder.build();
            if (!publicKeyMap.isEmpty()) {
                final var factory = new UserAuthPublicKeyFactory();
                factory.setSignatureFactories(factoryMgr.getSignatureFactories());
                authFactoriesBuilder.add(factory);
                factoryMgr.setPublickeyAuthenticator(new UserPublicKeyAuthenticator(publicKeyMap));
            }
            factoryMgr.setUserAuthFactories(authFactoriesBuilder.build());
        }
    }
}