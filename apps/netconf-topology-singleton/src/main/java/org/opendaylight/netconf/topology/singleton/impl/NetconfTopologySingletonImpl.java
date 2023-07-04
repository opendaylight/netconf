/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl;

import akka.actor.ActorRef;
import akka.cluster.Cluster;
import akka.dispatch.OnComplete;
import akka.pattern.Patterns;
import akka.util.Timeout;
import io.netty.util.concurrent.EventExecutor;
import org.opendaylight.controller.config.threadpool.ScheduledThreadPool;
import org.opendaylight.controller.config.threadpool.ThreadPool;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.netconf.client.NetconfClientDispatcher;
import org.opendaylight.netconf.client.mdsal.api.BaseNetconfSchemas;
import org.opendaylight.netconf.client.mdsal.api.DeviceActionFactory;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceHandler;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.api.SchemaResourceManager;
import org.opendaylight.netconf.topology.singleton.impl.actors.NetconfNodeActor;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologySetup;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologyUtils;
import org.opendaylight.netconf.topology.singleton.messages.RefreshSetupMasterActorData;
import org.opendaylight.netconf.topology.spi.AbstractNetconfTopology;
import org.opendaylight.netconf.topology.spi.NetconfClientConfigurationBuilderFactory;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class NetconfTopologySingletonImpl extends AbstractNetconfTopology implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfTopologySingletonImpl.class);

    private final RemoteDeviceId remoteDeviceId;
    private final NetconfTopologySetup setup;
    private final Timeout actorResponseWaitTime;

    private ActorRef masterActorRef;
    private MasterSalFacade masterSalFacade;
    private NetconfNodeManager netconfNodeManager;

    NetconfTopologySingletonImpl(final String topologyId, final NetconfClientDispatcher clientDispatcher,
            final EventExecutor eventExecutor, final ScheduledThreadPool keepaliveExecutor,
            final ThreadPool processingExecutor, final SchemaResourceManager schemaManager,
            final DataBroker dataBroker, final DOMMountPointService mountPointService,
            final NetconfClientConfigurationBuilderFactory builderFactory,
            final DeviceActionFactory deviceActionFactory, final BaseNetconfSchemas baseSchemas,
            final RemoteDeviceId remoteDeviceId, final NetconfTopologySetup setup,
            final Timeout actorResponseWaitTime) {
        super(topologyId, clientDispatcher, eventExecutor, keepaliveExecutor, processingExecutor, schemaManager,
                dataBroker, mountPointService, builderFactory, deviceActionFactory, baseSchemas);
        this.remoteDeviceId = remoteDeviceId;
        this.setup = setup;
        this.actorResponseWaitTime = actorResponseWaitTime;
        registerNodeManager();
    }

    void becomeTopologyLeader() {
        // all nodes initially register listener
        unregisterNodeManager();

        // create master actor reference
        final var masterAddress = Cluster.get(setup.getActorSystem()).selfAddress().toString();
        masterActorRef = setup.getActorSystem().actorOf(NetconfNodeActor.props(setup, remoteDeviceId,
                actorResponseWaitTime, mountPointService), NetconfTopologyUtils.createMasterActorName(
                remoteDeviceId.name(), masterAddress));

        // setup connection to device
        ensureNode(setup.getNode());
    }

    void becomeTopologyFollower() {
        registerNodeManager();
        // disconnect device from this node and listen for changes from leader
        deleteNode(setup.getNode().getNodeId());
        if (masterActorRef != null) {
            // was leader before
            setup.getActorSystem().stop(masterActorRef);
        }
    }

    void refreshSetupConnection(final NetconfTopologySetup netconfTopologyDeviceSetup, final RemoteDeviceId device) {
        Patterns.ask(masterActorRef, new RefreshSetupMasterActorData(netconfTopologyDeviceSetup, device),
            actorResponseWaitTime).onComplete(
                new OnComplete<>() {
                    @Override
                    public void onComplete(final Throwable failure, final Object success) {
                        if (failure != null) {
                            LOG.error("Failed to refresh master actor data", failure);
                            return;
                        }
                        LOG.debug("Succeed to refresh Master Action data. Creating Connector...");
                        setupConnection(setup.getNode().getNodeId(), setup.getNode());
                    }
                }, netconfTopologyDeviceSetup.getActorSystem().dispatcher());
    }

    void refreshDevice(final NetconfTopologySetup netconfTopologyDeviceSetup, final RemoteDeviceId deviceId) {
        netconfNodeManager.refreshDevice(netconfTopologyDeviceSetup, deviceId);
    }

    private void registerNodeManager() {
        netconfNodeManager = new NetconfNodeManager(setup, remoteDeviceId, actorResponseWaitTime, mountPointService);
        netconfNodeManager.registerDataTreeChangeListener(setup.getTopologyId(), setup.getNode().key());
    }

    private void unregisterNodeManager() {
        netconfNodeManager.close();
    }

    void dropNode(final NodeId nodeId) {
        deleteNode(nodeId);
    }

    @Override
    public void close() {
        unregisterNodeManager();

        // we expect that even leader node is going to be follower when data are deleted
        // thus we do not close connection and actor here
        // anyway we need to close topology and transaction chain on all nodes that were leaders
        if (masterSalFacade != null) {
            // node was at least once leader
            masterSalFacade.close();
        }
    }

    @Override
    public RemoteDeviceHandler createSalFacade(final RemoteDeviceId deviceId, final boolean lockDatastore) {
        masterSalFacade = new MasterSalFacade(deviceId, setup.getActorSystem(), masterActorRef,
                actorResponseWaitTime, mountPointService, dataBroker, lockDatastore);
        return masterSalFacade;
    }
}
