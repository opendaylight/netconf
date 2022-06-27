/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.util;

import static java.util.Objects.requireNonNull;

import java.net.InetSocketAddress;
import java.util.Objects;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.DomainName;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IetfInetUtil;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.network.topology.topology.topology.types.TopologyNetconf;
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
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;

public final class RemoteDeviceId {
    private static final String DEFAULT_TOPOLOGY_NAME = TopologyNetconf.QNAME.getLocalName();
    private static final KeyedInstanceIdentifier<Topology, TopologyKey> DEFAULT_TOPOLOGY_IID =
            InstanceIdentifier.create(NetworkTopology.class)
            .child(Topology.class, new TopologyKey(new TopologyId(DEFAULT_TOPOLOGY_NAME)));
    private static final YangInstanceIdentifier DEFAULT_TOPOLOGY_NODE = YangInstanceIdentifier.builder()
            .node(NetworkTopology.QNAME).node(Topology.QNAME)
            .nodeWithKey(Topology.QNAME, QName.create(Topology.QNAME, "topology-id"), DEFAULT_TOPOLOGY_NAME)
            .node(Node.QNAME)
            .build();
    private static final QName NODE_ID_QNAME = QName.create(Node.QNAME, "node-id").intern();

    private final String name;
    private final NodeKey key;
    private final YangInstanceIdentifier topologyPath;
    private final KeyedInstanceIdentifier<Node, NodeKey> topologyBindingPath;
    private InetSocketAddress address;
    private Host host;

    private RemoteDeviceId(final String name) {
        this.name = requireNonNull(name);
        topologyPath = DEFAULT_TOPOLOGY_NODE.node(NodeIdentifierWithPredicates.of(Node.QNAME, NODE_ID_QNAME, name));
        key = new NodeKey(new NodeId(name));
        topologyBindingPath = DEFAULT_TOPOLOGY_IID.child(Node.class, key);
    }

    public RemoteDeviceId(final String name, final InetSocketAddress address) {
        this(name);
        this.address = address;
        host = buildHost();
    }

    private Host buildHost() {
        final var addr = address.getAddress();
        return addr != null ? new Host(IetfInetUtil.INSTANCE.ipAddressFor(addr))
            : new Host(new DomainName(address.getHostString()));
    }

    public String getName() {
        return name;
    }

    public NodeKey getBindingKey() {
        return key;
    }

    public InstanceIdentifier<Node> getTopologyBindingPath() {
        return topologyBindingPath;
    }

    public YangInstanceIdentifier getTopologyPath() {
        return topologyPath;
    }

    public InetSocketAddress getAddress() {
        return address;
    }

    public Host getHost() {
        return host;
    }

    @Override
    public String toString() {
        return "RemoteDevice{" + name + '}';
    }

    @Override
    public boolean equals(final Object obj) {
        return this == obj || obj instanceof RemoteDeviceId other
            && name.equals(other.name) && topologyBindingPath.equals(other.topologyBindingPath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, topologyBindingPath);
    }
}
