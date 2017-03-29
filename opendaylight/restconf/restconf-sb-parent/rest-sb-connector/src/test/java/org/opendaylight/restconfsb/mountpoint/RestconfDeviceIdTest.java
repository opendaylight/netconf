/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.mountpoint;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.KeyedInstanceIdentifier;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;

public class RestconfDeviceIdTest {

    private static final String RESTCONF_NAME = "Restconf1";
    private static final String TOPOLOGY_RESTCONF = "topology-restconf";
    private static final InstanceIdentifier<Topology> restconfTopologyBindingId =
            InstanceIdentifier.create(NetworkTopology.class)
                    .child(Topology.class, new TopologyKey(new TopologyId(TOPOLOGY_RESTCONF)));

    private RestconfDeviceId deviceId;

    @Before
    public void setUp() {
        deviceId = new RestconfDeviceId(RESTCONF_NAME);
    }

    @Test
    public void getNodeNameTest() {
        final String deviceName = deviceId.getNodeName();
        assertEquals(RESTCONF_NAME, deviceName);
    }

    @Test
    public void getTopologyPathTest() {
        YangInstanceIdentifier topologyPath = YangInstanceIdentifier.builder()
                .node(NetworkTopology.QNAME)
                .node(Topology.QNAME)
                .nodeWithKey(Topology.QNAME, QName.create(Topology.QNAME, "topology-id"), TOPOLOGY_RESTCONF)
                .node(Node.QNAME)
                .nodeWithKey(Node.QNAME, QName.create(Node.QNAME, "node-id"), RESTCONF_NAME)
                .build();
        assertEquals(topologyPath, deviceId.getTopologyPath());
    }

    @Test
    public void getBindingTopologyPathTest() {
        KeyedInstanceIdentifier<Node, NodeKey> keyedId = restconfTopologyBindingId.child(Node.class, new NodeKey(new NodeId(RESTCONF_NAME)));
        assertEquals(keyedId, deviceId.getBindingTopologyPath());
    }

}