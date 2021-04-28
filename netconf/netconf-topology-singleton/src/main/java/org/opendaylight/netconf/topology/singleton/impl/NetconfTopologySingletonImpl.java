/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl;

import akka.actor.ActorRef;
import akka.cluster.Cluster;
import akka.util.Timeout;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import io.netty.util.concurrent.EventExecutor;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.controller.config.threadpool.ScheduledThreadPool;
import org.opendaylight.controller.config.threadpool.ThreadPool;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.netconf.client.NetconfClientDispatcher;
import org.opendaylight.netconf.sal.connect.api.DeviceActionFactory;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceHandler;
import org.opendaylight.netconf.sal.connect.api.SchemaResourceManager;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;
import org.opendaylight.netconf.sal.connect.netconf.sal.NetconfDeviceSalProvider;
import org.opendaylight.netconf.sal.connect.netconf.schema.mapping.BaseNetconfSchemas;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.netconf.topology.singleton.impl.actors.NetconfNodeActor;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologySetup;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologyUtils;
import org.opendaylight.netconf.topology.spi.AbstractNetconfTopology;
import org.opendaylight.netconf.topology.spi.NetconfConnectorDTO;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceRegistration;

final class NetconfTopologySingletonImpl extends AbstractNetconfTopology implements AutoCloseable {
    private final RemoteDeviceId remoteDeviceId;
    private final NetconfTopologySetup setup;
    private final Timeout actorResponseWaitTime;
    private final NetconfDeviceSalProvider salProvider;

    private ActorRef masterActorRef;
    private NetconfNodeManager netconfNodeManager;

    void becomeTopologyLeader() {
        // all nodes initially register listener
        unregisterNodeManager();

        final String masterAddress = Cluster.get(setup.getActorSystem()).selfAddress().toString();
        masterActorRef = setup.getActorSystem().actorOf(NetconfNodeActor.props(setup, remoteDeviceId,
                actorResponseWaitTime, mountPointService),
                NetconfTopologyUtils.createMasterActorName(remoteDeviceId.getName(), masterAddress));

        connectNode(setup.getNode().getNodeId(),setup.getNode());
    }

    void becomeTopologyFollower() {
        setup.getActorSystem().stop(masterActorRef);
        disconnectNodeFromFollower();
        registerNodeManager();
    }

    private ListenableFuture<Void> disconnectNodeFromFollower() {
        final NetconfConnectorDTO connectorDTO = activeConnectors.remove(setup.getNode().getNodeId());
        if (connectorDTO == null) {
            return Futures.immediateFailedFuture(new IllegalStateException(
                    "Unable to disconnect device from follower that is not connected"));
        }

        // first close {@code MasterSalFacade} to prevent it from writing to operational DS
        connectorDTO.getFacade().close();
        // close {@code NetconfDeviceCommunicator}
        connectorDTO.getCommunicator().close();
        // close {@code SchemaSourceRegistration} instances
        connectorDTO.getYanglibRegistrations().forEach(SchemaSourceRegistration::close);

        return Futures.immediateFuture(null);
    }

    @Override
    public void close() {
        unregisterNodeManager();
        salProvider.close();
    }

    private void registerNodeManager() {
        netconfNodeManager = new NetconfNodeManager(setup, remoteDeviceId, actorResponseWaitTime, mountPointService);
        netconfNodeManager.registerDataTreeChangeListener(setup.getTopologyId(), setup.getNode().key());
    }

    private void unregisterNodeManager() {
        netconfNodeManager.close();
        netconfNodeManager = null;
    }

    protected NetconfTopologySingletonImpl(final String topologyId, final NetconfClientDispatcher clientDispatcher,
            final EventExecutor eventExecutor, final ScheduledThreadPool keepaliveExecutor,
            final ThreadPool processingExecutor, final SchemaResourceManager schemaManager,
            final DataBroker dataBroker, final DOMMountPointService mountPointService,
            final AAAEncryptionService encryptionService, final DeviceActionFactory deviceActionFactory,
            final BaseNetconfSchemas baseSchemas, final RemoteDeviceId remoteDeviceId,
            final NetconfTopologySetup setup, final Timeout actorResponseWaitTime) {
        super(topologyId, clientDispatcher, eventExecutor, keepaliveExecutor, processingExecutor, schemaManager,
                dataBroker, mountPointService, encryptionService, deviceActionFactory, baseSchemas);
        this.remoteDeviceId = remoteDeviceId;
        this.setup = setup;
        this.actorResponseWaitTime = actorResponseWaitTime;
        // we tight lifecycle of {@code NetconfDeviceSalProvider} with this class
        // it has to be closed in {@code close} method
        this.salProvider = new NetconfDeviceSalProvider(remoteDeviceId, mountPointService, dataBroker);
        // followers are initially not notified they are followers thus we need to initialize listener for all nodes
        registerNodeManager();
    }

    @Override
    protected RemoteDeviceHandler<NetconfSessionPreferences> createSalFacade(final RemoteDeviceId id) {
        // everytime new connection to device is starting the new {@code MasterSalFacade} is created
        // and used in {@code NetconfConnectorDTO}
        // it is closed in {@code NetconfConnectorDTO} when connection is closing
        // we need to close it also when node becomes to be follower in this class
        return new MasterSalFacade(remoteDeviceId, setup.getActorSystem(), masterActorRef,
                actorResponseWaitTime, salProvider);
    }
}
