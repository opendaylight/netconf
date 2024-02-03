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
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.netconf.client.conf.NetconfClientConfiguration.NetconfClientProtocol;
import org.opendaylight.netconf.client.conf.NetconfClientConfigurationBuilder;
import org.opendaylight.netconf.client.mdsal.api.CredentialProvider;
import org.opendaylight.netconf.client.mdsal.api.SslHandlerFactoryProvider;
import org.opendaylight.netconf.shaded.sshd.client.auth.pubkey.UserAuthPublicKeyFactory;
import org.opendaylight.netconf.shaded.sshd.common.keyprovider.KeyIdentityProvider;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.crypto.types.rev231228.password.grouping.password.type.CleartextPasswordBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.client.rev231228.netconf.client.initiate.stack.grouping.transport.ssh.ssh.SshClientParametersBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.client.rev231228.netconf.client.initiate.stack.grouping.transport.ssh.ssh.TcpClientParametersBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.client.rev231228.ssh.client.grouping.ClientIdentity;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.client.rev231228.ssh.client.grouping.ClientIdentityBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.client.rev231228.ssh.client.grouping.client.identity.PasswordBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev240120.connection.parameters.Protocol.Name;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev240120.credentials.Credentials;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev240120.credentials.credentials.KeyAuth;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev240120.credentials.credentials.LoginPw;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev240120.credentials.credentials.LoginPwUnencrypted;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev231121.NetconfNode;
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
    private final SslHandlerFactoryProvider sslHandlerFactoryProvider;
    private final AAAEncryptionService encryptionService;
    private final CredentialProvider credentialProvider;

    @Inject
    @Activate
    public NetconfClientConfigurationBuilderFactoryImpl(
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
        final var protocol = node.getProtocol();
        if (node.requireTcpOnly()) {
            builder.withProtocol(NetconfClientProtocol.TCP);
        } else if (protocol == null || protocol.getName() == Name.SSH) {
            builder.withProtocol(NetconfClientProtocol.SSH);
            setSshParametersFromCredentials(builder, node.getCredentials());
        } else if (protocol.getName() == Name.TLS) {
            final var handlerFactory = sslHandlerFactoryProvider.getSslHandlerFactory(protocol.getSpecification());
            builder.withProtocol(NetconfClientProtocol.TLS)
                .withSslHandlerFactory(channel -> handlerFactory.createSslHandler());
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
            final Credentials credentials) {
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
            sshParamsBuilder.setClientIdentity(new ClientIdentityBuilder().setUsername(keyBased.getUsername()).build());
            confBuilder.withSshConfigurator(factoryMgr -> {
                final var keyId = keyBased.getKeyId();
                final var keyPair = credentialProvider.credentialForId(keyId);
                if (keyPair == null) {
                    throw new IllegalArgumentException("No keypair found with keyId=" + keyId);
                }
                factoryMgr.setKeyIdentityProvider(KeyIdentityProvider.wrapKeyPairs(keyPair));
                final var factory = new UserAuthPublicKeyFactory();
                factory.setSignatureFactories(factoryMgr.getSignatureFactories());
                factoryMgr.setUserAuthFactories(List.of(factory));
            });
        } else {
            throw new IllegalArgumentException("Unsupported credential type: " + credentials.getClass());
        }
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
}
