/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.impl;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.opendaylight.netconf.api.messages.NetconfHelloMessageAdditionalHeader;

public class AdditionalHeaderParserTest {

    @Test
    public void testParsing() throws Exception {
        String message = "[netconf;10.12.0.102:48528;ssh;;;;;;]";
        NetconfHelloMessageAdditionalHeader header = NetconfHelloMessageAdditionalHeader.fromString(message);
        assertEquals("netconf", header.getUserName());
        assertEquals("10.12.0.102", header.getAddress());
        assertEquals("ssh", header.getTransport());
    }

    @Test
    public void testParsing2() throws Exception {
        String message = "[tomas;10.0.0.0/10000;tcp;1000;1000;;/home/tomas;;]";
        NetconfHelloMessageAdditionalHeader header = NetconfHelloMessageAdditionalHeader.fromString(message);
        assertEquals("tomas", header.getUserName());
        assertEquals("10.0.0.0", header.getAddress());
        assertEquals("tcp", header.getTransport());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParsingNoUsername() throws Exception {
        String message = "[10.12.0.102:48528;ssh;;;;;;]";
        NetconfHelloMessageAdditionalHeader.fromString(message);
    }
}
