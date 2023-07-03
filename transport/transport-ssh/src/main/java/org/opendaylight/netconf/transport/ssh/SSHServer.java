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
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev221212.SshServerGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev221212.ssh.server.grouping.ClientAuthentication;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.server.rev221212.ssh.server.grouping.ServerIdentity;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.client.rev221212.TcpClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.server.rev221212.TcpServerGrouping;

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
        this.serverSessionFactory = new SessionFactory(serverFactoryManager);
        this.ioService = new SshIoService(this.serverFactoryManager,
            new DefaultChannelGroup("sshd-server-channels", GlobalEventExecutor.INSTANCE),
            this.serverSessionFactory);
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

    private static ServerFactoryManager newFactoryManager(
            @Nullable final SshServerGrouping serverParams,
            @Nullable final ServerFactoryManagerConfigurator configurator)
            throws UnsupportedConfigurationException {

        final var factoryMgr = SshServer.setUpDefaultServer();
        if (serverParams != null) {
            ConfigUtils.setTransportParams(factoryMgr, serverParams.getTransportParams());
            ConfigUtils.setKeepAlives(factoryMgr, serverParams.getKeepalives());
            setServerIdentity(factoryMgr, serverParams.getServerIdentity());
            setClientAuthentication(factoryMgr, serverParams.getClientAuthentication());
        }
        factoryMgr.setServiceFactories(SshServer.DEFAULT_SERVICE_FACTORIES);
        factoryMgr.setScheduledExecutorService(ThreadUtils.newSingleThreadScheduledExecutor(""));
        if (configurator != null) {
            configurator.configureServerFactoryManager(factoryMgr);
        }
        return factoryMgr;
    }

    private static void setServerIdentity(final @NonNull ServerFactoryManager factoryMgr,
            final @NonNull ServerIdentity serverIdentity) throws UnsupportedConfigurationException {
        if (serverIdentity == null) {
            throw new UnsupportedConfigurationException("Server identity configuration is required");
        }
        if (serverIdentity.getHostKey() != null && !serverIdentity.getHostKey().isEmpty()) {
            final var serverHostKeyPairs = ConfigUtils.extractServerHostKeys(serverIdentity.getHostKey());
            if (!serverHostKeyPairs.isEmpty()) {
                factoryMgr.setKeyPairProvider(KeyPairProvider.wrap(serverHostKeyPairs));
            }
        } else {
            throw new UnsupportedConfigurationException("Host keys is missing in server identity configuration");
        }
    }

    private static void setClientAuthentication(final @NonNull ServerFactoryManager factoryMgr,
            final @Nullable ClientAuthentication clientAuthentication) throws UnsupportedConfigurationException {
        if (clientAuthentication == null) {
            return;
        }
        if (clientAuthentication.getUsers() != null && clientAuthentication.getUsers().getUser() != null) {
            final var passwordMapBuilder = ImmutableMap.<String, String>builder();
            final var hostBasedMapBuilder = ImmutableMap.<String, List<PublicKey>>builder();
            final var publicKeyMapBuilder = ImmutableMap.<String, List<PublicKey>>builder();
            for (var entry : clientAuthentication.getUsers().getUser().entrySet()) {
                final String username = entry.getKey().getName();
                if (entry.getValue().getPassword() != null) { // password
                    passwordMapBuilder.put(username, entry.getValue().getPassword().getValue());
                }
                if (entry.getValue().getHostbased() != null) {
                    hostBasedMapBuilder.put(username,
                        ConfigUtils.extractPublicKeys(entry.getValue().getHostbased().getLocalOrTruststore()));
                }
                if (entry.getValue().getPublicKeys() != null) {
                    publicKeyMapBuilder.put(username,
                        ConfigUtils.extractPublicKeys(entry.getValue().getPublicKeys().getLocalOrTruststore()));
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