/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl;

import java.util.List;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSelection;
import org.apache.pekko.actor.PoisonPill;
import org.apache.pekko.dispatch.OnComplete;
import org.apache.pekko.pattern.AskTimeoutException;
import org.apache.pekko.pattern.Patterns;
import org.apache.pekko.util.Timeout;
import org.checkerframework.checker.lock.qual.GuardedBy;
import org.checkerframework.checker.lock.qual.Holding;
import org.opendaylight.mdsal.binding.api.DataObjectDeleted;
import org.opendaylight.mdsal.binding.api.DataObjectModification;
import org.opendaylight.mdsal.binding.api.DataObjectModified;
import org.opendaylight.mdsal.binding.api.DataObjectWritten;
import org.opendaylight.mdsal.binding.api.DataTreeChangeListener;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev241009.ConnectionOper.ConnectionStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev240911.NetconfNodeAugment;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yangtools.concepts.Registration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Managing and reacting on data tree changes in specific netconf node when master writes status to the operational
 * data store (e.g. handling lifecycle of slave mount point).
 */
class NetconfNodeManager implements DataTreeChangeListener<Node>, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfNodeManager.class);

    private final Timeout actorResponseWaitTime;
    private final DOMMountPointService mountPointService;

    private volatile NetconfTopologySetup setup;
    private volatile Registration dataChangeListenerRegistration;
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
    public void onDataTreeChanged(final List<DataTreeModification<Node>> changes) {
        for (var change : changes) {
            final var rootNode = change.getRootNode();
            final var nodeId = rootNode.coerceKeyStep(Node.class).key().getNodeId();
            switch (rootNode) {
                case DataObjectModified<Node> modified:
                    LOG.debug("{}: Operational state for node {} - subtree modified from {} to {}", id, nodeId,
                        modified.dataBefore(), modified.dataAfter());
                    handleSlaveMountPoint(modified);
                    break;
                case DataObjectWritten<Node> written:
                    if (written.dataBefore() != null) {
                        LOG.debug("{}: Operational state for node {} updated from {} to {}", id, nodeId,
                            written.dataBefore(), written.dataAfter());
                    } else {
                        LOG.debug("{}: Operational state for node {} created: {}", id, nodeId, written.dataAfter());
                    }
                    handleSlaveMountPoint(written);
                    break;
                case DataObjectDeleted<Node> ignored:
                    LOG.debug("{}: Operational state for node {} deleted.", id, nodeId);
                    unregisterSlaveMountpoint();
                    break;
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
        final var path = NetconfTopologyUtils.createTopologyNodeListPath(key, topologyId);
        LOG.debug("{}: Registering data tree change listener on path {}", id, path);
        dataChangeListenerRegistration = setup.getDataBroker().registerTreeChangeListener(
            LogicalDatastoreType.OPERATIONAL, path, this);
    }

    private synchronized void handleSlaveMountPoint(final DataObjectModification.WithDataAfter<Node> rootNode) {
        if (closed) {
            return;
        }

        final var netconfNodeAfter = rootNode.dataAfter().augmentation(NetconfNodeAugment.class).getNetconfNode();

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
