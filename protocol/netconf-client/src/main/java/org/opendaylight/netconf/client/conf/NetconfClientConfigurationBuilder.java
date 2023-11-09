/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.conf;

import static com.google.common.base.Preconditions.checkArgument;

import java.net.InetSocketAddress;
import java.util.List;
import org.checkerframework.checker.index.qual.NonNegative;
import org.opendaylight.netconf.api.messages.NetconfHelloMessageAdditionalHeader;
import org.opendaylight.netconf.client.NetconfClientSessionListener;
import org.opendaylight.netconf.client.SslHandlerFactory;
import org.opendaylight.netconf.nettyutil.NetconfSessionNegotiator;
import org.opendaylight.netconf.nettyutil.handler.ssh.authentication.AuthenticationHandler;
import org.opendaylight.netconf.nettyutil.handler.ssh.client.NetconfSshClient;
import org.opendaylight.netconf.transport.ssh.ClientFactoryManagerConfigurator;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.ssh.client.rev230417.SshClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tcp.client.rev230417.TcpClientGrouping;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.tls.client.rev230417.TlsClientGrouping;

/**
 * Builder for {@link NetconfClientConfiguration}.
 */
public class NetconfClientConfigurationBuilder {

    public static final int DEFAULT_CONNECTION_TIMEOUT_MILLIS = 5000;
    public static final NetconfClientConfiguration.NetconfClientProtocol DEFAULT_CLIENT_PROTOCOL =
        NetconfClientConfiguration.NetconfClientProtocol.TCP;

    private InetSocketAddress address;
    private long connectionTimeoutMillis = DEFAULT_CONNECTION_TIMEOUT_MILLIS;
    private NetconfHelloMessageAdditionalHeader additionalHeader;
    private NetconfClientSessionListener sessionListener;
    private AuthenticationHandler authHandler;
    private NetconfClientConfiguration.NetconfClientProtocol clientProtocol = DEFAULT_CLIENT_PROTOCOL;
    private SslHandlerFactory sslHandlerFactory;
    private NetconfSshClient sshClient;
    private List<Uri> odlHelloCapabilities;
    private @NonNegative int maximumIncomingChunkSize =
        NetconfSessionNegotiator.DEFAULT_MAXIMUM_INCOMING_CHUNK_SIZE;
    private String name;
    private TcpClientGrouping tcpParameters;
    private TlsClientGrouping tlsParameters;
    private org.opendaylight.netconf.transport.tls.SslHandlerFactory transportSslHandlerFactory;
    private SshClientGrouping sshParameters;
    private ClientFactoryManagerConfigurator sshConfigurator;

    protected NetconfClientConfigurationBuilder() {
    }

    public static NetconfClientConfigurationBuilder create() {
        return new NetconfClientConfigurationBuilder();
    }

    /**
     * Set remote address.
     *
     * @param address remote address
     * @return current builder instance
     * @deprecated due to design change. Only used with NetconfClientDispatcher,
     *     ignored when building configuration for {@link org.opendaylight.netconf.client.NetconfClientFactory} which
     *     expects remote address defined via TCP transport configuration {@link #withTcpParameters(TcpClientGrouping)}
     */
    @Deprecated
    @SuppressWarnings("checkstyle:hiddenField")
    public NetconfClientConfigurationBuilder withAddress(final InetSocketAddress address) {
        this.address = address;
        return this;
    }

    /**
     * Set connection timeout value in milliseconds.
     *
     * @param connectionTimeoutMillis value
     * @return current builder instance
     */
    @SuppressWarnings("checkstyle:hiddenField")
    public NetconfClientConfigurationBuilder withConnectionTimeoutMillis(final long connectionTimeoutMillis) {
        this.connectionTimeoutMillis = connectionTimeoutMillis;
        return this;
    }

