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
import org.checkerframework.checker.lock.qual.GuardedBy;
import org.checkerframework.checker.lock.qual.Holding;
import org.opendaylight.mdsal.binding.api.ClusteredDataTreeChangeListener;
import org.opendaylight.mdsal.binding.api.DataObjectModification;
import org.opendaylight.mdsal.binding.api.DataTreeIdentifier;
import org.opendaylight.mdsal.binding.api.DataTreeModification;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.topology.singleton.impl.actors.NetconfNodeActor;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologySetup;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologyUtils;
import org.opendaylight.netconf.topology.singleton.messages.AskForMasterMountPoint;
import org.opendaylight.netconf.topology.singleton.messages.RefreshSlaveActor;
import org.opendaylight.netconf.topology.singleton.messages.UnregisterSlaveMountPoint;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev230430.ConnectionOper.ConnectionStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev221225.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Managing and reacting on data tree changes in specific netconf node when master writes status to the operational
 * data store (e.g. handling lifecycle of slave mount point).
 */
class NetconfNodeManager implements ClusteredDataTreeChangeListener<Node>, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfNodeManager.class);

    private final Timeout actorResponseWaitTime;
    private final DOMMountPointService mountPointService;

    private volatile NetconfTopologySetup setup;
    private volatile ListenerRegistration<NetconfNodeManager> dataChangeListenerRegistration;
    private volatile RemoteDeviceId id;

    @GuardedBy("this")
    private ActorRef slaveActorRef;

    @GuardedBy("this")
    private boolean closed;

    @GuardedBy("this")
    private int lastUpdateCount;

    NetconfNodeManager(final NetconfTopologySetup setup, final RemoteDeviceId id, final Timeout actorResponseWaitTime,
                       final DOMMountPointService mountPointService) {
        this.setup = setup;
        this.id = id;
        this.actorResponseWaitTime = actorResponseWaitTime;
        this.mountPointService = mountPointService;
    }

    @Override
    public void onDataTreeChanged(final Collection<DataTreeModification<Node>> changes) {
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
    public synchronized void close() {
        if (closed) {
            return;
        }

        closed = true;
        closeActor();
        if (dataChangeListenerRegistration != null) {
            dataChangeListenerRegistration.close();
            dataChangeListenerRegistration = null;
        }
    }

    @Holding("this")
    private void closeActor() {
        if (slaveActorRef != null) {
            LOG.debug("{}: Sending poison pill to {}", id, slaveActorRef);
            slaveActorRef.tell(PoisonPill.getInstance(), ActorRef.noSender());
            slaveActorRef = null;
        }
    }

    private synchronized void unregisterSlaveMountpoint() {
        lastUpdateCount++;
        if (slaveActorRef != null) {
            LOG.debug("{}: Sending message to unregister slave mountpoint to {}", id, slaveActorRef);
            slaveActorRef.tell(new UnregisterSlaveMountPoint(), ActorRef.noSender());
        }
    }

    void registerDataTreeChangeListener(final String topologyId, final NodeKey key) {
        final InstanceIdentifier<Node> path = NetconfTopologyUtils.createTopologyNodeListPath(key, topologyId);
        LOG.debug("{}: Registering data tree change listener on path {}", id, path);
        dataChangeListenerRegistration = setup.getDataBroker().registerDataTreeChangeListener(
                DataTreeIdentifier.create(LogicalDatastoreType.OPERATIONAL, path), this);
    }

    private synchronized void handleSlaveMountPoint(final DataObjectModification<Node> rootNode) {
        if (closed) {
            return;
        }

        @SuppressWarnings("ConstantConditions")
        final NetconfNode netconfNodeAfter = rootNode.getDataAfter().augmentation(NetconfNode.class);

        if (ConnectionStatus.Connected == netconfNodeAfter.getConnectionStatus()) {
            lastUpdateCount++;
            createOrUpdateActorRef();

            final String masterAddress = netconfNodeAfter.getClusteredConnectionStatus().getNetconfMasterNode();
            final String masterActorPath = NetconfTopologyUtils.createActorPath(masterAddress,
                    NetconfTopologyUtils.createMasterActorName(id.name(),
                            netconfNodeAfter.getClusteredConnectionStatus().getNetconfMasterNode()));

            final AskForMasterMountPoint askForMasterMountPoint = new AskForMasterMountPoint(slaveActorRef);
            final ActorSelection masterActor = setup.getActorSystem().actorSelection(masterActorPath);

            LOG.debug("{}: Sending {} message to master {}", id, askForMasterMountPoint, masterActor);

            sendAskForMasterMountPointWithRetries(askForMasterMountPoint, masterActor, 1, lastUpdateCount);
        } else {
            unregisterSlaveMountpoint();
        }
    }

    @Holding("this")
    private void sendAskForMasterMountPointWithRetries(final AskForMasterMountPoint askForMasterMountPoint,
            final ActorSelection masterActor, final int tries, final int updateCount) {
        Patterns.ask(masterActor, askForMasterMountPoint, actorResponseWaitTime).onComplete(new OnComplete<>() {
            @Override
            public void onComplete(final Throwable failure, final Object response) {
                synchronized (this) {
                    // Ignore the response if we were since closed or another notification update occurred.
                    if (closed || updateCount != lastUpdateCount) {
                        return;
                    }

                    if (failure instanceof AskTimeoutException) {
                        if (tries <= 5 || tries % 10 == 0) {
                            LOG.warn("{}: Failed to send message to {} - retrying...", id, masterActor, failure);
                        }
                        sendAskForMasterMountPointWithRetries(askForMasterMountPoint, masterActor, tries + 1,
                            updateCount);
                    } else if (failure != null) {
                        LOG.error("{}: Failed to send message {} to {}. Slave mount point could not be created",
                            id, askForMasterMountPoint, masterActor, failure);
                    } else {
                        LOG.debug("{}: {} message to {} succeeded", id, askForMasterMountPoint, masterActor);
                    }
                }
            }
        }, setup.getActorSystem().dispatcher());
    }

    @Holding("this")
    private void createOrUpdateActorRef() {
        if (slaveActorRef == null) {
            slaveActorRef = setup.getActorSystem().actorOf(NetconfNodeActor.props(setup, id, actorResponseWaitTime,
                    mountPointService));
            LOG.debug("{}: Slave actor created with name {}", id, slaveActorRef);
        } else {
            slaveActorRef.tell(new RefreshSlaveActor(setup, id, actorResponseWaitTime), ActorRef.noSender());
        }
    }

    void refreshDevice(final NetconfTopologySetup netconfTopologyDeviceSetup, final RemoteDeviceId remoteDeviceId) {
        setup = netconfTopologyDeviceSetup;
        id = remoteDeviceId;
    }
}
