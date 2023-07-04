/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
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
import com.google.common.annotations.VisibleForTesting;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.netconf.client.mdsal.api.DeviceActionFactory;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.api.SchemaResourceManager;
import org.opendaylight.netconf.topology.singleton.impl.actors.NetconfNodeActor;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologySetup;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologyUtils;
import org.opendaylight.netconf.topology.singleton.messages.RefreshSetupMasterActorData;
import org.opendaylight.netconf.topology.spi.NetconfClientConfigurationBuilderFactory;
import org.opendaylight.netconf.topology.spi.NetconfNodeHandler;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.optional.rev221225.NetconfNodeAugmentedOptional;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev221225.NetconfNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class NetconfNodeContext implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfNodeContext.class);

    private final DeviceActionFactory deviceActionFactory;
    private final SchemaResourceManager schemaManager;
    private final NetconfClientConfigurationBuilderFactory builderFactory;
    private final DOMMountPointService mountPointService;
    private final RemoteDeviceId remoteDeviceId;
    private final NetconfTopologySetup setup;
    private final Timeout actorResponseWaitTime;

    private ActorRef masterActorRef;
    private MasterSalFacade masterSalFacade;
    private NetconfNodeManager netconfNodeManager;
    private NetconfNodeHandler nodeHandler;

    NetconfNodeContext(final NetconfTopologySetup setup, final SchemaResourceManager schemaManager,
            final DOMMountPointService mountPointService, final NetconfClientConfigurationBuilderFactory builderFactory,
            final DeviceActionFactory deviceActionFactory, final RemoteDeviceId remoteDeviceId,
            final Timeout actorResponseWaitTime) {
        this.setup = requireNonNull(setup);
        this.schemaManager = requireNonNull(schemaManager);
        this.mountPointService = requireNonNull(mountPointService);
        this.builderFactory = requireNonNull(builderFactory);
        this.deviceActionFactory = deviceActionFactory;
        this.remoteDeviceId = requireNonNull(remoteDeviceId);
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

        connectNode();
    }

    void becomeTopologyFollower() {
        registerNodeManager();

        // disconnect device from this node and listen for changes from leader
        dropNode();

        if (masterActorRef != null) {
            // was leader before
            setup.getActorSystem().stop(masterActorRef);
        }
    }

    void refreshSetupConnection(final NetconfTopologySetup netconfTopologyDeviceSetup, final RemoteDeviceId device) {
        dropNode();

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
                        connectNode();
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

    private void connectNode() {
        final var configNode = setup.getNode();

        final var netconfNode = configNode.augmentation(NetconfNode.class);
        final var nodeOptional = configNode.augmentation(NetconfNodeAugmentedOptional.class);

        requireNonNull(netconfNode.getHost());
        requireNonNull(netconfNode.getPort());

        // Instantiate the handler ...
        masterSalFacade = createSalFacade(netconfNode.requireLockDatastore());

        nodeHandler = new NetconfNodeHandler(setup.getNetconfClientDispatcher(), setup.getEventExecutor(),
            setup.getKeepaliveExecutor(), setup.getBaseSchemas(), schemaManager, setup.getProcessingExecutor(),
            builderFactory, deviceActionFactory, masterSalFacade, remoteDeviceId, configNode.getNodeId(), netconfNode,
            nodeOptional);
        nodeHandler.connect();
    }

    private void dropNode() {
        if (nodeHandler != null) {
            nodeHandler.close();
            nodeHandler = null;
        }
    }

    @VisibleForTesting
    MasterSalFacade createSalFacade(final boolean lockDatastore) {
        return new MasterSalFacade(remoteDeviceId, setup.getActorSystem(), masterActorRef, actorResponseWaitTime,
            mountPointService, setup.getDataBroker(), lockDatastore);
    }
}
