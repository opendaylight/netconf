/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.topology.cluster.impl;

import java.util.Collections;
import java.util.List;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.clustered.node.rev160615.ClusteredNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.clustered.node.rev160615.ClusteredNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.clustered.node.rev160615.network.topology.topology.node.ClusteredConnectionStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.clustered.node.rev160615.network.topology.topology.node.ClusteredConnectionStatusBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.clustered.node.rev160615.network.topology.topology.node.clustered.connection.status.ClusteredStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.clustered.node.rev160615.network.topology.topology.node.clustered.connection.status.ClusteredStatusBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.node.rev160511.NodeStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.node.rev160511.RestconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.node.rev160511.RestconfNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.node.rev160511.node.status.Module;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;

abstract class TopologyUtil {

    private TopologyUtil() {
        throw new UnsupportedOperationException("Util class can't be instantiated");
    }

    static Node buildState(final NodeId nodeId, final String address, final ClusteredStatus.Status clusteredStatus,
                           final NodeStatus.Status nodeStatus, final List<Module> input) {
        final RestconfNode restconfNode = new RestconfNodeBuilder()
                .setStatus(nodeStatus)
                .setModule(input)
                .build();
        final ClusteredConnectionStatus clusteredConnectionStatus = new ClusteredConnectionStatusBuilder()
                .setClusteredStatus(Collections.singletonList(
                        new ClusteredStatusBuilder()
                                .setNode(address)
                                .setStatus(clusteredStatus)
                                .build()))
                .build();
        final ClusteredNode clusteredNode = new ClusteredNodeBuilder()
                .setClusteredConnectionStatus(clusteredConnectionStatus)
                .build();
        return new NodeBuilder()
                .setNodeId(nodeId)
                .addAugmentation(RestconfNode.class, restconfNode)
                .addAugmentation(ClusteredNode.class, clusteredNode)
                .build();
    }

}
