/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.conf;

import java.net.InetSocketAddress;
import java.util.List;
import org.opendaylight.netconf.api.messages.NetconfHelloMessageAdditionalHeader;
import org.opendaylight.netconf.client.NetconfClientSessionListener;
import org.opendaylight.netconf.client.SslHandlerFactory;
import org.opendaylight.netconf.nettyutil.ReconnectStrategy;
import org.opendaylight.netconf.nettyutil.ReconnectStrategyFactory;
import org.opendaylight.netconf.nettyutil.handler.ssh.authentication.AuthenticationHandler;
import org.opendaylight.netconf.nettyutil.handler.ssh.client.NetconfSshClient;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;

public final class NetconfReconnectingClientConfigurationBuilder extends NetconfClientConfigurationBuilder {

    private ReconnectStrategyFactory connectStrategyFactory;

    private NetconfReconnectingClientConfigurationBuilder() {
    }

    public static NetconfReconnectingClientConfigurationBuilder create() {
        return new NetconfReconnectingClientConfigurationBuilder();
    }

    @SuppressWarnings("checkstyle:hiddenField")
    public NetconfReconnectingClientConfigurationBuilder withConnectStrategyFactory(
            final ReconnectStrategyFactory connectStrategyFactory) {
        this.connectStrategyFactory = connectStrategyFactory;
        return this;
    }

    @Override
    public NetconfReconnectingClientConfiguration build() {
        return new NetconfReconnectingClientConfiguration(getNodeId(), getProtocol(), getAddress(),
                getConnectionTimeoutMillis(), getAdditionalHeader(), getSessionListener(), getReconnectStrategy(),
                connectStrategyFactory, getAuthHandler(), getSslHandlerFactory(), getSshClient(),
                getOdlHelloCapabilities(), getMaximumIncomingChunkSize());
    }

    // Override setter methods to return subtype

    @Override
    public NetconfReconnectingClientConfigurationBuilder withNodeId(final String nodeId) {
        return (NetconfReconnectingClientConfigurationBuilder) super.withNodeId(nodeId);
    }

    @Override
    public NetconfReconnectingClientConfigurationBuilder withAddress(final InetSocketAddress address) {
        return (NetconfReconnectingClientConfigurationBuilder) super.withAddress(address);
    }

    @Override
    public NetconfReconnectingClientConfigurationBuilder withConnectionTimeoutMillis(
            final long connectionTimeoutMillis) {
        return (NetconfReconnectingClientConfigurationBuilder)
                super.withConnectionTimeoutMillis(connectionTimeoutMillis);
    }

    @Override
    public NetconfReconnectingClientConfigurationBuilder withAdditionalHeader(
            final NetconfHelloMessageAdditionalHeader additionalHeader) {
        return (NetconfReconnectingClientConfigurationBuilder) super.withAdditionalHeader(additionalHeader);
    }

    @Override
    public NetconfReconnectingClientConfigurationBuilder withSessionListener(
            final NetconfClientSessionListener sessionListener) {
        return (NetconfReconnectingClientConfigurationBuilder) super.withSessionListener(sessionListener);
    }

    @Override
    public NetconfReconnectingClientConfigurationBuilder withReconnectStrategy(
            final ReconnectStrategy reconnectStrategy) {
        return (NetconfReconnectingClientConfigurationBuilder) super.withReconnectStrategy(reconnectStrategy);
    }

    @Override
    public NetconfReconnectingClientConfigurationBuilder withAuthHandler(final AuthenticationHandler authHandler) {
        return (NetconfReconnectingClientConfigurationBuilder) super.withAuthHandler(authHandler);
    }

    @Override
    public NetconfReconnectingClientConfigurationBuilder withProtocol(
            final NetconfClientConfiguration.NetconfClientProtocol clientProtocol) {
        return (NetconfReconnectingClientConfigurationBuilder) super.withProtocol(clientProtocol);
    }

    @Override
    public NetconfReconnectingClientConfigurationBuilder withSslHandlerFactory(
            final SslHandlerFactory sslHandlerFactory) {
        return (NetconfReconnectingClientConfigurationBuilder) super.withSslHandlerFactory(sslHandlerFactory);
    }

    @Override
    public NetconfReconnectingClientConfigurationBuilder withSshClient(
        final NetconfSshClient sshClient) {
        return (NetconfReconnectingClientConfigurationBuilder) super.withSshClient(sshClient);
    }

    @Override
    public NetconfReconnectingClientConfigurationBuilder withOdlHelloCapabilities(
            final List<Uri> odlHelloCapabilities) {
        return (NetconfReconnectingClientConfigurationBuilder) super.withOdlHelloCapabilities(odlHelloCapabilities);
    }
}
