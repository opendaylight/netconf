/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.util;

import com.google.common.base.Preconditions;
import java.net.InetSocketAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.HostBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.network.rev180226.NetworkId;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.network.rev180226.Networks;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.network.rev180226.networks.Network;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.network.rev180226.networks.NetworkKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev180703.networks.network.network.types.NetconfNetwork;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;

public final class RemoteDeviceId {

    private final String name;
    private final org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier path;
    private final InstanceIdentifier<Node> bindingPath;
    private final NodeKey key;
    private final org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier topologyPath;
    private final InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang
            .ietf.network.rev180226.networks.network.Node> topologyBindingPath;
    private InetSocketAddress address;
    private Host host;

    private RemoteDeviceId(final String name) {
        this.name = Preconditions.checkNotNull(name);
        this.key = new NodeKey(new NodeId(name));
        this.path = createBIPath(name);
        this.bindingPath = createBindingPath(key);
        this.topologyPath = createBIPathForTopology(name);
        this.topologyBindingPath = createBindingPathForTopology(key);
    }

    public RemoteDeviceId(final String name, final InetSocketAddress address) {
        this(name);
        this.address = address;
        this.host = buildHost();
    }

    private static InstanceIdentifier<Node> createBindingPath(final NodeKey key) {
        return InstanceIdentifier.builder(Nodes.class).child(Node.class, key).build();
    }

    private static org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier createBIPath(final String name) {
        final org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.InstanceIdentifierBuilder builder =
                org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.builder();
        builder.node(Nodes.QNAME).node(Node.QNAME)
                .nodeWithKey(Node.QNAME, QName.create(Node.QNAME.getNamespace(), Node.QNAME.getRevision(), "id"), name);

        return builder.build();
    }

    private static InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang
            .ietf.network.rev180226.networks.network.Node> createBindingPathForTopology(final NodeKey key) {
        return InstanceIdentifier.builder(Networks.class)
                .child(Network.class, new NetworkKey(new NetworkId(NetconfNetwork.QNAME.getLocalName())))
                .child(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang
                    .ietf.network.rev180226.networks.network.Node.class,
                    new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang
                    .ietf.network.rev180226.networks.network.NodeKey(
                        new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang
                        .ietf.network.rev180226.NodeId(key.getId().getValue())))
                .build();
    }

    private static org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier createBIPathForTopology(
            final String name) {
        return YangInstanceIdentifier.builder()
                .node(Networks.QNAME)
                .node(Network.QNAME)
                .nodeWithKey(Network.QNAME, QName.create(Network.QNAME, "network-id"),
                    NetconfNetwork.QNAME.getLocalName())
                .node(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang
                    .ietf.network.rev180226.networks.network.Node.QNAME)
                .nodeWithKey(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang
                    .ietf.network.rev180226.networks.network.Node.QNAME,
                        QName.create(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang
                            .ietf.network.rev180226.networks.network.Node.QNAME, "node-id"), name)
                .build();
    }

    private Host buildHost() {
        return HostBuilder.getDefaultInstance(address.getHostString());
    }

    public String getName() {
        return name;
    }

    public InstanceIdentifier<Node> getBindingPath() {
        return bindingPath;
    }

    public org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier getPath() {
        return path;
    }

    public NodeKey getBindingKey() {
        return key;
    }

    public InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang
            .ietf.network.rev180226.networks.network.Node> getTopologyBindingPath() {
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
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof RemoteDeviceId)) {
            return false;
        }

        final RemoteDeviceId that = (RemoteDeviceId) obj;

        if (!name.equals(that.name)) {
            return false;
        }
        return bindingPath.equals(that.bindingPath);
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + bindingPath.hashCode();
        return result;
    }
}