    /**
     * Set client protocol.
     *
     * @param clientProtocol client protocol
     * @return current builder instance
     */
    @SuppressWarnings("checkstyle:hiddenField")
    public NetconfClientConfigurationBuilder withProtocol(
        final NetconfClientConfiguration.NetconfClientProtocol clientProtocol) {
        this.clientProtocol = clientProtocol;
        return this;
    }

    /**
     * Set additional header for Hello message.
     *
     * @param additionalHeader additional header
     * @return current builder instance
     */
    @SuppressWarnings("checkstyle:hiddenField")
    public NetconfClientConfigurationBuilder withAdditionalHeader(
        final NetconfHelloMessageAdditionalHeader additionalHeader) {
        this.additionalHeader = additionalHeader;
        return this;
    }

    /**
     * Set NETCONF session client listener.
     *
     * @param sessionListener session listener
     * @return current builder instance
     */
    @SuppressWarnings("checkstyle:hiddenField")
    public NetconfClientConfigurationBuilder withSessionListener(final NetconfClientSessionListener sessionListener) {
        this.sessionListener = sessionListener;
        return this;
    }

    /**
     * Set authentication handler. Used for SSH authentication.
     *
     * @param authHandler authentication handler
     * @return current builder instance
     * @deprecated due to design change. Only used with NetconfClientDispatcher,
     *     ignored when building configuration for {@link org.opendaylight.netconf.client.NetconfClientFactory} which
     *     expects SSH transport overlay configuration via {@link #withSshParameters(SshClientGrouping)}
     */
    @Deprecated
    @SuppressWarnings("checkstyle:hiddenField")
    public NetconfClientConfigurationBuilder withAuthHandler(final AuthenticationHandler authHandler) {
        this.authHandler = authHandler;
        return this;
    }

    /**
     * Set SSL Handler factory.
     *
     * @param sslHandlerFactory ssh handler instance
     * @return current builder instance
     * @deprecated due to design change. Only used with NetconfClientDispatcher,
     *     ignored when building configuration for {@link org.opendaylight.netconf.client.NetconfClientFactory} which
     *     expects TLS transport overlay configuration via {@link #withTlsParameters(TlsClientGrouping)}
     */
    @Deprecated
    @SuppressWarnings("checkstyle:hiddenField")
    public NetconfClientConfigurationBuilder withSslHandlerFactory(final SslHandlerFactory sslHandlerFactory) {
        this.sslHandlerFactory = sslHandlerFactory;
        return this;
    }

    /**
     * Set SSH client instance for use as SSH transport overlay.
     *
     * @param sshClient ssh client instance
     * @return current builder instance
     * @deprecated due to design change. Only used with NetconfClientDispatcher,
     *     ignored when building configuration for {@link org.opendaylight.netconf.client.NetconfClientFactory} which
     *     expects SSH transport overlay configuration via {@link #withSshParameters(SshClientGrouping)}
     */
    @Deprecated
    @SuppressWarnings("checkstyle:hiddenField")
    public NetconfClientConfigurationBuilder withSshClient(final NetconfSshClient sshClient) {
        this.sshClient = sshClient;
        return this;
    }

    /**
     * Set client name.
     *
     * @param name value
     * @return current builder instance
     */
    @SuppressWarnings("checkstyle:hiddenField")
    public NetconfClientConfigurationBuilder withName(final String name) {
        this.name = name;
        return this;
    }

    /**
     * Set capabilities for Hello message.
     *
     * @param odlHelloCapabilities capabilities
     * @return current builder instance
     */
    @SuppressWarnings("checkstyle:hiddenField")
    public NetconfClientConfigurationBuilder withOdlHelloCapabilities(final List<Uri> odlHelloCapabilities) {
        this.odlHelloCapabilities = odlHelloCapabilities;
        return this;
    }

    /**
     * Set max size of incoming data chink in bytes. Positive value is required.
     *
     * @param maximumIncomingChunkSize value
     * @return current builder instance
     * @throws IllegalArgumentException if value zero or less
     */
    @SuppressWarnings("checkstyle:hiddenField")
    public NetconfClientConfigurationBuilder withMaximumIncomingChunkSize(
        final @NonNegative int maximumIncomingChunkSize) {
        checkArgument(maximumIncomingChunkSize > 0);
        this.maximumIncomingChunkSize = maximumIncomingChunkSize;
        return this;
    }

