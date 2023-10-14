/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.spi;

import static java.util.Objects.requireNonNull;

import com.google.common.base.Strings;
import java.io.IOException;
import java.io.StringReader;
import java.security.KeyPair;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.aaa.encrypt.PKIUtil;
import org.opendaylight.netconf.client.conf.NetconfClientConfiguration.NetconfClientProtocol;
import org.opendaylight.netconf.client.conf.NetconfClientConfigurationBuilder;
import org.opendaylight.netconf.client.mdsal.api.CredentialProvider;
import org.opendaylight.netconf.client.mdsal.api.SslHandlerFactoryProvider;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev230417.EcPrivateKeyFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev230417.RsaPrivateKeyFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev230417.SubjectPublicKeyInfoFormat;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev230417.asymmetric.key.pair.grouping._private.key.type.CleartextPrivateKeyBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev230417.password.grouping.password.type.CleartextPasswordBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev230417.inline.or.keystore.asymmetric.key.grouping.inline.or.keystore.InlineBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.keystore.rev230417.inline.or.keystore.asymmetric.key.grouping.inline.or.keystore.inline.InlineDefinitionBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.client.rev230417.netconf.client.initiate.stack.grouping.transport.ssh.ssh.SshClientParametersBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.client.rev230417.netconf.client.initiate.stack.grouping.transport.ssh.ssh.TcpClientParametersBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.client.rev230417.SshClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.client.rev230417.ssh.client.grouping.ClientIdentity;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.client.rev230417.ssh.client.grouping.ClientIdentityBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.client.rev230417.ssh.client.grouping.client.identity.PasswordBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev230430.connection.parameters.Protocol.Name;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev230430.credentials.Credentials;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev230430.credentials.credentials.KeyAuth;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev230430.credentials.credentials.LoginPassword;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev230430.credentials.credentials.LoginPw;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev230430.credentials.credentials.LoginPwUnencrypted;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev221225.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Default implementation of NetconfClientConfigurationBuildFactory.
 */
@Component
@Singleton
public final class DefaultNetconfClientConfigurationBuilderFactory implements NetconfClientConfigurationBuilderFactory {
    private final SslHandlerFactoryProvider sslHandlerFactoryProvider;
    private final AAAEncryptionService encryptionService;
    private final CredentialProvider credentialProvider;

    @Inject
    @Activate
    public DefaultNetconfClientConfigurationBuilderFactory(
            @Reference final AAAEncryptionService encryptionService,
            @Reference final CredentialProvider credentialProvider,
            @Reference final SslHandlerFactoryProvider sslHandlerFactoryProvider) {
        this.encryptionService = requireNonNull(encryptionService);
        this.credentialProvider = requireNonNull(credentialProvider);
        this.sslHandlerFactoryProvider = requireNonNull(sslHandlerFactoryProvider);
    }

    @Override
    public NetconfClientConfigurationBuilder createClientConfigurationBuilder(final NodeId nodeId,
            final NetconfNode node) {
        final var builder = NetconfClientConfigurationBuilder.create();

        requireNonNull(node.getHost());
        requireNonNull(node.getPort());

        final var protocol = node.getProtocol();
        if (node.requireTcpOnly()) {
            builder.withProtocol(NetconfClientProtocol.TCP);
        } else if (protocol == null || protocol.getName() == Name.SSH) {
            builder.withProtocol(NetconfClientProtocol.SSH)
                .withSshParameters(getSshParametersFromCredentials(node.getCredentials()));
        } else if (protocol.getName() == Name.TLS) {
            final var sslHandlerBuilder = sslHandlerFactoryProvider.getSslHandlerFactory(protocol.getSpecification());
            builder.withProtocol(NetconfClientProtocol.TLS)
                .withTransportSslHandlerFactory(channel -> sslHandlerBuilder.createSslHandler());
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

    private @NonNull SshClientGrouping getSshParametersFromCredentials(final Credentials credentials) {
        final var builder = new SshClientParametersBuilder();
        if (credentials instanceof LoginPassword loginPassword) {
            builder.setClientIdentity(loginPasswordIdentity(loginPassword.getUsername(), loginPassword.getPassword()));

        } else if (credentials instanceof LoginPwUnencrypted unencrypted) {
            final var loginPassword = unencrypted.getLoginPasswordUnencrypted();
            builder.setClientIdentity(loginPasswordIdentity(loginPassword.getUsername(), loginPassword.getPassword()));

        } else if (credentials instanceof LoginPw loginPw) {
            final var loginPassword = loginPw.getLoginPassword();
            builder.setClientIdentity(loginPasswordIdentity(loginPassword.getUsername(),
                encryptionService.decrypt(loginPassword.getPassword())));

        } else if (credentials instanceof KeyAuth keyAuth) {
            final var keyBased = keyAuth.getKeyBased();
            builder.setClientIdentity(publicKeyIdentity(keyBased.getUsername(), keyBased.getKeyId(), credentialProvider,
                encryptionService));
        } else {
            throw new IllegalArgumentException("Unsupported credential type: " + credentials.getClass());
        }
        return builder.build();
    }

    private static ClientIdentity loginPasswordIdentity(final String username, final String password) {
        requireNonNull(username, "username is undefined");
        requireNonNull(password, "password is undefined");
        return new ClientIdentityBuilder()
            .setUsername(username)
            .setPassword(new PasswordBuilder()
                .setPasswordType(new CleartextPasswordBuilder().setCleartextPassword(password).build())
                .build())
            .build();
    }

    private static ClientIdentity publicKeyIdentity(final String username, final String keyId,
            final CredentialProvider credentialProvider, final AAAEncryptionService encryptionService) {
        requireNonNull(username, "username is undefined");

        // public key retrieval logic taken from DatastoreBackedPublicKeyAuth
        final var dsKeypair = credentialProvider.credentialForId(keyId);
        if (dsKeypair == null) {
            throw new IllegalArgumentException("No keypair found with keyId=" + keyId);
        }
        final var passPhrase = Strings.isNullOrEmpty(dsKeypair.getPassphrase()) ? "" : dsKeypair.getPassphrase();
        final KeyPair keyPair;
        try {
            keyPair = new PKIUtil().decodePrivateKey(
                new StringReader(encryptionService.decrypt(dsKeypair.getPrivateKey()).replace("\\n", "\n")),
                encryptionService.decrypt(passPhrase));
        } catch (IOException e) {
            throw new IllegalStateException("Could not decode private key with keyId=" + keyId, e);
        }
        return new ClientIdentityBuilder()
            .setUsername(username)
            .setPublicKey(new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.client.rev230417
                .ssh.client.grouping.client.identity.PublicKeyBuilder()
                .setInlineOrKeystore(new InlineBuilder()
                    .setInlineDefinition(new InlineDefinitionBuilder()
                        .setPublicKeyFormat(SubjectPublicKeyInfoFormat.VALUE)
                        .setPublicKey(keyPair.getPublic().getEncoded())
                        .setPrivateKeyFormat("RSA".equals(keyPair.getPrivate().getAlgorithm())
                            ? RsaPrivateKeyFormat.VALUE : EcPrivateKeyFormat.VALUE)
                        .setPrivateKeyType(new CleartextPrivateKeyBuilder()
                            .setCleartextPrivateKey(keyPair.getPrivate().getEncoded()).build())
                        .build())
                    .build())
                .build())
            .build();
    }
}
