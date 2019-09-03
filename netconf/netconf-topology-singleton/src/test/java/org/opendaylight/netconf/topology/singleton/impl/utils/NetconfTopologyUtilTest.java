/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.singleton.impl.utils;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Uint16;

public class NetconfTopologyUtilTest {

    @Test
    public void testCreateRemoteDeviceId() {
        final Host host = new Host(new IpAddress(new Ipv4Address("127.0.0.1")));
        final NetconfNode netconfNode = new NetconfNodeBuilder().setHost(host)
                .setPort(new PortNumber(Uint16.valueOf(9999))).build();
        final NodeId nodeId = new NodeId("testing-node");
        final RemoteDeviceId id = NetconfTopologyUtils.createRemoteDeviceId(nodeId, netconfNode);

        assertEquals("testing-node", id.getName());
        assertEquals(host, id.getHost());
        assertEquals(9999, id.getAddress().getPort());
    }

    @Test
    public void testCreateActorPath() {
        final String actorPath = NetconfTopologyUtils.createActorPath("member", "name");
        assertEquals("member/user/name", actorPath);
    }

    @Test
    public void testCreateListPath() {
        final InstanceIdentifier<Node> listPath =
                NetconfTopologyUtils.createTopologyNodeListPath(new NodeKey(new NodeId("nodeId")), "topologyId");

        assertEquals("nodeId", listPath.firstKeyOf(Node.class).getNodeId().getValue());
        assertEquals("topologyId", listPath.firstKeyOf(Topology.class).getTopologyId().getValue());

        assertEquals("topologyId",  NetconfTopologyUtils.createTopologyNodePath("topologyId")
                .firstKeyOf(Topology.class).getTopologyId().getValue());
    }

}
