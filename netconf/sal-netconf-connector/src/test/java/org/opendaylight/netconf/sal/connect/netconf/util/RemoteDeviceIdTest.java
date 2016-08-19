/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.connect.netconf.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.net.InetSocketAddress;
import org.junit.Test;
import org.opendaylight.controller.config.api.ModuleIdentifier;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;

public class RemoteDeviceIdTest {

    @Test
    public void testEquals() {
        final InetSocketAddress address = new InetSocketAddress("127.0.0.1", 8000);
        final ModuleIdentifier identifier = new ModuleIdentifier("test", "test");

        final RemoteDeviceId remoteDeviceId = new RemoteDeviceId("test", address);
        final RemoteDeviceId remoteDeviceIdEqualName = new RemoteDeviceId(identifier, address);
        final RemoteDeviceId remoteDeviceIdDiffName = new RemoteDeviceId("test-diff", address);

        assertEquals(true, remoteDeviceId.equals(remoteDeviceId));
        assertEquals(false, remoteDeviceId.equals(this));
        assertEquals(false, remoteDeviceId.equals(remoteDeviceIdDiffName));
        assertEquals(true, remoteDeviceId.equals(remoteDeviceIdEqualName));
    }

    @Test
    public void testHashCode() {
        final String name = "name";
        final InetSocketAddress address = new InetSocketAddress("127.0.0.1", 8000);
        final RemoteDeviceId remoteDeviceId = new RemoteDeviceId(name, address);
        final RemoteDeviceId remoteDeviceIdEqualName = new RemoteDeviceId(name, address);
        final RemoteDeviceId remoteDeviceIdDiffName = new RemoteDeviceId("test-diff", address);

        assertEquals(remoteDeviceIdEqualName.hashCode(), remoteDeviceId.hashCode());
        assertNotEquals(remoteDeviceIdDiffName.hashCode(), remoteDeviceId.hashCode());
    }
}
