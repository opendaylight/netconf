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
import java.util.Collection;
import javax.annotation.Nonnull;
import org.opendaylight.controller.md.sal.binding.api.ClusteredDataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataObjectModification;
import org.opendaylight.controller.md.sal.binding.api.DataTreeIdentifier;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.netconf.topology.singleton.api.NetconfTopologySingletonService;
import org.opendaylight.netconf.topology.singleton.impl.actors.NetconfNodeActor;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologySetup;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologyUtils;
import org.opendaylight.netconf.topology.singleton.messages.AskForMasterMountPoint;
import org.opendaylight.netconf.topology.singleton.messages.UnregisterSlaveMountPoint;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNodeConnectionStatus;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaRepository;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Managing and reacting on data tree changes in specific netconf node when master writes status to the operational
 * data store (e.g. handling lifecycle of slave mount point).
 */
class NetconfNodeManager
        implements ClusteredDataTreeChangeListener<Node>, NetconfTopologySingletonService, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(NetconfNodeManager.class);

    private NetconfTopologySetup setup;
    private ListenerRegistration<NetconfNodeManager> dataChangeListenerRegistration;
    private RemoteDeviceId id;
    private final SchemaSourceRegistry schemaRegistry;
    private final SchemaRepository schemaRepository;
    private ActorRef slaveActorRef;

    NetconfNodeManager(final NetconfTopologySetup setup,
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
            final NodeId nodeId = NetconfTopologyUtils.getNodeId(rootNode.getIdentifier());
            switch (rootNode.getModificationType()) {
                case SUBTREE_MODIFIED:
                    LOG.debug("Operational for node {} updated. Trying to register slave mount point", nodeId);
                    handleSlaveMountPoint(rootNode);
                    break;
                case WRITE:
                    if (rootNode.getDataBefore() != null) {
                        LOG.debug("Operational for node {} rewrited. Trying to register slave mount point", nodeId);
                    } else {
                        LOG.debug("Operational for node {} created. Trying to register slave mount point", nodeId);
                    }
                    handleSlaveMountPoint(rootNode);
                    break;
                case DELETE:
                    LOG.debug("Operational for node {} deleted. Trying to remove slave mount point", nodeId);
                    closeActor();
                    break;
                default:
                    LOG.debug("Uknown operation for node: {}", nodeId);
            }
        }
    }

    @Override
    public void close() {
        closeActor();

        if (dataChangeListenerRegistration != null) {
            dataChangeListenerRegistration.close();
            dataChangeListenerRegistration = null;
        }
    }

    private void closeActor() {
        if (slaveActorRef != null) {
            slaveActorRef.tell(new UnregisterSlaveMountPoint(), ActorRef.noSender());
            slaveActorRef.tell(PoisonPill.getInstance(), ActorRef.noSender());
            slaveActorRef = null;
        }
    }

    void registerDataTreeChangeListener(final String topologyId, final NodeKey key) {
        LOG.debug("Registering data tree change listener on node {}", key);
        dataChangeListenerRegistration = setup.getDataBroker().registerDataTreeChangeListener(
                new DataTreeIdentifier<>(LogicalDatastoreType.OPERATIONAL,
                        NetconfTopologyUtils.createTopologyNodeListPath(key, topologyId)), this);
    }

    private void handleSlaveMountPoint(final DataObjectModification<Node> rootNode) {
        @SuppressWarnings("ConstantConditions")
        final NetconfNode netconfNodeAfter = rootNode.getDataAfter().getAugmentation(NetconfNode.class);

        if (NetconfNodeConnectionStatus.ConnectionStatus.Connected.equals(netconfNodeAfter.getConnectionStatus())) {
            createActorRef();
            final String masterAddress = netconfNodeAfter.getClusteredConnectionStatus().getNetconfMasterNode();
            final String path = NetconfTopologyUtils.createActorPath(masterAddress,
                    NetconfTopologyUtils.createMasterActorName(id.getName(),
                            netconfNodeAfter.getClusteredConnectionStatus().getNetconfMasterNode()));
            setup.getActorSystem().actorSelection(path).tell(new AskForMasterMountPoint(), slaveActorRef);
        } else {            ;
            closeActor();
        }
    }

    private void createActorRef() {
        if (slaveActorRef == null) {
            slaveActorRef = setup.getActorSystem().actorOf(NetconfNodeActor.props(setup, id, schemaRegistry,
                    schemaRepository), id.getName());
        }
    }

    void refreshDevice(final NetconfTopologySetup netconfTopologyDeviceSetup, final RemoteDeviceId remoteDeviceId) {
        setup = netconfTopologyDeviceSetup;
        id = remoteDeviceId;
    }
}
