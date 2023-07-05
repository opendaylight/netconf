/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.transport.ssh;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.GlobalEventExecutor;
import java.security.cert.Certificate;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.shaded.sshd.client.ClientFactoryManager;
import org.opendaylight.netconf.shaded.sshd.client.SshClient;
import org.opendaylight.netconf.shaded.sshd.client.auth.UserAuthFactory;
import org.opendaylight.netconf.shaded.sshd.client.auth.hostbased.HostKeyIdentityProvider;
import org.opendaylight.netconf.shaded.sshd.client.auth.hostbased.UserAuthHostBasedFactory;
import org.opendaylight.netconf.shaded.sshd.client.auth.password.PasswordIdentityProvider;
import org.opendaylight.netconf.shaded.sshd.client.auth.password.UserAuthPasswordFactory;
import org.opendaylight.netconf.shaded.sshd.client.auth.pubkey.UserAuthPublicKeyFactory;
import org.opendaylight.netconf.shaded.sshd.client.keyverifier.AcceptAllServerKeyVerifier;
import org.opendaylight.netconf.shaded.sshd.client.session.ClientSessionImpl;
import org.opendaylight.netconf.shaded.sshd.client.session.SessionFactory;
import org.opendaylight.netconf.shaded.sshd.common.io.IoHandler;
import org.opendaylight.netconf.shaded.sshd.common.keyprovider.KeyIdentityProvider;
import org.opendaylight.netconf.shaded.sshd.common.util.threads.ThreadUtils;
import org.opendaylight.netconf.transport.api.TransportChannelListener;
import org.opendaylight.netconf.transport.api.TransportStack;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.netconf.transport.tcp.TCPClient;
import org.opendaylight.netconf.transport.tcp.TCPServer;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev230417.password.grouping.password.type.CleartextPassword;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.client.rev230417.SshClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.client.rev230417.ssh.client.grouping.ClientIdentity;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.client.rev230417.ssh.client.grouping.ServerAuthentication;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.client.rev230417.TcpClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.server.rev230417.TcpServerGrouping;

/**
 * A {@link TransportStack} acting as an SSH client.
 */
public final class SSHClient extends SSHTransportStack {

    private final ClientFactoryManager clientFactoryManager;
    private final SessionFactory sessionFactory;

    private SSHClient(final TransportChannelListener listener, final ClientFactoryManager clientFactoryManager,
            final String username) {
        super(listener);
        this.clientFactoryManager = clientFactoryManager;
        this.clientFactoryManager.addSessionListener(new UserAuthSessionListener(sessionAuthHandlers, sessions));
        sessionFactory = new SessionFactory(clientFactoryManager) {
            @Override
            protected ClientSessionImpl setupSession(final ClientSessionImpl session) {
                session.setUsername(username);
                return session;
            }
        };
        ioService = new SshIoService(this.clientFactoryManager,
                new DefaultChannelGroup("sshd-client-channels", GlobalEventExecutor.INSTANCE),
                sessionFactory);
    }

    @Override
    protected IoHandler getSessionFactory() {
        return sessionFactory;
    }

    public static @NonNull ListenableFuture<SSHClient> connect(final TransportChannelListener listener,
            final Bootstrap bootstrap, final TcpClientGrouping connectParams,
            final SshClientGrouping clientParams) throws UnsupportedConfigurationException {
        final var factoryMgr = newFactoryManager(clientParams);
        final var sshClient = new SSHClient(listener, factoryMgr, getUsername(clientParams));
        return transformUnderlay(sshClient, TCPClient.connect(sshClient.asListener(), bootstrap, connectParams));
    }

    public static @NonNull ListenableFuture<SSHClient> listen(final TransportChannelListener listener,
            final ServerBootstrap bootstrap, final TcpServerGrouping listenParams, final SshClientGrouping clientParams)
            throws UnsupportedConfigurationException {
        final var factoryMgr = newFactoryManager(clientParams);
        final var sshClient = new SSHClient(listener, factoryMgr, getUsername(clientParams));
        return transformUnderlay(sshClient, TCPServer.listen(sshClient.asListener(), bootstrap, listenParams));
    }

