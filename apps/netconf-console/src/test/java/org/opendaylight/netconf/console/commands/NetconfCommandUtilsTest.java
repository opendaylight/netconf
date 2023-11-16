/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.console.commands;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NetconfCommandUtilsTest {
    @Test
    void testIsPortValid() {
        assertTrue(NetconfCommandUtils.isPortValid("65535"));
        assertTrue(NetconfCommandUtils.isPortValid("0"));
        assertFalse(NetconfCommandUtils.isPortValid("123x"));
        assertFalse(NetconfCommandUtils.isPortValid("65536"));
        assertFalse(NetconfCommandUtils.isPortValid(""));
    }

    @Test
    void testIsIpValid() {
        assertTrue(NetconfCommandUtils.isIpValid("0.0.0.0"));
        assertTrue(NetconfCommandUtils.isIpValid("255.255.255.255"));
        assertFalse(NetconfCommandUtils.isIpValid("256.1.1.1"));
        assertFalse(NetconfCommandUtils.isIpValid("123.145.12.x"));
        assertFalse(NetconfCommandUtils.isIpValid(""));
    }
}
