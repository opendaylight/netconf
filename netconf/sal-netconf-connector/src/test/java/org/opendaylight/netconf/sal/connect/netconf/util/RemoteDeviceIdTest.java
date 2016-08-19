/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.connect.netconf.util;

import org.junit.Test;
import org.opendaylight.controller.config.api.ModuleIdentifier;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import java.net.InetSocketAddress;
import static org.junit.Assert.assertEquals;

public class RemoteDeviceIdTest {

    @Test
    public void testEquals() {
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", 8000);
        ModuleIdentifier identifier = new ModuleIdentifier("test", "test");

        RemoteDeviceId remoteDeviceId = new RemoteDeviceId("test", address);
        RemoteDeviceId remoteDeviceIdEqualName = new RemoteDeviceId(identifier, address);
        RemoteDeviceId remoteDeviceIdDiffName = new RemoteDeviceId("test-diff", address);

        assertEquals(true, remoteDeviceId.equals(remoteDeviceId));
        assertEquals(false, remoteDeviceId.equals(this));
        assertEquals(false, remoteDeviceId.equals(remoteDeviceIdDiffName));
        assertEquals(true, remoteDeviceId.equals(remoteDeviceIdEqualName));
    }

    @Test
    public void testHashCode() {
        String name = "name";
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", 8000);
        RemoteDeviceId remoteDeviceId = new RemoteDeviceId(name, address);

        int hashCode = 31 * name.hashCode() + remoteDeviceId.getBindingPath().hashCode();

        assertEquals(hashCode, remoteDeviceId.hashCode());
    }
}
