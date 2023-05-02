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
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier.IdentifiableItem;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.binding.KeyedInstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Decimal64;
import org.opendaylight.yangtools.yang.common.ErrorSeverity;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;

public final class NetconfTopologyUtils {
    public static final long DEFAULT_REQUEST_TIMEOUT_MILLIS = 60000L;
    public static final int DEFAULT_KEEPALIVE_DELAY = 0;
    public static final boolean DEFAULT_RECONNECT_ON_CHANGED_SCHEMA = false;
    public static final boolean DEFAULT_IS_TCP_ONLY = false;
    public static final int DEFAULT_CONCURRENT_RPC_LIMIT = 0;
    public static final int DEFAULT_MAX_CONNECTION_ATTEMPTS = 0;
    public static final int DEFAULT_BETWEEN_ATTEMPTS_TIMEOUT_MILLIS = 2000;
    public static final long DEFAULT_CONNECTION_TIMEOUT_MILLIS = 20000L;
    public static final Decimal64 DEFAULT_SLEEP_FACTOR = Decimal64.valueOf("1.5");

    private NetconfTopologyUtils() {
        // Hidden on purpose
    }

    public static String createActorPath(final String masterMember, final String name) {
        return  masterMember + "/user/" + name;
    }

    public static String createMasterActorName(final String name, final String masterAddress) {
        return masterAddress.replace("//", "") + "_" + name;
    }

    public static @NonNull NodeId getNodeId(final PathArgument pathArgument) {
        if (pathArgument instanceof IdentifiableItem<?, ?> identifiableItem
            && identifiableItem.getKey() instanceof NodeKey nodeKey) {
            return nodeKey.getNodeId();
        }
        throw new IllegalStateException("Unable to create NodeId from: " + pathArgument);
    }

    public static @NonNull KeyedInstanceIdentifier<Topology, TopologyKey> createTopologyListPath(
            final String topologyId) {
        return InstanceIdentifier.create(NetworkTopology.class)
            .child(Topology.class, new TopologyKey(new TopologyId(topologyId)));
    }

    public static @NonNull KeyedInstanceIdentifier<Node, NodeKey> createTopologyNodeListPath(final NodeKey key,
            final String topologyId) {
        return createTopologyListPath(topologyId)
                .child(Node.class, new NodeKey(new NodeId(key.getNodeId().getValue())));
    }

    public static @NonNull InstanceIdentifier<Node> createTopologyNodePath(final String topologyId) {
        return createTopologyListPath(topologyId).child(Node.class);
    }

    public static @NonNull DocumentedException createMasterIsDownException(final RemoteDeviceId id,
            final Exception cause) {
        return new DocumentedException(id + ":Master is down. Please try again.", cause,
                ErrorType.APPLICATION, ErrorTag.OPERATION_FAILED, ErrorSeverity.WARNING);
    }
}
