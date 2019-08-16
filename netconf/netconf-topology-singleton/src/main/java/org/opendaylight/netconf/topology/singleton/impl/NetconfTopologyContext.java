/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl;

import static java.util.Objects.requireNonNull;

import akka.actor.ActorRef;
import akka.cluster.Cluster;
import akka.dispatch.OnComplete;
import akka.pattern.Patterns;
import akka.util.Timeout;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.singleton.common.api.ClusterSingletonService;
import org.opendaylight.mdsal.singleton.common.api.ServiceGroupIdentifier;
import org.opendaylight.netconf.sal.connect.api.DeviceActionFactory;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.netconf.topology.singleton.api.RemoteDeviceConnector;
import org.opendaylight.netconf.topology.singleton.impl.actors.NetconfNodeActor;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologySetup;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologyUtils;
import org.opendaylight.netconf.topology.singleton.messages.RefreshSetupMasterActorData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.opendaylight.yangtools.util.concurrent.FluentFutures;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.concurrent.Future;

class NetconfTopologyContext implements ClusterSingletonService, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(NetconfTopologyContext.class);

    private final ServiceGroupIdentifier serviceGroupIdent;
    private final Timeout actorResponseWaitTime;
    private final DOMMountPointService mountService;
    private final DeviceActionFactory deviceActionFactory;

    private NetconfTopologySetup netconfTopologyDeviceSetup;
    private RemoteDeviceId remoteDeviceId;
    private RemoteDeviceConnector remoteDeviceConnector;
    private NetconfNodeManager netconfNodeManager;
    private ActorRef masterActorRef;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private volatile boolean isMaster;

    NetconfTopologyContext(final NetconfTopologySetup netconfTopologyDeviceSetup,
            final ServiceGroupIdentifier serviceGroupIdent, final Timeout actorResponseWaitTime,
            final DOMMountPointService mountService, final DeviceActionFactory deviceActionFactory) {
        this.netconfTopologyDeviceSetup = requireNonNull(netconfTopologyDeviceSetup);
        this.serviceGroupIdent = serviceGroupIdent;
        this.actorResponseWaitTime = actorResponseWaitTime;
        this.mountService = mountService;
        this.deviceActionFactory = deviceActionFactory;

        remoteDeviceId = NetconfTopologyUtils.createRemoteDeviceId(netconfTopologyDeviceSetup.getNode().getNodeId(),
            netconfTopologyDeviceSetup.getNode().augmentation(NetconfNode.class));
        remoteDeviceConnector = new RemoteDeviceConnectorImpl(netconfTopologyDeviceSetup, remoteDeviceId,
            deviceActionFactory);
        netconfNodeManager = createNodeDeviceManager();
    }

    @Override
    public void instantiateServiceInstance() {
        LOG.info("Master was selected: {}", remoteDeviceId.getHost().getIpAddress());

        isMaster = true;

        // master should not listen on netconf-node operational datastore
        if (netconfNodeManager != null) {
            netconfNodeManager.close();
            netconfNodeManager = null;
        }

        if (!closed.get()) {
            final String masterAddress =
                    Cluster.get(netconfTopologyDeviceSetup.getActorSystem()).selfAddress().toString();
            masterActorRef = netconfTopologyDeviceSetup.getActorSystem().actorOf(NetconfNodeActor.props(
                    netconfTopologyDeviceSetup, remoteDeviceId, actorResponseWaitTime, mountService),
                    NetconfTopologyUtils.createMasterActorName(remoteDeviceId.getName(), masterAddress));

            remoteDeviceConnector.startRemoteDeviceConnection(newMasterSalFacade());
        }

    }

    // called when master is down/changed to slave
    @Override
    public ListenableFuture<?> closeServiceInstance() {

        if (!closed.get()) {
            // in case that master changes role to slave, new NodeDeviceManager must be created and listener registered
            netconfNodeManager = createNodeDeviceManager();
        }
        stopDeviceConnectorAndActor();

        return FluentFutures.immediateNullFluentFuture();
    }

    @Override
    public ServiceGroupIdentifier getIdentifier() {
        return serviceGroupIdent;
    }

    private NetconfNodeManager createNodeDeviceManager() {
        final NetconfNodeManager ndm =
                new NetconfNodeManager(netconfTopologyDeviceSetup, remoteDeviceId, actorResponseWaitTime, mountService);
        ndm.registerDataTreeChangeListener(netconfTopologyDeviceSetup.getTopologyId(),
                netconfTopologyDeviceSetup.getNode().key());

        return ndm;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        if (netconfNodeManager != null) {
            netconfNodeManager.close();
        }
        stopDeviceConnectorAndActor();

    }

    /**
     * Refresh, if configuration data was changed.
     * @param setup new setup
     */
    void refresh(final @NonNull NetconfTopologySetup setup) {
        netconfTopologyDeviceSetup = requireNonNull(setup);
        remoteDeviceId = NetconfTopologyUtils.createRemoteDeviceId(netconfTopologyDeviceSetup.getNode().getNodeId(),
                netconfTopologyDeviceSetup.getNode().augmentation(NetconfNode.class));

        if (isMaster) {
            remoteDeviceConnector.stopRemoteDeviceConnection();
        }
        if (!isMaster) {
            netconfNodeManager.refreshDevice(netconfTopologyDeviceSetup, remoteDeviceId);
        }
        remoteDeviceConnector = new RemoteDeviceConnectorImpl(netconfTopologyDeviceSetup, remoteDeviceId,
            deviceActionFactory);

        if (isMaster) {
            final Future<Object> future = Patterns.ask(masterActorRef, new RefreshSetupMasterActorData(
                netconfTopologyDeviceSetup, remoteDeviceId), actorResponseWaitTime);
            future.onComplete(new OnComplete<Object>() {
                @Override
                public void onComplete(final Throwable failure, final Object success) {
                    if (failure != null) {
                        LOG.error("Failed to refresh master actor data", failure);
                        return;
                    }
                    remoteDeviceConnector.startRemoteDeviceConnection(newMasterSalFacade());
                }
            }, netconfTopologyDeviceSetup.getActorSystem().dispatcher());
        }
    }

    private void stopDeviceConnectorAndActor() {
        if (!stopped.compareAndSet(false, true)) {
            return;
        }
        if (remoteDeviceConnector != null) {
            remoteDeviceConnector.stopRemoteDeviceConnection();
        }

        if (masterActorRef != null) {
            netconfTopologyDeviceSetup.getActorSystem().stop(masterActorRef);
            masterActorRef = null;
        }
    }

    protected MasterSalFacade newMasterSalFacade() {
        return new MasterSalFacade(remoteDeviceId, netconfTopologyDeviceSetup.getActorSystem(), masterActorRef,
                actorResponseWaitTime, mountService, netconfTopologyDeviceSetup.getDataBroker());
    }
}