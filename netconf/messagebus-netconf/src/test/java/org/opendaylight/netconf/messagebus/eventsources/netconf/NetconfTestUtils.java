/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.messagebus.eventsources.netconf;

import com.google.common.base.Optional;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.notification._1._0.rev080714.StreamNameType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netmod.notification.rev080714.netconf.Streams;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netmod.notification.rev080714.netconf.StreamsBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netmod.notification.rev080714.netconf.streams.Stream;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netmod.notification.rev080714.netconf.streams.StreamBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.DomainName;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.network.rev180226.NetworkId;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.network.rev180226.Networks;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.network.rev180226.NodeId;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.network.rev180226.networks.Network;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.network.rev180226.networks.NetworkKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.network.rev180226.networks.network.Node;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.network.rev180226.networks.network.NodeBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.network.rev180226.networks.network.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNodeConnectionStatus.ConnectionStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.connection.status.AvailableCapabilities;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.connection.status.AvailableCapabilitiesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.connection.status.available.capabilities.AvailableCapability;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.connection.status.available.capabilities.AvailableCapabilityBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.networks.network.network.types.TopologyNetconf;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.CollectionNodeBuilder;

public final class NetconfTestUtils {

    public static final String NOTIFICATION_CAPABILITY_PREFIX = "(urn:ietf:params:xml:ns:netconf:notification";

    private NetconfTestUtils() {
    }

    public static Node getNetconfNode(final String nodeIdent, final String hostName, final ConnectionStatus cs,
                                      final String notificationCapabilityPrefix) {

        DomainName dn = new DomainName(hostName);
        Host host = new Host(dn);

        List<AvailableCapability> avCapList = new ArrayList<>();
        avCapList.add(new AvailableCapabilityBuilder().setCapability(notificationCapabilityPrefix
                + "_availableCapabilityString1").build());
        AvailableCapabilities avCaps = new AvailableCapabilitiesBuilder().setAvailableCapability(avCapList).build();
        NetconfNode nn = new NetconfNodeBuilder().setConnectionStatus(cs).setHost(host).setAvailableCapabilities(avCaps)
                .build();

        NodeId nodeId = new NodeId(nodeIdent);
        NodeKey nk = new NodeKey(nodeId);
        NodeBuilder nb = new NodeBuilder();
        nb.withKey(nk);

        nb.addAugmentation(NetconfNode.class, nn);
        return nb.build();
    }

    public static Node getNode(final String nodeIdent) {
        NodeId nodeId = new NodeId(nodeIdent);
        NodeKey nk = new NodeKey(nodeId);
        NodeBuilder nb = new NodeBuilder();
        nb.withKey(nk);
        return nb.build();
    }

    public static InstanceIdentifier<Node> getInstanceIdentifier(final Node node) {
        NetworkKey netconfTopologyKey = new NetworkKey(new NetworkId(TopologyNetconf.QNAME.getLocalName()));
        return InstanceIdentifier.create(Networks.class)
                .child(Network.class, netconfTopologyKey).child(Node.class, node.key());
    }

    public static Optional<Streams> getAvailableStream(final String name, final boolean replaySupport) {
        Stream stream = new StreamBuilder().setName(new StreamNameType(name)).setReplaySupport(replaySupport).build();
        List<Stream> streamList = new ArrayList<>();
        streamList.add(stream);
        Streams streams = new StreamsBuilder().setStream(streamList).build();
        return Optional.of(streams);
    }

    public static NormalizedNode<?, ?> getStreamsNode(final String... streamName) {
        QName nameNode = QName.create(Stream.QNAME, "name");
        Set<MapEntryNode> streamSet = new HashSet<>();
        for (String s : streamName) {
            MapEntryNode stream = Builders.mapEntryBuilder()
                    .withNodeIdentifier(new YangInstanceIdentifier
                            .NodeIdentifierWithPredicates(Stream.QNAME, nameNode, s))
                    .withChild(Builders.leafBuilder()
                            .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(nameNode))
                            .withValue(s)
                            .build())
                    .build();
            streamSet.add(stream);
        }

        CollectionNodeBuilder<MapEntryNode, MapNode> streams =
                Builders.mapBuilder().withNodeIdentifier(YangInstanceIdentifier.NodeIdentifier.create(Stream.QNAME));
        for (MapEntryNode mapEntryNode : streamSet) {
            streams.withChild(mapEntryNode);
        }
        return Builders.containerBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(Streams.QNAME))
                .withChild(streams.build())
                .build();
    }

}
