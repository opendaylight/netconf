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
import org.opendaylight.netconf.nettyutil.AbstractNetconfSessionNegotiator;
import org.opendaylight.netconf.nettyutil.handler.ssh.authentication.AuthenticationHandler;
import org.opendaylight.netconf.nettyutil.handler.ssh.client.NetconfSshClient;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;

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
        AbstractNetconfSessionNegotiator.DEFAULT_MAXIMUM_INCOMING_CHUNK_SIZE;
    private String name;

    protected NetconfClientConfigurationBuilder() {
    }

    public static NetconfClientConfigurationBuilder create() {
        return new NetconfClientConfigurationBuilder();
    }

    @SuppressWarnings("checkstyle:hiddenField")
    public NetconfClientConfigurationBuilder withAddress(final InetSocketAddress address) {
        this.address = address;
        return this;
    }

    @SuppressWarnings("checkstyle:hiddenField")
    public NetconfClientConfigurationBuilder withConnectionTimeoutMillis(final long connectionTimeoutMillis) {
        this.connectionTimeoutMillis = connectionTimeoutMillis;
        return this;
    }

    @SuppressWarnings("checkstyle:hiddenField")
    public NetconfClientConfigurationBuilder withProtocol(
            final NetconfClientConfiguration.NetconfClientProtocol clientProtocol) {
        this.clientProtocol = clientProtocol;
        return this;
    }

    @SuppressWarnings("checkstyle:hiddenField")
    public NetconfClientConfigurationBuilder withAdditionalHeader(
            final NetconfHelloMessageAdditionalHeader additionalHeader) {
        this.additionalHeader = additionalHeader;
        return this;
    }

    @SuppressWarnings("checkstyle:hiddenField")
    public NetconfClientConfigurationBuilder withSessionListener(final NetconfClientSessionListener sessionListener) {
        this.sessionListener = sessionListener;
        return this;
    }

    @SuppressWarnings("checkstyle:hiddenField")
    public NetconfClientConfigurationBuilder withAuthHandler(final AuthenticationHandler authHandler) {
        this.authHandler = authHandler;
        return this;
    }

    @SuppressWarnings("checkstyle:hiddenField")
    public NetconfClientConfigurationBuilder withSslHandlerFactory(final SslHandlerFactory sslHandlerFactory) {
        this.sslHandlerFactory = sslHandlerFactory;
        return this;
    }

    @SuppressWarnings("checkstyle:hiddenField")
    public NetconfClientConfigurationBuilder withSshClient(final NetconfSshClient sshClient) {
        this.sshClient = sshClient;
        return this;
    }

    @SuppressWarnings("checkstyle:hiddenField")
    public NetconfClientConfigurationBuilder withName(final String name) {
        this.name = name;
        return this;
    }

    @SuppressWarnings("checkstyle:hiddenField")
    public NetconfClientConfigurationBuilder withOdlHelloCapabilities(final List<Uri> odlHelloCapabilities) {
        this.odlHelloCapabilities = odlHelloCapabilities;
        return this;
    }

    @SuppressWarnings("checkstyle:hiddenField")
    public NetconfClientConfigurationBuilder withMaximumIncomingChunkSize(
            final @NonNegative int maximumIncomingChunkSize) {
        checkArgument(maximumIncomingChunkSize > 0);
        this.maximumIncomingChunkSize  = maximumIncomingChunkSize;
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

    public NetconfClientConfiguration build() {
        return new NetconfClientConfiguration(clientProtocol, address, connectionTimeoutMillis, additionalHeader,
                sessionListener, authHandler, sslHandlerFactory, sshClient, odlHelloCapabilities,
                maximumIncomingChunkSize, name);
    }
}
