/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.messagebus.eventsources.netconf;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.notification._1._0.rev080714.StreamNameType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netmod.notification.rev080714.netconf.Streams;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netmod.notification.rev080714.netconf.StreamsBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netmod.notification.rev080714.netconf.streams.Stream;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netmod.notification.rev080714.netconf.streams.StreamBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.DomainName;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNodeConnectionStatus.ConnectionStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.connection.status.AvailableCapabilitiesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.connection.status.available.capabilities.AvailableCapability;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.connection.status.available.capabilities.AvailableCapabilityBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.network.topology.topology.topology.types.TopologyNetconf;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.CollectionNodeBuilder;

@Deprecated(forRemoval = true)
public final class NetconfTestUtils {
    public static final String NOTIFICATION_CAPABILITY_PREFIX = "(urn:ietf:params:xml:ns:netconf:notification";

    private NetconfTestUtils() {

    }

    public static Node getNetconfNode(final String nodeIdent, final String hostName, final ConnectionStatus cs,
                                      final String notificationCapabilityPrefix) {
        List<AvailableCapability> avCapList = new ArrayList<>();
        avCapList.add(new AvailableCapabilityBuilder().setCapability(notificationCapabilityPrefix
                + "_availableCapabilityString1").build());

        return new NodeBuilder()
                .withKey(new NodeKey(new NodeId(nodeIdent)))
                .addAugmentation(new NetconfNodeBuilder()
                    .setConnectionStatus(cs)
                    .setHost(new Host(new DomainName(hostName)))
                    .setAvailableCapabilities(new AvailableCapabilitiesBuilder()
                        .setAvailableCapability(avCapList)
                        .build())
                    .build())
                .build();
    }

    public static Node getNode(final String nodeIdent) {
        return new NodeBuilder().withKey(new NodeKey(new NodeId(nodeIdent))).build();
    }

    public static InstanceIdentifier<Node> getInstanceIdentifier(final Node node) {
        return InstanceIdentifier.create(NetworkTopology.class)
                .child(Topology.class, new TopologyKey(new TopologyId(TopologyNetconf.QNAME.getLocalName())))
                .child(Node.class, node.key());
    }

    public static Optional<Streams> getAvailableStream(final String name, final boolean replaySupport) {
        Stream stream = new StreamBuilder().setName(new StreamNameType(name)).setReplaySupport(replaySupport).build();
        return Optional.of(new StreamsBuilder().setStream(ImmutableMap.of(stream.key(), stream)).build());
    }

    public static NormalizedNode<?, ?> getStreamsNode(final String... streamName) {
        QName nameNode = QName.create(Stream.QNAME, "name");
        Set<MapEntryNode> streamSet = new HashSet<>();
        for (String s : streamName) {
            MapEntryNode stream = Builders.mapEntryBuilder()
                    .withNodeIdentifier(NodeIdentifierWithPredicates.of(Stream.QNAME, nameNode, s))
                    .withChild(Builders.leafBuilder()
                            .withNodeIdentifier(new NodeIdentifier(nameNode))
                            .withValue(s)
                            .build())
                    .build();
            streamSet.add(stream);
        }

        CollectionNodeBuilder<MapEntryNode, MapNode> streams =
                Builders.mapBuilder().withNodeIdentifier(NodeIdentifier.create(Stream.QNAME));
        for (MapEntryNode mapEntryNode : streamSet) {
            streams.withChild(mapEntryNode);
        }
        return Builders.containerBuilder()
                .withNodeIdentifier(new NodeIdentifier(Streams.QNAME))
                .withChild(streams.build())
                .build();
    }

}