    private static String getUsername(final SshClientGrouping clientParams) {
        final var clientIdentity = clientParams.getClientIdentity();
        return clientIdentity == null ? "" : clientIdentity.getUsername();
    }

    private static ClientFactoryManager newFactoryManager(final SshClientGrouping parameters)
            throws UnsupportedConfigurationException {
        final var factoryMgr = SshClient.setUpDefaultClient();

        ConfigUtils.setTransportParams(factoryMgr, parameters.getTransportParams());
        ConfigUtils.setKeepAlives(factoryMgr, parameters.getKeepalives());

        setClientIdentity(factoryMgr, parameters.getClientIdentity());
        setServerAuthentication(factoryMgr, parameters.getServerAuthentication());

        factoryMgr.setServiceFactories(SshClient.DEFAULT_SERVICE_FACTORIES);
        factoryMgr.setScheduledExecutorService(ThreadUtils.newSingleThreadScheduledExecutor("sshd-client-pool"));
        return factoryMgr;
    }

    private static void setClientIdentity(@NonNull final ClientFactoryManager factoryMgr,
            final @Nullable ClientIdentity clientIdentity) throws UnsupportedConfigurationException {
        if (clientIdentity == null || clientIdentity.getNone() != null) {
            return;
        }
        final var authFactoriesListBuilder = ImmutableList.<UserAuthFactory>builder();
        final var password = clientIdentity.getPassword();
        if (password != null) {
            if (password.getPasswordType() instanceof CleartextPassword clearTextPassword) {
                factoryMgr.setPasswordIdentityProvider(
                        PasswordIdentityProvider.wrapPasswords(clearTextPassword.requireCleartextPassword()));
                authFactoriesListBuilder.add(new UserAuthPasswordFactory());
            }
            // TODO support encrypted password -- requires augmentation of default schema
        }
        final var hostBased = clientIdentity.getHostbased();
        if (hostBased != null) {
            var keyPair = ConfigUtils.extractKeyPair(hostBased.getInlineOrKeystore());
            var factory = new UserAuthHostBasedFactory();
            factory.setClientHostKeys(HostKeyIdentityProvider.wrap(keyPair));
            factory.setClientUsername(clientIdentity.getUsername());
            factory.setClientHostname(null); // not provided via config
            factory.setSignatureFactories(factoryMgr.getSignatureFactories());
            authFactoriesListBuilder.add(factory);
        }
        final var publicKey = clientIdentity.getPublicKey();
        if (publicKey != null) {
            final var keyPairs = ConfigUtils.extractKeyPair(publicKey.getInlineOrKeystore());
            factoryMgr.setKeyIdentityProvider(KeyIdentityProvider.wrapKeyPairs(keyPairs));
            final var factory = new UserAuthPublicKeyFactory();
            factory.setSignatureFactories(factoryMgr.getSignatureFactories());
            authFactoriesListBuilder.add(factory);
        }
        // FIXME implement authentication using X509 certificate
        final var userAuthFactories = authFactoriesListBuilder.build();
        if (userAuthFactories.isEmpty()) {
            throw new UnsupportedConfigurationException("Client Identity has no authentication mechanism defined");
        }
        factoryMgr.setUserAuthFactories(userAuthFactories);
    }

    private static void setServerAuthentication(final @NonNull ClientFactoryManager factoryMgr,
            final @Nullable ServerAuthentication serverAuthentication) throws UnsupportedConfigurationException {
        if (serverAuthentication == null) {
            factoryMgr.setServerKeyVerifier(AcceptAllServerKeyVerifier.INSTANCE);
            return;
        }
        final var certificatesList = ImmutableList.<Certificate>builder()
                .addAll(ConfigUtils.extractCertificates(serverAuthentication.getCaCerts()))
                .addAll(ConfigUtils.extractCertificates(serverAuthentication.getEeCerts()))
                .build();
        final var publicKeys = ConfigUtils.extractPublicKeys(serverAuthentication.getSshHostKeys());
        if (!certificatesList.isEmpty() || !publicKeys.isEmpty()) {
            factoryMgr.setServerKeyVerifier(new ServerPublicKeyVerifier(certificatesList, publicKeys));
        } else {
            throw new UnsupportedConfigurationException("Server authentication should contain either ssh-host-keys "
                    + "or ca-certs or ee-certs");
        }
    }
}