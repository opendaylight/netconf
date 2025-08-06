/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;

class NetconfTopologyUtilTest {
    @Test
    void testCreateActorPath() {
        final String actorPath = NetconfTopologyUtils.createActorPath("member", "name");
        assertEquals("member/user/name", actorPath);
    }

    @Test
    void testCreateListPath() {
        final var listPath = NetconfTopologyUtils.createTopologyNodeListPath(
            new NodeKey(new NodeId("nodeId")), "topologyId");

        assertEquals("nodeId", listPath.getFirstKeyOf(Node.class).getNodeId().getValue());
        assertEquals("topologyId", listPath.getFirstKeyOf(Topology.class).getTopologyId().getValue());
        assertEquals("topologyId", NetconfTopologyUtils.createTopologyNodePath("topologyId")
            .getFirstKeyOf(Topology.class).getTopologyId().getValue());
    }
}
