/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.mountpoint;

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

public class RestconfDeviceId {

    public static final String TOPOLOGY_RESTCONF = "topology-restconf";
    private static final InstanceIdentifier<Topology> restconfTopologyBindingId =
            InstanceIdentifier.create(NetworkTopology.class)
                    .child(Topology.class, new TopologyKey(new TopologyId(TOPOLOGY_RESTCONF)));

    private final String nodeName;
    private final KeyedInstanceIdentifier<Node, NodeKey> bindingTopologyPath;
    private final YangInstanceIdentifier topologyPath;

    public RestconfDeviceId(final String nodeName) {
        this.nodeName = nodeName;
        bindingTopologyPath = restconfTopologyBindingId.child(Node.class, new NodeKey(new NodeId(nodeName)));

        topologyPath = YangInstanceIdentifier.builder()
                .node(NetworkTopology.QNAME)
                .node(Topology.QNAME)
                .nodeWithKey(Topology.QNAME, QName.create(Topology.QNAME, "topology-id"), TOPOLOGY_RESTCONF)
                .node(Node.QNAME)
                .nodeWithKey(Node.QNAME, QName.create(Node.QNAME, "node-id"), nodeName)
                .build();
    }

    public String getNodeName() {
        return nodeName;
    }

    public InstanceIdentifier<Node> getBindingTopologyPath() {
        return bindingTopologyPath;
    }

    public YangInstanceIdentifier getTopologyPath() {
        return topologyPath;
    }
}
