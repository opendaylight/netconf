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
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.RSAPrivateKey;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.netconf.client.conf.NetconfClientConfiguration.NetconfClientProtocol;
import org.opendaylight.netconf.client.conf.NetconfClientConfigurationBuilder;
import org.opendaylight.netconf.client.mdsal.api.CredentialProvider;
import org.opendaylight.netconf.client.mdsal.api.SslContextFactoryProvider;
import org.opendaylight.netconf.transport.tls.FixedSslHandlerFactory;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010.EcPrivateKeyFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010.RsaPrivateKeyFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010.SubjectPublicKeyInfoFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010._private.key.grouping._private.key.type.CleartextPrivateKeyBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev241010.password.grouping.password.type.CleartextPasswordBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev241010.inline.or.keystore.asymmetric.key.grouping.inline.or.keystore.CentralKeystoreBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev241010.inline.or.keystore.asymmetric.key.grouping.inline.or.keystore.Inline;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev241010.inline.or.keystore.asymmetric.key.grouping.inline.or.keystore.InlineBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev241010.inline.or.keystore.asymmetric.key.grouping.inline.or.keystore.inline.InlineDefinitionBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.client.rev251204.netconf.client.initiate.stack.grouping.transport.ssh.ssh.SshClientParametersBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.client.rev251204.netconf.client.initiate.stack.grouping.transport.ssh.ssh.TcpClientParametersBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.client.rev241010.ssh.client.grouping.ClientIdentity;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.client.rev241010.ssh.client.grouping.ClientIdentityBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.client.rev241010.ssh.client.grouping.TransportParamsBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.client.rev241010.ssh.client.grouping.client.identity.PasswordBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.client.rev241010.ssh.client.grouping.client.identity.PublicKeyBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev251205.connection.parameters.Protocol;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev251205.connection.parameters.Protocol.Name;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev251205.connection.parameters.protocol.specification.SshCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev251205.credentials.Credentials;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev251205.credentials.credentials.KeyAuth;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev251205.credentials.credentials.LoginPw;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev251205.credentials.credentials.LoginPwUnencrypted;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev251205.netconf.node.augment.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev251205.network.topology.topology.topology.types.topology.netconf.SshTransportTopologyParameters;
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
    // Set only by the deprecated constructor. It resolves the keys for key-based authentication instead of the central
    // keystore.
    @Deprecated(since = "12.0.3", forRemoval = true)
    private final @Nullable CredentialProvider credentialProvider;

    @Inject
    @Activate
    public NetconfClientConfigurationBuilderFactoryImpl(@Reference final AAAEncryptionService encryptionService,
            @Reference final SslContextFactoryProvider sslHandlerContextProvider) {
        this.encryptionService = requireNonNull(encryptionService);
        sslContextFactoryProvider = requireNonNull(sslHandlerContextProvider);
        credentialProvider = null;
    }

    /**
     * Construct an instance which resolves the keys for key-based authentication through a deprecated
     * {@link CredentialProvider}. The key is passed to the NETCONF client inline, so it works without a central
     * keystore. Only RSA and EC keys are supported.
     *
     * @param encryptionService the encryption service
     * @param credentialProvider the provider of the keys for key-based authentication
     * @param sslHandlerContextProvider the SSL context factory provider
     * @deprecated Use {@link #NetconfClientConfigurationBuilderFactoryImpl(AAAEncryptionService,
     *             SslContextFactoryProvider)} instead, with the keys in the central keystore.
     */
    @Deprecated(since = "12.0.3", forRemoval = true)
    public NetconfClientConfigurationBuilderFactoryImpl(final AAAEncryptionService encryptionService,
            final CredentialProvider credentialProvider, final SslContextFactoryProvider sslHandlerContextProvider) {
        this.encryptionService = requireNonNull(encryptionService);
        sslContextFactoryProvider = requireNonNull(sslHandlerContextProvider);
        this.credentialProvider = requireNonNull(credentialProvider);
    }

    @Override
    public NetconfClientConfigurationBuilder createClientConfigurationBuilder(final NodeId nodeId,
            final NetconfNode node, final SshTransportTopologyParameters topologySshParams) {
        final var builder = NetconfClientConfigurationBuilder.create();
        final var protocol = node.getProtocol();
        if (node.requireTcpOnly()) {
            builder.withProtocol(NetconfClientProtocol.TCP);
        } else if (protocol == null || protocol.getName() == Name.SSH) {
            builder.withProtocol(NetconfClientProtocol.SSH);
            setSshParametersFromCredentials(builder, node.getCredentials(), node.nonnullProtocol(), topologySshParams);
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
            final Credentials credentials, final Protocol protocol,
            final SshTransportTopologyParameters topologySshParams) {
        final var sshParamsBuilder = new SshClientParametersBuilder();
        switch (credentials) {
            case LoginPwUnencrypted unencrypted -> {
                final var loginPassword = unencrypted.getLoginPasswordUnencrypted();
                sshParamsBuilder.setClientIdentity(loginPasswordIdentity(
                    loginPassword.getUsername(), loginPassword.getPassword()));
            }
            case LoginPw loginPw -> {
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
            }
            case KeyAuth keyAuth -> {
                final var keyBased = keyAuth.getKeyBased();
                // key-id is optional in the model, without it there is no key to authenticate with
                final var keyId = keyBased.getKeyId();
                if (keyId == null) {
                    throw new IllegalArgumentException("Key-based credentials are missing key-id");
                }
                sshParamsBuilder.setClientIdentity(new ClientIdentityBuilder()
                    .setUsername(keyBased.getUsername())
                    .setPublicKey(new PublicKeyBuilder()
                        .setInlineOrKeystore(credentialProvider == null
                            ? new CentralKeystoreBuilder().setCentralKeystoreReference(keyId).build()
                            : inlineKeyPair(credentialProvider, keyId))
                        .build())
                    .build());
            }
            default -> throw new IllegalArgumentException("Unsupported credential type: " + credentials.getClass());
        }

        final var transportParamBuilder = new TransportParamsBuilder();
        switch (protocol.getSpecification()) {
            case SshCase sshCase -> {
                final var nodeSshParams = sshCase.getSshTransportParameters();
                transportParamBuilder.setHostKey(nodeSshParams.getHostKey() == null
                    ? topologySshParams.nonnullHostKey() : nodeSshParams.getHostKey());
                transportParamBuilder.setEncryption(nodeSshParams.getEncryption() == null
                    ? topologySshParams.nonnullEncryption() : nodeSshParams.getEncryption());
                transportParamBuilder.setKeyExchange(nodeSshParams.getKeyExchange() == null
                    ? topologySshParams.nonnullKeyExchange() : nodeSshParams.getKeyExchange());
                transportParamBuilder.setMac(nodeSshParams.getMac() == null
                    ? topologySshParams.nonnullMac() : nodeSshParams.getMac());
            }
            case null -> {
                // There are no node specific parameters use topology defaults
                transportParamBuilder.setHostKey(topologySshParams.nonnullHostKey());
                transportParamBuilder.setEncryption(topologySshParams.nonnullEncryption());
                transportParamBuilder.setKeyExchange(topologySshParams.nonnullKeyExchange());
                transportParamBuilder.setMac(topologySshParams.nonnullMac());
            }
            default -> {
                throw new IllegalArgumentException("Unexpected protocol specification:" + protocol.getSpecification());
            }
        }
        sshParamsBuilder.setTransportParams(transportParamBuilder.build());

        confBuilder.withSshParameters(sshParamsBuilder.build());
    }

    /**
     * Resolve the key pair for key-based authentication through a {@link CredentialProvider} and return it as an
     * inline key, so it works without a central keystore. The key is encoded in the formats transport-ssh parses: the
     * public key as SubjectPublicKeyInfo and the private key as PKCS#8, which supports only RSA and EC keys.
     *
     * @param credentialProvider the provider of the keys
     * @param keyId the identifier of the key
     * @return the inline key pair
     * @throws IllegalArgumentException if there is no key with this identifier, or if it is neither an RSA nor an EC
     *                                  key
     * @deprecated Used only by the deprecated constructor, to be removed together with it.
     */
    @Deprecated(since = "12.0.3", forRemoval = true)
    private static Inline inlineKeyPair(final CredentialProvider credentialProvider, final String keyId) {
        final var keyPair = credentialProvider.credentialForId(keyId);
        if (keyPair == null) {
            throw new IllegalArgumentException("No keypair found with keyId=" + keyId);
        }
        final var privateKey = keyPair.getPrivate();
        final var privateKeyFormat = switch (privateKey) {
            case RSAPrivateKey ignored -> RsaPrivateKeyFormat.VALUE;
            case ECPrivateKey ignored -> EcPrivateKeyFormat.VALUE;
            default -> throw new IllegalArgumentException("Unsupported %s key with keyId=%s".formatted(
                privateKey.getAlgorithm(), keyId));
        };
        return new InlineBuilder()
            .setInlineDefinition(new InlineDefinitionBuilder()
                .setPublicKeyFormat(SubjectPublicKeyInfoFormat.VALUE)
                .setPublicKey(keyPair.getPublic().getEncoded())
                .setPrivateKeyFormat(privateKeyFormat)
                .setPrivateKeyType(new CleartextPrivateKeyBuilder()
                    .setCleartextPrivateKey(privateKey.getEncoded())
                    .build())
                .build())
            .build();
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
}
