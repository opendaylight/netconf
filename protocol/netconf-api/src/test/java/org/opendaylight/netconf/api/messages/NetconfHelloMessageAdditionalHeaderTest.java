/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api.messages;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class NetconfHelloMessageAdditionalHeaderTest {
    private final NetconfHelloMessageAdditionalHeader header =
        new NetconfHelloMessageAdditionalHeader("user", "1.1.1.1", "40", "tcp", "client");

    @Test
    void testGetters() {
        assertEquals(header.getAddress(), "1.1.1.1");
        assertEquals(header.getUserName(), "user");
        assertEquals(header.getPort(), "40");
        assertEquals(header.getTransport(), "tcp");
        assertEquals(header.getSessionIdentifier(), "client");
    }

    @Test
    void testStaticConstructor() {
        final var hdr = NetconfHelloMessageAdditionalHeader.fromString("[user;1.1.1.1:40;tcp;client;]");
        assertEquals(hdr.toString(), header.toString());
        assertEquals(hdr.toFormattedString(), header.toFormattedString());
    }
}
