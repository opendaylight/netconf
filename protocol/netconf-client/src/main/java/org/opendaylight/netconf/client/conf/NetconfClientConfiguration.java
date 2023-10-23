/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.conf;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Optional;
import org.checkerframework.checker.index.qual.NonNegative;
import org.opendaylight.netconf.api.messages.NetconfHelloMessageAdditionalHeader;
import org.opendaylight.netconf.client.NetconfClientSessionListener;
import org.opendaylight.netconf.client.SslHandlerFactory;
import org.opendaylight.netconf.nettyutil.handler.ssh.authentication.AuthenticationHandler;
import org.opendaylight.netconf.nettyutil.handler.ssh.client.NetconfSshClient;
import org.opendaylight.netconf.transport.ssh.ClientFactoryManagerConfigurator;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.client.rev230417.SshClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.client.rev230417.TcpClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.server.rev230417.TcpServerGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.client.rev230417.TlsClientGrouping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetconfClientConfiguration {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfClientConfiguration.class);

    private final NetconfClientProtocol clientProtocol;
    private final InetSocketAddress address;
    private final Long connectionTimeoutMillis;

    private final NetconfHelloMessageAdditionalHeader additionalHeader;
    private final NetconfClientSessionListener sessionListener;

    private final AuthenticationHandler authHandler;
    private final SslHandlerFactory sslHandlerFactory;
    private final NetconfSshClient sshClient;

    private final List<Uri> odlHelloCapabilities;
    private final @NonNegative int maximumIncomingChunkSize;
    private final String name;

    private final TcpClientGrouping tcpParameters;
    private final TcpServerGrouping tcpServerParameters;
    private final TlsClientGrouping tlsParameters;
    private final org.opendaylight.netconf.transport.tls.SslHandlerFactory transportSslHandlerFactory;
    private final SshClientGrouping sshParameters;
    private final ClientFactoryManagerConfigurator sshConfigurator;

    NetconfClientConfiguration(final NetconfClientProtocol protocol, final InetSocketAddress address,
            final Long connectionTimeoutMillis,
            final NetconfHelloMessageAdditionalHeader additionalHeader,
            final NetconfClientSessionListener sessionListener,
            final AuthenticationHandler authHandler,
            final SslHandlerFactory sslHandlerFactory, final NetconfSshClient sshClient,
            final List<Uri> odlHelloCapabilities, final @NonNegative int maximumIncomingChunkSize,
            final String name) {
        this.address = address;
        this.connectionTimeoutMillis = connectionTimeoutMillis;
        this.additionalHeader = additionalHeader;
        this.sessionListener = sessionListener;
        clientProtocol = protocol;
        this.authHandler = authHandler;
        this.sslHandlerFactory = sslHandlerFactory;
        this.sshClient = sshClient;
        this.odlHelloCapabilities = odlHelloCapabilities;
        this.maximumIncomingChunkSize = maximumIncomingChunkSize;
        this.name = name;
        this.tcpParameters = null;
        this.tcpServerParameters = null;
        this.tlsParameters = null;
        this.transportSslHandlerFactory = null;
        this.sshParameters = null;
        this.sshConfigurator = null;
        validateConfiguration();
    }

    NetconfClientConfiguration(final NetconfClientProtocol protocol,
            final TcpClientGrouping tcpParameters,
            final TcpServerGrouping tcpServerParameters,
            final TlsClientGrouping tlsParameters,
            final org.opendaylight.netconf.transport.tls.SslHandlerFactory transportSslHandlerFactory,
            final SshClientGrouping sshParameters,
            final ClientFactoryManagerConfigurator sshConfigurator,
            final NetconfClientSessionListener sessionListener,
            final List<Uri> odlHelloCapabilities,
            final Long connectionTimeoutMillis,
            final @NonNegative int maximumIncomingChunkSize,
            final NetconfHelloMessageAdditionalHeader additionalHeader,
            final String name) {
        this.clientProtocol = requireNonNull(protocol);
        this.tcpParameters = tcpParameters;
        this.tcpServerParameters = tcpServerParameters;
        this.tlsParameters = tlsParameters;
        this.transportSslHandlerFactory = transportSslHandlerFactory;
        this.sshParameters = sshParameters;
        this.sshConfigurator = sshConfigurator;
        this.sessionListener = requireNonNull(sessionListener);
        this.odlHelloCapabilities = odlHelloCapabilities;
        this.connectionTimeoutMillis = connectionTimeoutMillis;
        this.maximumIncomingChunkSize = maximumIncomingChunkSize;
        this.additionalHeader = additionalHeader;
        this.name = name;
        this.address = null;
        this.authHandler = null;
        this.sslHandlerFactory = null;
        this.sshClient = null;
        // validate
        checkArgument(tcpParameters != null || tcpServerParameters != null,
            "Either tcpParameters or tcpServerParameters is required");
        if (NetconfClientProtocol.TLS.equals(protocol)) {
            checkArgument(tlsParameters != null || transportSslHandlerFactory != null,
                "Either tlsParameters or sslHandlerFactory is required");
        } else if (NetconfClientProtocol.SSH.equals(protocol)) {
            requireNonNull(sshParameters);
        }
    }

    public final String getName() {
        return name;
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

    public @NonNegative int getMaximumIncomingChunkSize() {
        return maximumIncomingChunkSize;
    }

    public final TcpClientGrouping getTcpParameters() {
        return tcpParameters;
    }

    public final TcpServerGrouping getTcpServerParameters() {
        return tcpServerParameters;
    }

    public final  TlsClientGrouping getTlsParameters() {
        return tlsParameters;
    }

    public final org.opendaylight.netconf.transport.tls.SslHandlerFactory getTransportSslHandlerFactory() {
        return transportSslHandlerFactory;
    }

    public final SshClientGrouping getSshParameters() {
        return sshParameters;
    }

    public ClientFactoryManagerConfigurator getSshConfigurator() {
        return sshConfigurator;
    }

    private void validateConfiguration() {
        switch (requireNonNull(clientProtocol)) {
            case TLS:
                validateTcpConfiguration();
                validateTlsConfiguration();
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

    protected final void validateTlsConfiguration() {
        requireNonNull(sslHandlerFactory, "sslHandlerFactory");
    }

    protected final void validateSshConfiguration() {
        requireNonNull(authHandler, "authHandler");
    }

    protected final void validateTcpConfiguration() {
        requireNonNull(address, "address");
        requireNonNull(clientProtocol, "clientProtocol");
        requireNonNull(connectionTimeoutMillis, "connectionTimeoutMillis");
        requireNonNull(sessionListener, "sessionListener");
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
                .add("clientProtocol", clientProtocol)
                .add("authHandler", authHandler)
                .add("sslHandlerFactory", sslHandlerFactory);
    }

    public enum NetconfClientProtocol {
        TCP, SSH, TLS
    }
}
