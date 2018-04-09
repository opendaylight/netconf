/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.conf;

import com.google.common.base.MoreObjects.ToStringHelper;
import com.google.common.base.Preconditions;
import java.net.InetSocketAddress;
import java.util.List;

import org.opendaylight.netconf.api.messages.NetconfHelloMessageAdditionalHeader;
import org.opendaylight.netconf.client.NetconfClientSessionListener;
import org.opendaylight.netconf.client.SslHandlerFactory;
import org.opendaylight.netconf.nettyutil.handler.ssh.authentication.AuthenticationHandler;
import org.opendaylight.protocol.framework.ReconnectStrategy;
import org.opendaylight.protocol.framework.ReconnectStrategyFactory;

public final class NetconfReconnectingClientConfiguration extends NetconfClientConfiguration {

    private final ReconnectStrategyFactory connectStrategyFactory;

    NetconfReconnectingClientConfiguration(final NetconfClientProtocol clientProtocol, final InetSocketAddress address,
                                           final Long connectionTimeoutMillis,
                                           final NetconfHelloMessageAdditionalHeader additionalHeader,
                                           final NetconfClientSessionListener sessionListener,
                                           final ReconnectStrategy reconnectStrategy,
                                           final ReconnectStrategyFactory connectStrategyFactory,
                                           final AuthenticationHandler authHandler,
                                           final SslHandlerFactory sslHandlerFactory,
                                           final List<String> odlHelloCapabilities) {
        super(clientProtocol, address, connectionTimeoutMillis, additionalHeader, sessionListener, reconnectStrategy,
                authHandler, sslHandlerFactory, odlHelloCapabilities);
        this.connectStrategyFactory = connectStrategyFactory;
        validateReconnectConfiguration();
    }

    public ReconnectStrategyFactory getConnectStrategyFactory() {
        return connectStrategyFactory;
    }

    private void validateReconnectConfiguration() {
        Preconditions.checkNotNull(connectStrategyFactory);
    }

    @Override
    protected ToStringHelper buildToStringHelper() {
        return super.buildToStringHelper().add("connectStrategyFactory", connectStrategyFactory);
    }
}
