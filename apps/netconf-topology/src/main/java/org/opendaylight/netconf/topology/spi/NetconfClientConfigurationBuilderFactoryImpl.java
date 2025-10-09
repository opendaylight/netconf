/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.spi;

import static java.util.Objects.requireNonNull;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.netconf.client.conf.NetconfClientConfiguration.NetconfClientProtocol;
import org.opendaylight.netconf.client.conf.NetconfClientConfigurationBuilder;
import org.opendaylight.netconf.client.mdsal.api.CredentialProvider;
import org.opendaylight.netconf.client.mdsal.api.SslContextFactoryProvider;
import org.opendaylight.netconf.shaded.sshd.client.ClientFactoryManager;
import org.opendaylight.netconf.shaded.sshd.client.auth.pubkey.UserAuthPublicKeyFactory;
import org.opendaylight.netconf.shaded.sshd.common.keyprovider.KeyIdentityProvider;
import org.opendaylight.netconf.transport.ssh.ClientFactoryManagerConfigurator;
import org.opendaylight.netconf.transport.tls.FixedSslHandlerFactory;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010.password.grouping.password.type.CleartextPasswordBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.client.rev240814.netconf.client.initiate.stack.grouping.transport.ssh.ssh.SshClientParametersBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.client.rev240814.netconf.client.initiate.stack.grouping.transport.ssh.ssh.TcpClientParametersBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.client.rev241010.ssh.client.grouping.ClientIdentity;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.client.rev241010.ssh.client.grouping.ClientIdentityBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.client.rev241010.ssh.client.grouping.TransportParams;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.client.rev241010.ssh.client.grouping.TransportParamsBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.client.rev241010.ssh.client.grouping.client.identity.PasswordBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.SshEncryptionAlgorithm;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.SshKeyExchangeAlgorithm;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.SshMacAlgorithm;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.SshPublicKeyAlgorithm;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.transport.params.grouping.EncryptionBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.transport.params.grouping.HostKeyBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.transport.params.grouping.KeyExchangeBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.common.rev241010.transport.params.grouping.MacBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev241009.connection.parameters.Protocol.Name;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev241009.credentials.Credentials;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev241009.credentials.credentials.KeyAuth;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev241009.credentials.credentials.LoginPw;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev241009.credentials.credentials.LoginPwUnencrypted;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev240911.netconf.node.augment.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Default implementation of NetconfClientConfigurationBuildFactory.
 */
@Component
@Singleton
public final class NetconfClientConfigurationBuilderFactoryImpl implements NetconfClientConfigurationBuilderFactory {
    private final SslContextFactoryProvider sslContextFactoryProvider;
    private final AAAEncryptionService encryptionService;
    private final CredentialProvider credentialProvider;

    @Inject
    @Activate
    public NetconfClientConfigurationBuilderFactoryImpl(
            @Reference final AAAEncryptionService encryptionService,
            @Reference final CredentialProvider credentialProvider,
            @Reference final SslContextFactoryProvider sslHandlerContextProvider) {
        this.encryptionService = requireNonNull(encryptionService);
        this.credentialProvider = requireNonNull(credentialProvider);
        sslContextFactoryProvider = requireNonNull(sslHandlerContextProvider);
    }

    @Override
    public NetconfClientConfigurationBuilder createClientConfigurationBuilder(final NodeId nodeId,
            final NetconfNode node) {
        final var builder = NetconfClientConfigurationBuilder.create();
        final var protocol = node.getProtocol();
        if (node.requireTcpOnly()) {
            builder.withProtocol(NetconfClientProtocol.TCP);
        } else if (protocol == null || protocol.getName() == Name.SSH) {
            builder.withProtocol(NetconfClientProtocol.SSH);
            setSshParametersFromCredentials(builder, node.getCredentials(), node);
        } else if (protocol.getName() == Name.TLS) {
            final var contextFactory = sslContextFactoryProvider.getSslContextFactory(protocol.getSpecification());
            final var sslContext = protocol.getKeyId() == null
                ? contextFactory.createSslContext()
                : contextFactory.createSslContext(protocol.getKeyId());
            builder.withProtocol(NetconfClientProtocol.TLS)
                .withSslHandlerFactory(new FixedSslHandlerFactory(sslContext));
        } else {
            throw new IllegalArgumentException("Unsupported protocol type: " + protocol.getName());
        }

        final var helloCapabilities = node.getOdlHelloMessageCapabilities();
        if (helloCapabilities != null) {
            builder.withOdlHelloCapabilities(List.copyOf(helloCapabilities.requireCapability()));
        }

        return builder
            .withName(nodeId.getValue())
            .withTcpParameters(new TcpClientParametersBuilder()
                .setRemoteAddress(node.requireHost())
                .setRemotePort(node.requirePort()).build())
            .withConnectionTimeoutMillis(node.requireConnectionTimeoutMillis().toJava());
    }

