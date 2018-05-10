/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.singleton.impl;

import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.PoisonPill;
import akka.dispatch.OnComplete;
import akka.pattern.AskTimeoutException;
import akka.pattern.Patterns;
import akka.util.Timeout;
import java.util.Collection;
import javax.annotation.Nonnull;
import org.opendaylight.controller.md.sal.binding.api.ClusteredDataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataObjectModification;
import org.opendaylight.controller.md.sal.binding.api.DataTreeIdentifier;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPointService;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.netconf.topology.singleton.api.NetconfTopologySingletonService;
import org.opendaylight.netconf.topology.singleton.impl.actors.NetconfNodeActor;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologySetup;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologyUtils;
import org.opendaylight.netconf.topology.singleton.messages.AskForMasterMountPoint;
import org.opendaylight.netconf.topology.singleton.messages.RefreshSlaveActor;
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
import scala.concurrent.Future;

/**
 * Managing and reacting on data tree changes in specific netconf node when master writes status to the operational
 * data store (e.g. handling lifecycle of slave mount point).
 */
class NetconfNodeManager
        implements ClusteredDataTreeChangeListener<Node>, NetconfTopologySingletonService, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(NetconfNodeManager.class);

    private final Timeout actorResponseWaitTime;
    private final DOMMountPointService mountPointService;
    private final SchemaSourceRegistry schemaRegistry;
    private final SchemaRepository schemaRepository;

    private NetconfTopologySetup setup;
    private ListenerRegistration<NetconfNodeManager> dataChangeListenerRegistration;
    private RemoteDeviceId id;
    private ActorRef slaveActorRef;


    NetconfNodeManager(final NetconfTopologySetup setup,
                       final RemoteDeviceId id, final Timeout actorResponseWaitTime,
                       final DOMMountPointService mountPointService) {
        this.setup = setup;
        this.id = id;
        this.schemaRegistry = setup.getSchemaResourcesDTO().getSchemaRegistry();
        this.schemaRepository = setup.getSchemaResourcesDTO().getSchemaRepository();
        this.actorResponseWaitTime = actorResponseWaitTime;
        this.mountPointService = mountPointService;
    }

    @Override
    public void onDataTreeChanged(@Nonnull final Collection<DataTreeModification<Node>> changes) {
        for (final DataTreeModification<Node> change : changes) {
            final DataObjectModification<Node> rootNode = change.getRootNode();
            final NodeId nodeId = NetconfTopologyUtils.getNodeId(rootNode.getIdentifier());
            switch (rootNode.getModificationType()) {
                case SUBTREE_MODIFIED:
                    LOG.debug("{}: Operational state for node {} - subtree modified from {} to {}",
                            id, nodeId, rootNode.getDataBefore(), rootNode.getDataAfter());
                    handleSlaveMountPoint(rootNode);
                    break;
                case WRITE:
                    if (rootNode.getDataBefore() != null) {
                        LOG.debug("{}: Operational state for node {} updated from {} to {}",
                                id, nodeId, rootNode.getDataBefore(), rootNode.getDataAfter());
                    } else {
                        LOG.debug("{}: Operational state for node {} created: {}",
                                id, nodeId, rootNode.getDataAfter());
                    }
                    handleSlaveMountPoint(rootNode);
                    break;
                case DELETE:
                    LOG.debug("{}: Operational state for node {} deleted.", id, nodeId);
                    unregisterSlaveMountpoint();
                    break;
                default:
                    LOG.debug("{}: Uknown operation for node: {}", id, nodeId);
            }
        }
    }

    @Override
    public void close() {
        unregisterSlaveMountpoint();
        closeActor();
        if (dataChangeListenerRegistration != null) {
            dataChangeListenerRegistration.close();
            dataChangeListenerRegistration = null;
        }
    }

    private void closeActor() {
        if (slaveActorRef != null) {
            LOG.debug("{}: Sending poison pill to {}", id, slaveActorRef);
            slaveActorRef.tell(PoisonPill.getInstance(), ActorRef.noSender());
            slaveActorRef = null;
        }
    }

    private void unregisterSlaveMountpoint() {
        if (slaveActorRef != null) {
            LOG.debug("{}: Sending message to unregister slave mountpoint to {}", id, slaveActorRef);
            slaveActorRef.tell(new UnregisterSlaveMountPoint(), ActorRef.noSender());
        }
    }

    void registerDataTreeChangeListener(final String topologyId, final NodeKey key) {
        LOG.debug("{}: Registering data tree change listener on node {}", id, key);
        dataChangeListenerRegistration = setup.getDataBroker().registerDataTreeChangeListener(
                new DataTreeIdentifier<>(LogicalDatastoreType.OPERATIONAL,
                        NetconfTopologyUtils.createTopologyNodeListPath(key, topologyId)), this);
    }

    private void handleSlaveMountPoint(final DataObjectModification<Node> rootNode) {
        @SuppressWarnings("ConstantConditions")
        final NetconfNode netconfNodeAfter = rootNode.getDataAfter().getAugmentation(NetconfNode.class);

        if (NetconfNodeConnectionStatus.ConnectionStatus.Connected.equals(netconfNodeAfter.getConnectionStatus())) {
            createOrUpdateActorRef();

            final String masterAddress = netconfNodeAfter.getClusteredConnectionStatus().getNetconfMasterNode();
            final String masterActorPath = NetconfTopologyUtils.createActorPath(masterAddress,
                    NetconfTopologyUtils.createMasterActorName(id.getName(),
                            netconfNodeAfter.getClusteredConnectionStatus().getNetconfMasterNode()));

            final AskForMasterMountPoint askForMasterMountPoint = new AskForMasterMountPoint(slaveActorRef);
            final ActorSelection masterActor = setup.getActorSystem().actorSelection(masterActorPath);

            sendAskForMasterMountPointWithRetries(askForMasterMountPoint, masterActor);
        } else {
            unregisterSlaveMountpoint();
        }
    }

    private void sendAskForMasterMountPointWithRetries(AskForMasterMountPoint askForMasterMountPoint,
            ActorSelection masterActor) {
        final Future<Object> future = Patterns.ask(masterActor, askForMasterMountPoint, actorResponseWaitTime);
        future.onComplete(new OnComplete<Object>() {
            @Override
            public void onComplete(final Throwable failure, final Object response) {
                if (failure instanceof AskTimeoutException) {
                    LOG.warn("{}: Received \"{}\" while sending message to {} - retrying...", id, failure, masterActor);
                    sendAskForMasterMountPointWithRetries(askForMasterMountPoint, masterActor);
                } else if (failure != null) {
                    LOG.error("{}: Error sending message {} to {}. Slave mount point could not be created",
                            id, askForMasterMountPoint, masterActor, failure);
                } else {
                    LOG.debug("{}: {} message to {} succeeded", id, askForMasterMountPoint, masterActor);
                }
            }
        }, setup.getActorSystem().dispatcher());

        LOG.debug("{}: Sent {} message to master {}", id, askForMasterMountPoint, masterActor);
    }

    private void createOrUpdateActorRef() {
        if (slaveActorRef == null) {
            slaveActorRef = setup.getActorSystem().actorOf(NetconfNodeActor.props(setup, id, schemaRegistry,
                    schemaRepository, actorResponseWaitTime, mountPointService));
            LOG.debug("{}: Slave actor created with name {}", id, slaveActorRef);
        } else {
            slaveActorRef
                    .tell(new RefreshSlaveActor(setup, id, schemaRegistry, schemaRepository, actorResponseWaitTime),
                            ActorRef.noSender());
        }
    }

    void refreshDevice(final NetconfTopologySetup netconfTopologyDeviceSetup, final RemoteDeviceId remoteDeviceId) {
        setup = netconfTopologyDeviceSetup;
        id = remoteDeviceId;
    }
}
