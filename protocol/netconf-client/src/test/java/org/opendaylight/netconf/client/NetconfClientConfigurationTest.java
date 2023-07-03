/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

import java.net.InetSocketAddress;
import java.util.Optional;
import org.junit.Test;
import org.opendaylight.netconf.api.messages.NetconfHelloMessageAdditionalHeader;
import org.opendaylight.netconf.client.conf.NetconfClientConfiguration;
import org.opendaylight.netconf.client.conf.NetconfClientConfigurationBuilder;
import org.opendaylight.netconf.nettyutil.handler.ssh.authentication.AuthenticationHandler;

public class NetconfClientConfigurationTest {
    @Test
    public void testNetconfClientConfiguration() throws Exception {
        Long timeout = 200L;
        NetconfHelloMessageAdditionalHeader header =
                new NetconfHelloMessageAdditionalHeader("a", "host", "port", "trans", "id");
        NetconfClientSessionListener listener = new SimpleNetconfClientSessionListener();
        InetSocketAddress address = InetSocketAddress.createUnresolved("host", 830);
        AuthenticationHandler handler = mock(AuthenticationHandler.class);
        NetconfClientConfiguration cfg = NetconfClientConfigurationBuilder.create()
                .withProtocol(NetconfClientConfiguration.NetconfClientProtocol.SSH)
                .withAddress(address)
                .withConnectionTimeoutMillis(timeout)
                .withAdditionalHeader(header)
                .withSessionListener(listener)
                .withAuthHandler(handler).build();

        assertEquals(timeout, cfg.getConnectionTimeoutMillis());
        assertEquals(Optional.of(header), cfg.getAdditionalHeader());
        assertEquals(listener, cfg.getSessionListener());
        assertEquals(handler, cfg.getAuthHandler());
        assertEquals(NetconfClientConfiguration.NetconfClientProtocol.SSH, cfg.getProtocol());
        assertEquals(address, cfg.getAddress());

        SslHandlerFactory sslHandlerFactory = mock(SslHandlerFactory.class);
        NetconfClientConfiguration cfg2 = NetconfClientConfigurationBuilder.create()
                .withProtocol(NetconfClientConfiguration.NetconfClientProtocol.TLS)
                .withAddress(address)
                .withConnectionTimeoutMillis(timeout)
                .withAdditionalHeader(header)
                .withSessionListener(listener)
                .withSslHandlerFactory(sslHandlerFactory).build();

        assertEquals(timeout, cfg2.getConnectionTimeoutMillis());
        assertEquals(Optional.of(header), cfg2.getAdditionalHeader());
        assertEquals(listener, cfg2.getSessionListener());
        assertEquals(sslHandlerFactory, cfg2.getSslHandlerFactory());
        assertEquals(NetconfClientConfiguration.NetconfClientProtocol.TLS, cfg2.getProtocol());
        assertEquals(address, cfg2.getAddress());
    }
}