    private void setSshParametersFromCredentials(final NetconfClientConfigurationBuilder confBuilder,
            final Credentials credentials, final NetconfNode node) {
        final var sshParamsBuilder = new SshClientParametersBuilder();
        if (credentials instanceof LoginPwUnencrypted unencrypted) {
            final var loginPassword = unencrypted.getLoginPasswordUnencrypted();
            sshParamsBuilder.setClientIdentity(loginPasswordIdentity(
                loginPassword.getUsername(), loginPassword.getPassword()));
        } else if (credentials instanceof LoginPw loginPw) {
            final var loginPassword = loginPw.getLoginPassword();
            final var username = loginPassword.getUsername();

            final byte[] plainBytes;
            try {
                plainBytes = encryptionService.decrypt(loginPassword.getPassword());
            } catch (GeneralSecurityException e) {
                throw new IllegalStateException("Failed to decrypt password", e);
            }

            sshParamsBuilder.setClientIdentity(loginPasswordIdentity(username,
                new String(plainBytes, StandardCharsets.UTF_8)));
        } else if (credentials instanceof KeyAuth keyAuth) {
            final var keyBased = keyAuth.getKeyBased();
            final var keyId = keyBased.getKeyId();
            final var keyPair = credentialProvider.credentialForId(keyId);
            if (keyPair == null) {
                throw new IllegalArgumentException("No keypair found with keyId=" + keyId);
            }

            // FIXME: NETCONF-1190: this should work via
            // keystore.rev241010.inline.or.keystore.asymmetric.key.grouping.inline.or.keystore.inline.InlineDefinition
            // representation of keypair
            sshParamsBuilder.setClientIdentity(new ClientIdentityBuilder().setUsername(keyBased.getUsername()).build());
            confBuilder.withSshConfigurator(new ClientFactoryManagerConfigurator() {
                @Override
                protected void configureClientFactoryManager(final ClientFactoryManager factoryManager) {
                    factoryManager.setKeyIdentityProvider(KeyIdentityProvider.wrapKeyPairs(keyPair));
                    factoryManager.setUserAuthFactories(
                        List.of(new UserAuthPublicKeyFactory(factoryManager.getSignatureFactories())));
                }
            });
        } else {
            throw new IllegalArgumentException("Unsupported credential type: " + credentials.getClass());
        }
        sshParamsBuilder.setTransportParams(parseTransportParams(node));
        confBuilder.withSshParameters(sshParamsBuilder.build());
    }

    private static ClientIdentity loginPasswordIdentity(final String username, final String password) {
        return new ClientIdentityBuilder()
            .setUsername(requireNonNull(username, "username is undefined"))
            .setPassword(new PasswordBuilder()
                .setPasswordType(new CleartextPasswordBuilder()
                    .setCleartextPassword(requireNonNull(password, "password is undefined"))
                    .build())
                .build())
            .build();
    }

    private @NonNull TransportParams parseTransportParams(final NetconfNode node) {
        // KEX
        final var kexRaw = node.requireKeyExchange();
        final var kex = kexRaw.isEmpty() ? new String[0] : kexRaw.split(",");
        final var kexAlg = new ArrayList<SshKeyExchangeAlgorithm>();
        final var kexBuilder = new KeyExchangeBuilder();
        for (var alg : kex) {
            kexAlg.add(new SshKeyExchangeAlgorithm(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh
                .key.exchange.algs.rev241016.SshKeyExchangeAlgorithm.ofName(alg)));
        }
        final var transportParamBuilder = new TransportParamsBuilder();
        transportParamBuilder.setKeyExchange(kexBuilder.setKeyExchangeAlg(kexAlg).build());

        //MACS
        final var macsRaw = node.requireMacs();
        final var macs = macsRaw.isEmpty() ? new String[0] : macsRaw.split(",");
        final var macAlg = new ArrayList<SshMacAlgorithm>();
        final var macBuilder = new MacBuilder();
        for (var alg : macs) {
            macAlg.add(new SshMacAlgorithm(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh
                .mac.algs.rev241016.SshMacAlgorithm.ofName(alg)));
        }
        transportParamBuilder.setMac(macBuilder.setMacAlg(macAlg).build());

        //ENCRYPTION (ciphers)
        final var encryptionsRaw = node.requireEncryption();
        final var encryptions = encryptionsRaw.isEmpty() ? new String[0] : encryptionsRaw.split(",");
        final var encryptionAlg = new ArrayList<SshEncryptionAlgorithm>();
        final var encryptionBuilder = new EncryptionBuilder();
        for (var alg : encryptions) {
            encryptionAlg.add(new SshEncryptionAlgorithm(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana
                .ssh.encryption.algs.rev241016.SshEncryptionAlgorithm.ofName(alg)));
        }
        transportParamBuilder.setEncryption(encryptionBuilder.setEncryptionAlg(encryptionAlg).build());


        // HOST KEYS (signatures)
        final var hostKeysRaw = node.requireHostKeys();
        final var hostKeys = hostKeysRaw.isEmpty() ? new String[0] : hostKeysRaw.split(",");
        final var hostKeysAlg = new ArrayList<SshPublicKeyAlgorithm>();
        final var hostKeyBuilder = new HostKeyBuilder();
        for (var alg : hostKeys) {
            hostKeysAlg.add(new SshPublicKeyAlgorithm(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana.ssh
                ._public.key.algs.rev241016.SshPublicKeyAlgorithm.ofName(alg)));
        }
        transportParamBuilder.setHostKey(hostKeyBuilder.setHostKeyAlg(hostKeysAlg).build());

        return transportParamBuilder.build();
    }
}
