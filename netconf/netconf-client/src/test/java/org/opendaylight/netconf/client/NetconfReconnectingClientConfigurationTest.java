/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.client;

import java.net.InetSocketAddress;
import java.util.Optional;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.opendaylight.netconf.api.messages.NetconfHelloMessageAdditionalHeader;
import org.opendaylight.netconf.client.conf.NetconfClientConfiguration;
import org.opendaylight.netconf.client.conf.NetconfReconnectingClientConfiguration;
import org.opendaylight.netconf.client.conf.NetconfReconnectingClientConfigurationBuilder;
import org.opendaylight.netconf.nettyutil.ReconnectStrategy;
import org.opendaylight.netconf.nettyutil.ReconnectStrategyFactory;
import org.opendaylight.netconf.nettyutil.handler.ssh.authentication.AuthenticationHandler;

public class NetconfReconnectingClientConfigurationTest {
    @Test
    public void testNetconfReconnectingClientConfiguration() throws Exception {
        Long timeout = 200L;
        NetconfHelloMessageAdditionalHeader header =
                new NetconfHelloMessageAdditionalHeader("a", "host", "port", "trans", "id");
        NetconfClientSessionListener listener = new SimpleNetconfClientSessionListener();
        InetSocketAddress address = InetSocketAddress.createUnresolved("host", 830);
        ReconnectStrategyFactory strategy = Mockito.mock(ReconnectStrategyFactory.class);
        AuthenticationHandler handler = Mockito.mock(AuthenticationHandler.class);
        ReconnectStrategy reconnect = Mockito.mock(ReconnectStrategy.class);

        NetconfReconnectingClientConfiguration cfg = NetconfReconnectingClientConfigurationBuilder.create()
                .withNodeId("test")
                .withProtocol(NetconfClientConfiguration.NetconfClientProtocol.SSH)
                .withAddress(address)
                .withConnectionTimeoutMillis(timeout)
                .withReconnectStrategy(reconnect)
                .withAdditionalHeader(header)
                .withSessionListener(listener)
                .withConnectStrategyFactory(strategy)
                .withAuthHandler(handler).build();

        Assert.assertEquals(timeout, cfg.getConnectionTimeoutMillis());
        Assert.assertEquals(Optional.of(header), cfg.getAdditionalHeader());
        Assert.assertEquals(listener, cfg.getSessionListener());
        Assert.assertEquals(handler, cfg.getAuthHandler());
        Assert.assertEquals(strategy, cfg.getConnectStrategyFactory());
        Assert.assertEquals(NetconfClientConfiguration.NetconfClientProtocol.SSH, cfg.getProtocol());
        Assert.assertEquals(address, cfg.getAddress());
        Assert.assertEquals(reconnect, cfg.getReconnectStrategy());

        SslHandlerFactory sslHandlerFactory = Mockito.mock(SslHandlerFactory.class);
        NetconfReconnectingClientConfiguration cfg2 = NetconfReconnectingClientConfigurationBuilder.create()
                .withNodeId("test")
                .withProtocol(NetconfClientConfiguration.NetconfClientProtocol.TLS)
                .withAddress(address)
                .withConnectionTimeoutMillis(timeout)
                .withReconnectStrategy(reconnect)
                .withAdditionalHeader(header)
                .withSessionListener(listener)
                .withConnectStrategyFactory(strategy)
                .withSslHandlerFactory(sslHandlerFactory).build();

        Assert.assertEquals(timeout, cfg2.getConnectionTimeoutMillis());
        Assert.assertEquals(Optional.of(header), cfg2.getAdditionalHeader());
        Assert.assertEquals(listener, cfg2.getSessionListener());
        Assert.assertEquals(sslHandlerFactory, cfg2.getSslHandlerFactory());
        Assert.assertEquals(strategy, cfg2.getConnectStrategyFactory());
        Assert.assertEquals(NetconfClientConfiguration.NetconfClientProtocol.TLS, cfg2.getProtocol());
        Assert.assertEquals(address, cfg2.getAddress());
        Assert.assertEquals(reconnect, cfg2.getReconnectStrategy());
    }
}
