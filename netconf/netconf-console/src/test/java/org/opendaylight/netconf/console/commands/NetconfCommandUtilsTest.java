/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.console.commands;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;

import org.junit.Test;

public class NetconfCommandUtilsTest {

    @Test
    public void testIsPortValid() {
        final boolean portTrue = NetconfCommandUtils.isPortValid("65535");
        final boolean portTrue2 = NetconfCommandUtils.isPortValid("0");

        final boolean portFalse = NetconfCommandUtils.isPortValid("123x");
        final boolean portFalse2 = NetconfCommandUtils.isPortValid("65536");
        final boolean portFalse3 = NetconfCommandUtils.isPortValid("");

        assertTrue(portTrue);
        assertTrue(portTrue2);
        assertFalse(portFalse);
        assertFalse(portFalse2);
        assertFalse(portFalse3);

    }

    @Test
    public void testIsIpValid() {
        final boolean ipTrue = NetconfCommandUtils.isIpValid("0.0.0.0");
        final boolean ipTrue2 = NetconfCommandUtils.isIpValid("255.255.255.255");

        final boolean ipFalse = NetconfCommandUtils.isIpValid("256.1.1.1");
        final boolean ipFalse2 = NetconfCommandUtils.isIpValid("123.145.12.x");
        final boolean ipFalse3 = NetconfCommandUtils.isIpValid("");

        assertTrue(ipTrue);
        assertTrue(ipTrue2);
        assertFalse(ipFalse);
        assertFalse(ipFalse2);
        assertFalse(ipFalse3);
    }
}
