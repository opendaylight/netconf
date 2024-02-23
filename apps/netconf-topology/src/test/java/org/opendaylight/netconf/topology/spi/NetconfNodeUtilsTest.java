/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.spi;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceId;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
<<<<<<< HEAD   (b0db55 Make RESTCONF base path configurable)
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev221225.NetconfNodeBuilder;
=======
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev240223.credentials.credentials.LoginPasswordBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev240223.NetconfNodeBuilder;
>>>>>>> CHANGE (8ef6ed Use base64 encoding for netconf device passwords)
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yangtools.yang.common.Uint16;

public class NetconfNodeUtilsTest {
    @Test
    public void testCreateRemoteDeviceId() {
        final Host host = new Host(new IpAddress(new Ipv4Address("127.0.0.1")));
        final RemoteDeviceId id = NetconfNodeUtils.toRemoteDeviceId(new NodeId("testing-node"), new NetconfNodeBuilder()
            .setHost(host)
            .setPort(new PortNumber(Uint16.valueOf(9999)))
            .build());

        assertEquals("testing-node", id.name());
        assertEquals(host, id.host());
        assertEquals(9999, id.address().getPort());
    }
}
