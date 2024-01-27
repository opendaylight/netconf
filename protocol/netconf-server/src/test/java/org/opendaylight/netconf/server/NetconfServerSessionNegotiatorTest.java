/*
 * Copyright (c) 2014, 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.netty.channel.local.LocalAddress;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;

class NetconfServerSessionNegotiatorTest {
    @Test
    void testPortTen() {
        final var address = new InetSocketAddress(10);
        final var hostname = NetconfServerSessionNegotiator.getHostName(address);
        assertNotNull(hostname);
        assertEquals(address.getHostName(), hostname.getValue());
    }

    @Test
    void testPortTwenty() {
        final var address = new InetSocketAddress("TestPortInet", 20);
        final var hostname = NetconfServerSessionNegotiator.getHostName(address);
        assertEquals(address.getHostName(), hostname.getValue());
        assertEquals(String.valueOf(address.getPort()), hostname.getKey());
    }

    @Test
    void testGetInetSocketAddress() {
        final var address = new LocalAddress("TestPortLocal");
        final var hostname = NetconfServerSessionNegotiator.getHostName(address);
        assertEquals(String.valueOf(address.id()), hostname.getValue());
    }
}
