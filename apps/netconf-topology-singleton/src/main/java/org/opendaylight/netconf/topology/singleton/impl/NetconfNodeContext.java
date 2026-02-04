/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.cluster.Cluster;
import org.apache.pekko.dispatch.OnComplete;
import org.apache.pekko.pattern.Patterns;
import org.apache.pekko.util.Timeout;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev251205.credentials.Credentials;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.optional.rev221225.NetconfNodeAugmentedOptional;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev251205.NetconfNodeAugment;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev251205.network.topology.topology.topology.types.topology.netconf.SshTransportTopologyParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class NetconfNodeContext implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfNodeContext.class);

    private final @NonNull DeviceActionFactory deviceActionFactory;
    private final @NonNull SchemaResourceManager schemaManager;
    private final @NonNull NetconfClientConfigurationBuilderFactory builderFactory;
    private final @NonNull DOMMountPointService mountPointService;
    private final @NonNull Timeout actorResponseWaitTime;

    private @NonNull RemoteDeviceId remoteDeviceId;
    private @NonNull NetconfTopologySetup setup;
    private @Nullable ActorRef masterActorRef;
    private @Nullable MasterSalFacade masterSalFacade;
    private @NonNull NetconfNodeManager netconfNodeManager;
    private @Nullable NetconfNodeHandler nodeHandler;

    @NonNullByDefault
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
        this.actorResponseWaitTime = requireNonNull(actorResponseWaitTime);
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
        setup = requireNonNull(netconfTopologyDeviceSetup);
        remoteDeviceId = requireNonNull(device);

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

    /**
     * Refresh connection using new topology ssh parameters.
     *
     * @param sshParams  new topology ssh parameters
     * @param device     {@link RemoteDeviceId} of affected device
     */
    void refreshSshParamsConnection(final SshTransportTopologyParameters sshParams, final RemoteDeviceId device) {
        refreshSetupConnection(setupWithNewSshParams(sshParams), device);
    }

    void refreshDevice(final NetconfTopologySetup netconfTopologyDeviceSetup, final RemoteDeviceId deviceId) {
        setup = requireNonNull(netconfTopologyDeviceSetup);
        remoteDeviceId = requireNonNull(deviceId);
        netconfNodeManager.refreshDevice(netconfTopologyDeviceSetup, deviceId);
    }

    /**
     * Refresh device using new topology ssh parameters.
     *
     * @param sshParams  new topology ssh parameters
     * @param deviceId   {@link RemoteDeviceId} of affected device
     */
    void refreshDevice(final SshTransportTopologyParameters sshParams, final RemoteDeviceId deviceId) {
        setup = requireNonNull(setupWithNewSshParams(sshParams));
        netconfNodeManager.refreshDevice(setup, deviceId);
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

        final var netconfNode = requireNonNull(configNode.augmentation(NetconfNodeAugment.class)).getNetconfNode();
        final var nodeOptional = configNode.augmentation(NetconfNodeAugmentedOptional.class);

        requireNonNull(netconfNode.getHost());
        requireNonNull(netconfNode.getPort());

        // Instantiate the handler ...
        masterSalFacade = createSalFacade(netconfNode.getCredentials(), netconfNode.getLockDatastore());

        nodeHandler = new NetconfNodeHandler(setup.getNetconfClientFactory(), setup.getTimer(),
            setup.getBaseSchemaProvider(), schemaManager, setup.getSchemaAssembler(), builderFactory,
            deviceActionFactory, masterSalFacade, remoteDeviceId, configNode.getNodeId(), netconfNode, nodeOptional,
            setup.getSshParams());
        nodeHandler.connect();
    }

    private void dropNode() {
        if (nodeHandler != null) {
            nodeHandler.close();
            nodeHandler = null;
        }
    }

    /**
     * Update setup with new topology ssh parameters.
     *
     * @param  sshParams new topology ssh parameters
     * @return updated setup
     */
    private NetconfTopologySetup setupWithNewSshParams(final SshTransportTopologyParameters sshParams) {
        return NetconfTopologySetup.builder()
            .setClusterSingletonServiceProvider(setup.getClusterSingletonServiceProvider())
            .setBaseSchemaProvider(setup.getBaseSchemaProvider())
            .setDataBroker(setup.getDataBroker())
            .setInstanceIdentifier(setup.getInstanceIdentifier())
            .setNode(setup.getNode())
            .setActorSystem(setup.getActorSystem())
            .setTimer(setup.getTimer())
            .setSchemaAssembler(setup.getSchemaAssembler())
            .setTopologyId(setup.getTopologyId())
            .setNetconfClientFactory(setup.getNetconfClientFactory())
            .setDeviceSchemaProvider(setup.getDeviceSchemaProvider())
            .setIdleTimeout(setup.getIdleTimeout())
            .setSshParams(sshParams)
            .build();
    }

    @VisibleForTesting
    MasterSalFacade createSalFacade(final Credentials credentials, final boolean lockDatastore) {
        return new MasterSalFacade(remoteDeviceId, credentials, setup.getActorSystem(), masterActorRef,
            actorResponseWaitTime, mountPointService, setup.getDataBroker(), lockDatastore);
    }
}
