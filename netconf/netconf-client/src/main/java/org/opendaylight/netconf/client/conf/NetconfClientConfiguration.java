/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.conf;

import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import com.google.common.base.Preconditions;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Optional;
import org.opendaylight.netconf.api.messages.NetconfHelloMessageAdditionalHeader;
import org.opendaylight.netconf.client.NetconfClientSessionListener;
import org.opendaylight.netconf.client.SslHandlerFactory;
import org.opendaylight.netconf.nettyutil.ReconnectStrategy;
import org.opendaylight.netconf.nettyutil.handler.ssh.authentication.AuthenticationHandler;
import org.opendaylight.netconf.nettyutil.handler.ssh.client.NetconfSshClient;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetconfClientConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(NetconfClientConfiguration.class);

    private final NetconfClientProtocol clientProtocol;
    private final InetSocketAddress address;
    private final Long connectionTimeoutMillis;

    private final NetconfHelloMessageAdditionalHeader additionalHeader;
    private final NetconfClientSessionListener sessionListener;

    private final ReconnectStrategy reconnectStrategy;

    private final AuthenticationHandler authHandler;
    private final SslHandlerFactory sslHandlerFactory;
    private final NetconfSshClient sshClient;

    private final List<Uri> odlHelloCapabilities;


    NetconfClientConfiguration(final NetconfClientProtocol protocol, final InetSocketAddress address,
                               final Long connectionTimeoutMillis,
                               final NetconfHelloMessageAdditionalHeader additionalHeader,
                               final NetconfClientSessionListener sessionListener,
                               final ReconnectStrategy reconnectStrategy, final AuthenticationHandler authHandler,
                               final SslHandlerFactory sslHandlerFactory, final NetconfSshClient sshClient,
                               final List<Uri> odlHelloCapabilities) {
        this.address = address;
        this.connectionTimeoutMillis = connectionTimeoutMillis;
        this.additionalHeader = additionalHeader;
        this.sessionListener = sessionListener;
        this.clientProtocol = protocol;
        this.reconnectStrategy = reconnectStrategy;
        this.authHandler = authHandler;
        this.sslHandlerFactory = sslHandlerFactory;
        this.sshClient = sshClient;
        this.odlHelloCapabilities = odlHelloCapabilities;
        validateConfiguration();
    }

    public final InetSocketAddress getAddress() {
        return address;
    }

    public final Long getConnectionTimeoutMillis() {
        return connectionTimeoutMillis;
    }

    public final Optional<NetconfHelloMessageAdditionalHeader> getAdditionalHeader() {
        return Optional.ofNullable(additionalHeader);
    }

    public final NetconfClientSessionListener getSessionListener() {
        return sessionListener;
    }

    public final ReconnectStrategy getReconnectStrategy() {
        return reconnectStrategy;
    }

    public final AuthenticationHandler getAuthHandler() {
        return authHandler;
    }

    public NetconfClientProtocol getProtocol() {
        return clientProtocol;
    }

    public SslHandlerFactory getSslHandlerFactory() {
        return sslHandlerFactory;
    }

    public NetconfSshClient getSshClient() {
        return sshClient;
    }

    public List<Uri> getOdlHelloCapabilities() {
        return odlHelloCapabilities;
    }

    private void validateConfiguration() {
        Preconditions.checkNotNull(clientProtocol, " ");
        switch (clientProtocol) {
            case TLS:
                validateTlsConfiguration();
                validateTcpConfiguration();
                break;
            case SSH:
                validateSshConfiguration();
                validateTcpConfiguration();
                break;
            case TCP:
                validateTcpConfiguration();
                break;
            default:
                LOG.warn("Unexpected protocol: {} in netconf client configuration.", clientProtocol);
        }
    }

    protected void validateTlsConfiguration() {
        Preconditions.checkNotNull(sslHandlerFactory, "sslHandlerFactory");
    }

    protected void validateSshConfiguration() {
        Preconditions.checkNotNull(authHandler, "authHandler");
    }

    protected void validateTcpConfiguration() {
        Preconditions.checkNotNull(address, "address");
        Preconditions.checkNotNull(clientProtocol, "clientProtocol");
        Preconditions.checkNotNull(connectionTimeoutMillis, "connectionTimeoutMillis");
        Preconditions.checkNotNull(sessionListener, "sessionListener");
        Preconditions.checkNotNull(reconnectStrategy, "reconnectStrategy");
    }

    @Override
    public final String toString() {
        return buildToStringHelper().toString();
    }

    protected ToStringHelper buildToStringHelper() {
        return MoreObjects.toStringHelper(this)
                .add("address", address)
                .add("connectionTimeoutMillis", connectionTimeoutMillis)
                .add("additionalHeader", additionalHeader)
                .add("sessionListener", sessionListener)
                .add("reconnectStrategy", reconnectStrategy)
                .add("clientProtocol", clientProtocol)
                .add("authHandler", authHandler)
                .add("sslHandlerFactory", sslHandlerFactory);
    }

    public enum NetconfClientProtocol {
        TCP, SSH, TLS
    }
}
