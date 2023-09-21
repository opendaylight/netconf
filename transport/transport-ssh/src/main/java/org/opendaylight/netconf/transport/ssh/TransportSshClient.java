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
import com.google.errorprone.annotations.DoNotCall;
import io.netty.channel.EventLoopGroup;
import java.security.cert.Certificate;
import org.opendaylight.netconf.shaded.sshd.client.ClientBuilder;
import org.opendaylight.netconf.shaded.sshd.client.SshClient;
import org.opendaylight.netconf.shaded.sshd.client.auth.UserAuthFactory;
import org.opendaylight.netconf.shaded.sshd.client.auth.hostbased.HostKeyIdentityProvider;
import org.opendaylight.netconf.shaded.sshd.client.auth.hostbased.UserAuthHostBasedFactory;
import org.opendaylight.netconf.shaded.sshd.client.auth.password.PasswordIdentityProvider;
import org.opendaylight.netconf.shaded.sshd.client.auth.password.UserAuthPasswordFactory;
import org.opendaylight.netconf.shaded.sshd.client.auth.pubkey.UserAuthPublicKeyFactory;
import org.opendaylight.netconf.shaded.sshd.client.keyverifier.ServerKeyVerifier;
import org.opendaylight.netconf.shaded.sshd.common.keyprovider.KeyIdentityProvider;
import org.opendaylight.netconf.shaded.sshd.netty.NettyIoServiceFactoryFactory;
import org.opendaylight.netconf.transport.api.UnsupportedConfigurationException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev230417.password.grouping.password.type.CleartextPassword;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.client.rev230417.ssh.client.grouping.ClientIdentity;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.client.rev230417.ssh.client.grouping.Keepalives;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.client.rev230417.ssh.client.grouping.ServerAuthentication;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev230417.TransportParamsGrouping;

/**
 * Our internal-use {@link SshClient}. We reuse all the properties and logic of an {@link SshClient}, but we never allow
 * it to be started.
 */
final class TransportSshClient extends SshClient {
    private TransportSshClient() {
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
     * A {@link ClientBuilder} producing {@link TransportSshClient}s. Also hosts adaptation from
     * {@code ietf-netconf-client.yang} configuration.
     */
    static final class Builder extends ClientBuilder {
        private final NettyIoServiceFactoryFactory ioServiceFactory;
        private final EventLoopGroup group;

        private Keepalives keepAlives;
        private ClientIdentity clientIdentity;

        Builder(final NettyIoServiceFactoryFactory ioServiceFactory, final EventLoopGroup group) {
            this.ioServiceFactory = requireNonNull(ioServiceFactory);
            this.group = requireNonNull(group);
        }

        Builder transportParams(final TransportParamsGrouping params) throws UnsupportedConfigurationException {
            ConfigUtils.setTransportParams(this, params, TransportUtils::getClientKexFactories);
            return this;
        }

        Builder keepAlives(final Keepalives newkeepAlives) {
            keepAlives = newkeepAlives;
            return this;
        }

        Builder clientIdentity(final ClientIdentity newClientIdentity) {
            clientIdentity = newClientIdentity;
            return this;
        }

        Builder serverAuthentication(final ServerAuthentication serverAuthentication)
                throws UnsupportedConfigurationException {
            final ServerKeyVerifier newVerifier;
            if (serverAuthentication != null) {
                final var certificatesList = ImmutableList.<Certificate>builder()
                    .addAll(ConfigUtils.extractCertificates(serverAuthentication.getCaCerts()))
                    .addAll(ConfigUtils.extractCertificates(serverAuthentication.getEeCerts()))
                    .build();
                final var publicKeys = ConfigUtils.extractPublicKeys(serverAuthentication.getSshHostKeys());
                if (certificatesList.isEmpty() && publicKeys.isEmpty()) {
                    throw new UnsupportedConfigurationException(
                        "Server authentication should contain either ssh-host-keys, or ca-certs, or ee-certs");
                }
                newVerifier = new ServerPublicKeyVerifier(certificatesList, publicKeys);
            } else {
                newVerifier = null;
            }

            serverKeyVerifier(newVerifier);
            return this;
        }

        TransportSshClient buildChecked() throws UnsupportedConfigurationException {
            final var ret = (TransportSshClient) super.build(true);
            if (keepAlives != null) {
                ConfigUtils.setKeepAlives(ret, keepAlives.getMaxWait(), keepAlives.getMaxAttempts());
            } else {
                ConfigUtils.setKeepAlives(ret, null, null);
            }
            if (clientIdentity != null && clientIdentity.getNone() == null) {
                setClientIdentity(ret, clientIdentity);
            }
            ret.setIoServiceFactoryFactory(ioServiceFactory);
            ret.setScheduledExecutorService(group);

            try {
                ret.checkConfig();
            } catch (IllegalArgumentException e) {
                throw new UnsupportedConfigurationException("Inconsistent client configuration", e);
            }
            return ret;
        }

        /**
         * Guaranteed to throw an exception.
         *
         * @throws UnsupportedOperationException always
         */
        @Override
        @Deprecated(forRemoval = true)
        @DoNotCall("Always throws UnsupportedOperationException")
        public TransportSshClient build() {
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
        public TransportSshClient build(final boolean isFillWithDefaultValues) {
            throw new UnsupportedOperationException();
        }

        @Override
        protected ClientBuilder fillWithDefaultValues() {
            if (factory == null) {
                factory = TransportSshClient::new;
            }
            return super.fillWithDefaultValues();
        }

        private static void setClientIdentity(final TransportSshClient client, final ClientIdentity clientIdentity)
                throws UnsupportedConfigurationException {
            final var authFactoriesListBuilder = ImmutableList.<UserAuthFactory>builder();
            final var password = clientIdentity.getPassword();
            if (password != null) {
                if (password.getPasswordType() instanceof CleartextPassword clearTextPassword) {
                    client.setPasswordIdentityProvider(
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
                factory.setSignatureFactories(client.getSignatureFactories());
                authFactoriesListBuilder.add(factory);
            }
            final var publicKey = clientIdentity.getPublicKey();
            if (publicKey != null) {
                final var keyPairs = ConfigUtils.extractKeyPair(publicKey.getInlineOrKeystore());
                client.setKeyIdentityProvider(KeyIdentityProvider.wrapKeyPairs(keyPairs));
                final var factory = new UserAuthPublicKeyFactory();
                factory.setSignatureFactories(client.getSignatureFactories());
                authFactoriesListBuilder.add(factory);
            }
            // FIXME implement authentication using X509 certificate
            final var userAuthFactories = authFactoriesListBuilder.build();
            if (userAuthFactories.isEmpty()) {
                throw new UnsupportedConfigurationException("Client Identity has no authentication mechanism defined");
            }
            client.setUserAuthFactories(userAuthFactories);
        }
    }
}
