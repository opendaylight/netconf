/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.conf;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects.ToStringHelper;
import java.net.InetSocketAddress;
import java.util.List;
import org.checkerframework.checker.index.qual.NonNegative;
import org.opendaylight.netconf.api.messages.NetconfHelloMessageAdditionalHeader;
import org.opendaylight.netconf.client.NetconfClientSessionListener;
import org.opendaylight.netconf.client.SslHandlerFactory;
import org.opendaylight.netconf.nettyutil.ReconnectStrategy;
import org.opendaylight.netconf.nettyutil.ReconnectStrategyFactory;
import org.opendaylight.netconf.nettyutil.handler.ssh.authentication.AuthenticationHandler;
import org.opendaylight.netconf.nettyutil.handler.ssh.client.NetconfSshClient;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;

public final class NetconfReconnectingClientConfiguration extends NetconfClientConfiguration {

    private final ReconnectStrategyFactory connectStrategyFactory;

    NetconfReconnectingClientConfiguration(final String nodeId, final NetconfClientProtocol clientProtocol,
                                           final InetSocketAddress address, final Long connectionTimeoutMillis,
                                           final NetconfHelloMessageAdditionalHeader additionalHeader,
                                           final NetconfClientSessionListener sessionListener,
                                           final ReconnectStrategy reconnectStrategy,
                                           final ReconnectStrategyFactory connectStrategyFactory,
                                           final AuthenticationHandler authHandler,
                                           final SslHandlerFactory sslHandlerFactory,
                                           final NetconfSshClient sshClient,
                                           final List<Uri> odlHelloCapabilities,
                                           final @NonNegative int maximumIncomingChunkSize) {
        super(nodeId, clientProtocol, address, connectionTimeoutMillis, additionalHeader, sessionListener,
                reconnectStrategy, authHandler, sslHandlerFactory, sshClient, odlHelloCapabilities,
                maximumIncomingChunkSize);
        this.connectStrategyFactory = connectStrategyFactory;
        validateReconnectConfiguration();
    }

    public ReconnectStrategyFactory getConnectStrategyFactory() {
        return connectStrategyFactory;
    }

    private void validateReconnectConfiguration() {
        requireNonNull(connectStrategyFactory);
    }

    @Override
    protected ToStringHelper buildToStringHelper() {
        return super.buildToStringHelper().add("connectStrategyFactory", connectStrategyFactory);
    }
}
