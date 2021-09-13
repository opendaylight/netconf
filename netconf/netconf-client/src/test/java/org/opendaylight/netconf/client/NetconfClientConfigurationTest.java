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
import org.opendaylight.netconf.client.conf.NetconfClientConfigurationBuilder;
import org.opendaylight.netconf.nettyutil.ReconnectStrategy;
import org.opendaylight.netconf.nettyutil.handler.ssh.authentication.AuthenticationHandler;

public class NetconfClientConfigurationTest {
    @Test
    public void testNetconfClientConfiguration() {
        final String nodeId = "test-node";
        Long timeout = 200L;
        NetconfHelloMessageAdditionalHeader header =
                new NetconfHelloMessageAdditionalHeader("a", "host", "port", "trans", "id");
        NetconfClientSessionListener listener = new SimpleNetconfClientSessionListener();
        InetSocketAddress address = InetSocketAddress.createUnresolved("host", 830);
        ReconnectStrategy strategy = Mockito.mock(ReconnectStrategy.class);
        AuthenticationHandler handler = Mockito.mock(AuthenticationHandler.class);
        NetconfClientConfiguration cfg = NetconfClientConfigurationBuilder.create()
                .withNodeId(nodeId)
                .withProtocol(NetconfClientConfiguration.NetconfClientProtocol.SSH)
                .withAddress(address)
                .withConnectionTimeoutMillis(timeout)
                .withReconnectStrategy(strategy)
                .withAdditionalHeader(header)
                .withSessionListener(listener)
                .withAuthHandler(handler).build();

        Assert.assertEquals(timeout, cfg.getConnectionTimeoutMillis());
        Assert.assertEquals(Optional.of(header), cfg.getAdditionalHeader());
        Assert.assertEquals(listener, cfg.getSessionListener());
        Assert.assertEquals(handler, cfg.getAuthHandler());
        Assert.assertEquals(strategy, cfg.getReconnectStrategy());
        Assert.assertEquals(NetconfClientConfiguration.NetconfClientProtocol.SSH, cfg.getProtocol());
        Assert.assertEquals(address, cfg.getAddress());
        Assert.assertEquals(nodeId, cfg.getNodeId());

        SslHandlerFactory sslHandlerFactory = Mockito.mock(SslHandlerFactory.class);
        NetconfClientConfiguration cfg2 = NetconfClientConfigurationBuilder.create()
                .withNodeId(nodeId)
                .withProtocol(NetconfClientConfiguration.NetconfClientProtocol.TLS)
                .withAddress(address)
                .withConnectionTimeoutMillis(timeout)
                .withReconnectStrategy(strategy)
                .withAdditionalHeader(header)
                .withSessionListener(listener)
                .withSslHandlerFactory(sslHandlerFactory).build();

        Assert.assertEquals(timeout, cfg2.getConnectionTimeoutMillis());
        Assert.assertEquals(Optional.of(header), cfg2.getAdditionalHeader());
        Assert.assertEquals(listener, cfg2.getSessionListener());
        Assert.assertEquals(sslHandlerFactory, cfg2.getSslHandlerFactory());
        Assert.assertEquals(strategy, cfg2.getReconnectStrategy());
        Assert.assertEquals(NetconfClientConfiguration.NetconfClientProtocol.TLS, cfg2.getProtocol());
        Assert.assertEquals(address, cfg2.getAddress());
        Assert.assertEquals(nodeId, cfg2.getNodeId());
    }
}
