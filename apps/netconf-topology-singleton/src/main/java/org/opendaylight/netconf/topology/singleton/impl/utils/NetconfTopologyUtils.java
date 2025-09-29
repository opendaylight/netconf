/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl.utils;

import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yangtools.binding.DataObjectIdentifier;
import org.opendaylight.yangtools.binding.DataObjectIdentifier.WithKey;
import org.opendaylight.yangtools.binding.DataObjectReference;
import org.opendaylight.yangtools.binding.DataObjectStep;
import org.opendaylight.yangtools.binding.KeyStep;
import org.opendaylight.yangtools.yang.common.ErrorSeverity;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;

public final class NetconfTopologyUtils {

    private NetconfTopologyUtils() {
        // Hidden on purpose
    }

    public static String createActorPath(final String masterMember, final String name) {
        return  masterMember + "/user/" + name;
    }

    public static String createMasterActorName(final String name, final String masterAddress) {
        return masterAddress.replace("//", "") + "_" + name;
    }

    public static @NonNull NodeId getNodeId(final DataObjectStep<?> pathArgument) {
        if (pathArgument instanceof KeyStep identifiableItem && identifiableItem.key() instanceof NodeKey nodeKey) {
            return nodeKey.getNodeId();
        }
        throw new IllegalStateException("Unable to create NodeId from: " + pathArgument);
    }

    public static @NonNull WithKey<Topology, TopologyKey> createTopologyListPath(final String topologyId) {
        return DataObjectIdentifier.builder(NetworkTopology.class)
            .child(Topology.class, new TopologyKey(new TopologyId(topologyId)))
            .build();
    }

    public static @NonNull WithKey<Node, NodeKey> createTopologyNodeListPath(final NodeKey key,
            final String topologyId) {
        return createTopologyListPath(topologyId).toBuilder()
            .child(Node.class, new NodeKey(new NodeId(key.getNodeId().getValue())))
            .build();
    }

    public static DataObjectReference<Node> createTopologyNodePath(final String topologyId) {
        return createTopologyListPath(topologyId).toBuilder().toReferenceBuilder().child(Node.class).build();
    }

    public static @NonNull DocumentedException createMasterIsDownException(final RemoteDeviceId id,
            final Exception cause) {
        return new DocumentedException(id + ":Master is down. Please try again.", cause,
                ErrorType.APPLICATION, ErrorTag.OPERATION_FAILED, ErrorSeverity.WARNING);
    }
}
