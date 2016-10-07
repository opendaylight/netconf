/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.singleton.impl;

import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.cluster.Cluster;
import akka.cluster.Member;
import java.util.Collection;
import javax.annotation.Nonnull;
import org.opendaylight.controller.md.sal.binding.api.ClusteredDataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataObjectModification;
import org.opendaylight.controller.md.sal.binding.api.DataTreeIdentifier;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.netconf.topology.singleton.api.NetconfTopologyServicesProvider;
import org.opendaylight.netconf.topology.singleton.impl.actors.NetconfNodeActor;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologySetupBuilder;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologyUtils;
import org.opendaylight.netconf.topology.singleton.messages.AskForMasterMountPoint;
import org.opendaylight.netconf.topology.singleton.messages.UnregisterSlaveMountPoint;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNodeConnectionStatus;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaRepository;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class NetconfNodeManager
        implements ClusteredDataTreeChangeListener<Node>, NetconfTopologyServicesProvider, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(NetconfNodeManager.class);

    private final NetconfTopologySetupBuilder.NetconfTopologySetup setup;
    private ListenerRegistration<NetconfNodeManager> dataChangeListenerRegistration;
    private final RemoteDeviceId id;
    private final SchemaSourceRegistry schemaRegistry;
    private final SchemaRepository schemaRepository;
    private ActorRef slaveActorRef;

    NetconfNodeManager(final NetconfTopologySetupBuilder.NetconfTopologySetup setup,
                       final RemoteDeviceId id, final SchemaSourceRegistry schemaRegistry,
                       final SchemaRepository schemaRepository) {
        this.setup = setup;
        this.id = id;
        this.schemaRegistry = schemaRegistry;
        this.schemaRepository = schemaRepository;
    }

    @Override
    public void onDataTreeChanged(@Nonnull final Collection<DataTreeModification<Node>> changes) {
        for (final DataTreeModification<Node> change : changes) {
            final DataObjectModification<Node> rootNode = change.getRootNode();

            switch (rootNode.getModificationType()) {
                case SUBTREE_MODIFIED:
                    LOG.debug("Operational for node {} updated. Trying registering slave mount point",
                            NetconfTopologyUtils.getNodeId(rootNode.getIdentifier()));
                    handleSlaveMountPoint(rootNode);
                    break;
                case WRITE:
                    LOG.debug("Operational for node {} created. Trying registering slave mount point",
                            NetconfTopologyUtils.getNodeId(rootNode.getIdentifier()));
                    handleSlaveMountPoint(rootNode);
                    break;
                default:
                    LOG.debug("Uknow operation for node: {}", NetconfTopologyUtils.getNodeId(rootNode.getIdentifier()));
            }
        }
    }

    @Override
    public void close() {
        if (slaveActorRef != null) {
            slaveActorRef.tell(new UnregisterSlaveMountPoint(), ActorRef.noSender());
            slaveActorRef.tell(PoisonPill.getInstance(), ActorRef.noSender());
        }
        if (dataChangeListenerRegistration != null) {
            dataChangeListenerRegistration.close();
            dataChangeListenerRegistration = null;
        }
    }

    void registerDataTreeChangeListener(final String topologyId, final NodeKey key) {
        LOG.debug("Registering data tree change listener on node {}", key);
        dataChangeListenerRegistration = setup.getDataBroker().registerDataTreeChangeListener(
                new DataTreeIdentifier<>(LogicalDatastoreType.OPERATIONAL,
                        NetconfTopologyUtils.createTopologyNodeListPath(key, topologyId)), this);
    }

    private void handleSlaveMountPoint(final DataObjectModification<Node> rootNode) {
        NetconfNodeConnectionStatus.ConnectionStatus actualStatus =
                rootNode.getDataAfter().getAugmentation(NetconfNode.class).getConnectionStatus();

        if (NetconfNodeConnectionStatus.ConnectionStatus.Connected.equals(actualStatus)) {
            createActorRef();
            final Cluster cluster = Cluster.get(setup.getActorSystem());
            final Iterable<Member> members = cluster.state().getMembers();

            for (final Member member : members) {
                if (!member.address().equals(cluster.selfAddress())) {
                    final String path = NetconfTopologyUtils.createActorPath(member,
                            NetconfTopologyUtils.createMasterActorName(id.getName()));
                    setup.getActorSystem().actorSelection(path).tell(new AskForMasterMountPoint(), slaveActorRef);
                }
            }
        }
    }

    private void createActorRef() {
        if (slaveActorRef == null) {
            slaveActorRef = setup.getActorSystem().actorOf(NetconfNodeActor.props(setup, id, schemaRegistry,
                    schemaRepository), id.getName());
        }
    }

}
