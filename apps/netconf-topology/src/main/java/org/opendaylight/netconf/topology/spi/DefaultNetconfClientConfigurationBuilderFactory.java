/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.spi;

import static java.util.Objects.requireNonNull;

import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.netconf.client.conf.NetconfClientConfiguration.NetconfClientProtocol;
import org.opendaylight.netconf.client.conf.NetconfClientConfigurationBuilder;
import org.opendaylight.netconf.client.mdsal.DatastoreBackedPublicKeyAuth;
import org.opendaylight.netconf.client.mdsal.api.CredentialProvider;
import org.opendaylight.netconf.client.mdsal.api.SslHandlerFactoryProvider;
import org.opendaylight.netconf.nettyutil.handler.ssh.authentication.AuthenticationHandler;
import org.opendaylight.netconf.nettyutil.handler.ssh.authentication.LoginPasswordHandler;
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
        final var builder  = NetconfClientConfigurationBuilder.create();

        final var protocol = node.getProtocol();
        if (node.requireTcpOnly()) {
            builder.withProtocol(NetconfClientProtocol.TCP)
                .withAuthHandler(getHandlerFromCredentials(node.getCredentials()));
        } else if (protocol == null || protocol.getName() == Name.SSH) {
            builder.withProtocol(NetconfClientProtocol.SSH)
                .withAuthHandler(getHandlerFromCredentials(node.getCredentials()));
        } else if (protocol.getName() == Name.TLS) {
            builder.withProtocol(NetconfClientProtocol.TLS)
                .withSslHandlerFactory(sslHandlerFactoryProvider.getSslHandlerFactory(protocol.getSpecification()));
        } else {
            throw new IllegalArgumentException("Unsupported protocol type: " + protocol.getName());
        }

        final var helloCapabilities = node.getOdlHelloMessageCapabilities();
        if (helloCapabilities != null) {
            builder.withOdlHelloCapabilities(List.copyOf(helloCapabilities.requireCapability()));
        }

        return builder
            .withName(nodeId.getValue())
            .withAddress(NetconfNodeUtils.toInetSocketAddress(node))
            .withConnectionTimeoutMillis(node.requireConnectionTimeoutMillis().toJava());
    }

    private @NonNull AuthenticationHandler getHandlerFromCredentials(final Credentials credentials) {
        if (credentials instanceof LoginPassword loginPassword) {
            return new LoginPasswordHandler(loginPassword.getUsername(), loginPassword.getPassword());
        } else if (credentials instanceof LoginPwUnencrypted unencrypted) {
            final var loginPassword = unencrypted.getLoginPasswordUnencrypted();
            return new LoginPasswordHandler(loginPassword.getUsername(), loginPassword.getPassword());
        } else if (credentials instanceof LoginPw loginPw) {
            final var loginPassword = loginPw.getLoginPassword();
            return new LoginPasswordHandler(loginPassword.getUsername(),
                    encryptionService.decrypt(loginPassword.getPassword()));
        } else if (credentials instanceof KeyAuth keyAuth) {
            final var keyPair = keyAuth.getKeyBased();
            return new DatastoreBackedPublicKeyAuth(keyPair.getUsername(), keyPair.getKeyId(), credentialProvider,
                encryptionService);
        } else {
            throw new IllegalArgumentException("Unsupported credential type: " + credentials.getClass());
        }
    }
}
