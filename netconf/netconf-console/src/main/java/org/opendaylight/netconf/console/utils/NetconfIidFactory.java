/*
 * Copyright (c) 2016 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.console.utils;

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.network.rev180226.NetworkId;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.network.rev180226.Networks;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.network.rev180226.NodeId;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.network.rev180226.networks.Network;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.network.rev180226.networks.NetworkKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.network.rev180226.networks.network.Node;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.network.rev180226.networks.network.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev180703.networks.network.network.types.NetconfNetwork;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public final class NetconfIidFactory {

    private NetconfIidFactory() {
        throw new IllegalStateException("Instantiating utility class.");
    }

    public static final InstanceIdentifier<Network> NETCONF_TOPOLOGY_IID =
            InstanceIdentifier.builder(Networks.class)
            .child(Network.class, new NetworkKey(new NetworkId(NetconfNetwork.QNAME.getLocalName())))
            .build();

    public static InstanceIdentifier<Node> netconfNodeIid(final String nodeId) {
        return NETCONF_TOPOLOGY_IID.child(Node.class, new NodeKey(new NodeId(nodeId)));
    }
}