    /**
     * Set TCP client transport parameters.
     *
     * @param tcpParameters parameters
     * @return current builder instance
     */
    @SuppressWarnings("checkstyle:hiddenField")
    public NetconfClientConfigurationBuilder withTcpParameters(final TcpClientGrouping tcpParameters) {
        this.tcpParameters = tcpParameters;
        return this;
    }

    /**
     * Set TLS client transport parameters.
     *
     * @param tlsParameters parameters
     * @return current builder instance
     */
    @SuppressWarnings("checkstyle:hiddenField")
    public NetconfClientConfigurationBuilder withTlsParameters(final TlsClientGrouping tlsParameters) {
        this.tlsParameters = tlsParameters;
        return this;
    }

    /**
     * Set SslHandlerFactory for TLS transport.
     *
     * @param transportSslHandlerFactory ssl handler factory
     * @return current builder instance
     */
    @SuppressWarnings("checkstyle:hiddenField")
    public NetconfClientConfigurationBuilder withTransportSslHandlerFactory(
            final org.opendaylight.netconf.transport.tls.SslHandlerFactory transportSslHandlerFactory) {
        this.transportSslHandlerFactory = transportSslHandlerFactory;
        return this;
    }

    /**
     * Set SSH client transport parameters.
     *
     * @param sshParameters SSH parameters
     * @return current builder instance
     */
    @SuppressWarnings("checkstyle:hiddenField")
    public NetconfClientConfigurationBuilder withSshParameters(final SshClientGrouping sshParameters) {
        this.sshParameters = sshParameters;
        return this;
    }

    /**
     * Set SSH Client Factory Manager configurator.
     *
     * @param sshConfigurator configurator
     * @return current builder instance
     */
    @SuppressWarnings("checkstyle:hiddenField")
    public NetconfClientConfigurationBuilder withSshConfigurator(
            final ClientFactoryManagerConfigurator sshConfigurator) {
        this.sshConfigurator = sshConfigurator;
        return this;
    }

    final InetSocketAddress getAddress() {
        return address;
    }

    final long getConnectionTimeoutMillis() {
        return connectionTimeoutMillis;
    }

    final NetconfHelloMessageAdditionalHeader getAdditionalHeader() {
        return additionalHeader;
    }

    final NetconfClientSessionListener getSessionListener() {
        return sessionListener;
    }

    final AuthenticationHandler getAuthHandler() {
        return authHandler;
    }

    final NetconfClientConfiguration.NetconfClientProtocol getProtocol() {
        return clientProtocol;
    }

    final SslHandlerFactory getSslHandlerFactory() {
        return sslHandlerFactory;
    }

    public NetconfSshClient getSshClient() {
        return sshClient;
    }

    final List<Uri> getOdlHelloCapabilities() {
        return odlHelloCapabilities;
    }

    final @NonNegative int getMaximumIncomingChunkSize() {
        return maximumIncomingChunkSize;
    }

    final String getName() {
        return name;
    }

    /**
     * Builds configuration based on parameters provided.
     *
     * @return immutable configuration instance
     */
    public NetconfClientConfiguration build() {
        return tcpParameters == null
            // legacy configuration
            ? new NetconfClientConfiguration(clientProtocol, address, connectionTimeoutMillis, additionalHeader,
                sessionListener, authHandler, sslHandlerFactory, sshClient, odlHelloCapabilities,
                maximumIncomingChunkSize, name)
            // new configuration
            : new NetconfClientConfiguration(clientProtocol, tcpParameters, tlsParameters, transportSslHandlerFactory,
                sshParameters, sshConfigurator, sessionListener, odlHelloCapabilities, connectionTimeoutMillis,
                maximumIncomingChunkSize, additionalHeader, name);
    }
}
