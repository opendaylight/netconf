/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.net.InetSocketAddress;
import org.junit.Test;

public class RemoteDeviceIdTest {
    @Test
    public void testEquals() {
        final var address = new InetSocketAddress("127.0.0.1", 8000);

        final var remoteDeviceId = new RemoteDeviceId("test", address);

        assertEquals(remoteDeviceId, remoteDeviceId);
        assertNotEquals(remoteDeviceId, this);
        assertNotEquals(new RemoteDeviceId("test-diff", address), remoteDeviceId);
        assertEquals(new RemoteDeviceId("test", address), remoteDeviceId);
    }

    @Test
    public void testHashCode() {
        final var name = "name";
        final var address = new InetSocketAddress("127.0.0.1", 8000);
        final var remoteDeviceId = new RemoteDeviceId(name, address);
        final var remoteDeviceIdEqualName = new RemoteDeviceId(name, address);
        final var remoteDeviceIdDiffName = new RemoteDeviceId("test-diff", address);

        assertEquals(remoteDeviceIdEqualName.hashCode(), remoteDeviceId.hashCode());
        assertNotEquals(remoteDeviceIdDiffName.hashCode(), remoteDeviceId.hashCode());
    }
}
