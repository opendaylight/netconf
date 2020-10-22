/*
 * Copyright (c) 2020 ... and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.nativ.netconf.communicator.util;

import io.netty.util.concurrent.EventExecutor;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.netconf.client.NetconfClientSessionListener;
import org.opendaylight.netconf.client.conf.NetconfClientConfiguration;
import org.opendaylight.netconf.client.conf.NetconfReconnectingClientConfiguration;
import org.opendaylight.netconf.client.conf.NetconfReconnectingClientConfigurationBuilder;
import org.opendaylight.netconf.nativ.netconf.communicator.protocols.ssh.DatastoreBackedPublicKeyAuth;
import org.opendaylight.netconf.nativ.netconf.communicator.protocols.ssh.NativeNetconfKeystore;
import org.opendaylight.netconf.nativ.netconf.communicator.protocols.ssh.SslHandlerFactoryImpl;
import org.opendaylight.netconf.nettyutil.ReconnectStrategyFactory;
import org.opendaylight.netconf.nettyutil.TimedReconnectStrategyFactory;
import org.opendaylight.netconf.nettyutil.handler.ssh.authentication.AuthenticationHandler;
import org.opendaylight.netconf.nettyutil.handler.ssh.authentication.LoginPasswordHandler;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.connection.parameters.Protocol;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.connection.parameters.Protocol.Name;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.credentials.Credentials;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.credentials.credentials.KeyAuth;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.credentials.credentials.LoginPw;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.credentials.credentials.LoginPwUnencrypted;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.credentials.credentials.key.auth.KeyBased;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.credentials.credentials.login.pw.LoginPassword;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.credentials.credentials.login.pw.unencrypted.LoginPasswordUnencrypted;

public final class NativeNetconfClientConfigUtil {

    private static final boolean DEFAULT_IS_TCP_ONLY = false;
    private static final int DEFAULT_MAX_CONNECTION_ATTEMPTS = 0;
    private static final int DEFAULT_BETWEEN_ATTEMPTS_TIMEOUT_MILLIS = 2000;
    private static final long DEFAULT_CONNECTION_TIMEOUT_MILLIS = 20000L;
    private static final BigDecimal DEFAULT_SLEEP_FACTOR = new BigDecimal(1.5);

    private NativeNetconfClientConfigUtil() {
        throw new UnsupportedOperationException("Util class");
    }

    public static NetconfReconnectingClientConfiguration getClientConfig(
            final NetconfClientSessionListener listener, final NetconfNode node, final EventExecutor eventExecutor,
            final NativeNetconfKeystore keystore, final AAAEncryptionService encryptionService) {

        // setup default values since default value is not supported in mdsal
        final long clientConnectionTimeoutMillis = node.getConnectionTimeoutMillis() == null
                ? DEFAULT_CONNECTION_TIMEOUT_MILLIS
                : node.getConnectionTimeoutMillis().toJava();
        final long maxConnectionAttempts = node.getMaxConnectionAttempts() == null ? DEFAULT_MAX_CONNECTION_ATTEMPTS
                : node.getMaxConnectionAttempts().toJava();
        final int betweenAttemptsTimeoutMillis = node.getBetweenAttemptsTimeoutMillis() == null
                ? DEFAULT_BETWEEN_ATTEMPTS_TIMEOUT_MILLIS
                : node.getBetweenAttemptsTimeoutMillis().toJava();
        final boolean useTcp = node.isTcpOnly() == null ? DEFAULT_IS_TCP_ONLY : node.isTcpOnly();
        final BigDecimal sleepFactor = node.getSleepFactor() == null ? DEFAULT_SLEEP_FACTOR : node.getSleepFactor();

        final InetSocketAddress socketAddress = getSocketAddress(node.getHost(), node.getPort().getValue().toJava());

        final ReconnectStrategyFactory sf = new TimedReconnectStrategyFactory(eventExecutor, maxConnectionAttempts,
                betweenAttemptsTimeoutMillis, sleepFactor);

        final NetconfReconnectingClientConfigurationBuilder reconnectingClientConfigurationBuilder;
        final Protocol protocol = node.getProtocol();
        if (useTcp) {
            reconnectingClientConfigurationBuilder = NetconfReconnectingClientConfigurationBuilder.create()
                    .withProtocol(NetconfClientConfiguration.NetconfClientProtocol.TCP)
                    .withAuthHandler(getHandlerFromCredentials(node.getCredentials(), encryptionService, keystore));
        } else if (protocol == null || protocol.getName() == Name.SSH) {
            reconnectingClientConfigurationBuilder = NetconfReconnectingClientConfigurationBuilder.create()
                    .withProtocol(NetconfClientConfiguration.NetconfClientProtocol.SSH)
                    .withAuthHandler(getHandlerFromCredentials(node.getCredentials(), encryptionService, keystore));
        } else if (protocol.getName() == Name.TLS) {
            reconnectingClientConfigurationBuilder = NetconfReconnectingClientConfigurationBuilder.create()
                    .withSslHandlerFactory(new SslHandlerFactoryImpl(keystore, protocol.getSpecification()))
                    .withProtocol(NetconfClientConfiguration.NetconfClientProtocol.TLS);
        } else {
            throw new IllegalStateException("Unsupported protocol type: " + protocol.getName());
        }

        if (node.getOdlHelloMessageCapabilities() != null) {
            reconnectingClientConfigurationBuilder
                    .withOdlHelloCapabilities(node.getOdlHelloMessageCapabilities().getCapability());
        }

        return reconnectingClientConfigurationBuilder.withAddress(socketAddress)
                .withConnectionTimeoutMillis(clientConnectionTimeoutMillis)
                .withReconnectStrategy(sf.createReconnectStrategy()).withConnectStrategyFactory(sf)
                .withSessionListener(listener).build();
    }

    private static AuthenticationHandler getHandlerFromCredentials(final Credentials credentials,
            final AAAEncryptionService encryptionService, NativeNetconfKeystore keystore) {
        if (credentials instanceof org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114
                .netconf.node.credentials.credentials.LoginPassword) {
            final org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node
                .credentials.credentials.LoginPassword loginPassword = (org.opendaylight.yang.gen.v1.urn.opendaylight
                    .netconf.node.topology.rev150114.netconf.node.credentials.credentials.LoginPassword) credentials;
            return new LoginPasswordHandler(loginPassword.getUsername(), loginPassword.getPassword());
        }
        if (credentials instanceof LoginPwUnencrypted) {
            final LoginPasswordUnencrypted loginPassword = ((LoginPwUnencrypted) credentials)
                    .getLoginPasswordUnencrypted();
            return new LoginPasswordHandler(loginPassword.getUsername(), loginPassword.getPassword());
        }
        if (credentials instanceof LoginPw) {
            final LoginPassword loginPassword = ((LoginPw) credentials).getLoginPassword();
            return new LoginPasswordHandler(loginPassword.getUsername(),
                    encryptionService.decrypt(loginPassword.getPassword()));
        }
        if (credentials instanceof KeyAuth) {
            final KeyBased keyPair = ((KeyAuth) credentials).getKeyBased();
            return new DatastoreBackedPublicKeyAuth(keyPair.getUsername(), keyPair.getKeyId(),
                    keystore, encryptionService);
        }
        throw new IllegalStateException("Unsupported credential type: " + credentials.getClass());
    }

    private static InetSocketAddress getSocketAddress(final Host host, final int port) {
        if (host.getDomainName() != null) {
            return new InetSocketAddress(host.getDomainName().getValue(), port);
        }

        final IpAddress ipAddress = host.getIpAddress();
        final String ip = ipAddress.getIpv4Address() != null ? ipAddress.getIpv4Address().getValue()
                : ipAddress.getIpv6Address().getValue();
        return new InetSocketAddress(ip, port);
    }

}
