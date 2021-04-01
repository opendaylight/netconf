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
import akka.actor.ActorSystem;
import akka.cluster.Cluster;
import akka.dispatch.OnComplete;
import akka.pattern.Patterns;
import akka.util.Timeout;
import com.google.common.util.concurrent.ListenableFuture;
import io.netty.util.concurrent.EventExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.controller.config.threadpool.ScheduledThreadPool;
import org.opendaylight.controller.config.threadpool.ThreadPool;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.singleton.common.api.ClusterSingletonService;
import org.opendaylight.mdsal.singleton.common.api.ServiceGroupIdentifier;
import org.opendaylight.netconf.client.NetconfClientDispatcher;
import org.opendaylight.netconf.sal.connect.api.DeviceActionFactory;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceHandler;
import org.opendaylight.netconf.sal.connect.api.SchemaResourceManager;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;
import org.opendaylight.netconf.sal.connect.netconf.schema.mapping.BaseNetconfSchemas;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.netconf.topology.singleton.impl.actors.NetconfNodeActor;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologySetup;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologyUtils;
import org.opendaylight.netconf.topology.singleton.messages.RefreshSetupMasterActorData;
import org.opendaylight.netconf.topology.spi.AbstractNetconfTopology;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.util.concurrent.FluentFutures;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.concurrent.Future;

class NetconfTopologyContext extends AbstractNetconfTopology implements ClusterSingletonService, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfTopologyContext.class);

    private final ServiceGroupIdentifier serviceGroupIdent;
    private final Timeout actorResponseWaitTime;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicBoolean stopped = new AtomicBoolean(false);

    // changed on refresh
    private ActorSystem actorSystem;
    private Node node;
    private RemoteDeviceId remoteDeviceId;
    private NetconfTopologySetup setup;

    // changed on owner->follower transition
    private NetconfNodeManager netconfNodeManager;
    private ActorRef masterActorRef;

    private volatile boolean isMaster;

    protected NetconfTopologyContext(final String topologyId, final NetconfClientDispatcher clientDispatcher,
            final EventExecutor eventExecutor, final ScheduledThreadPool keepaliveExecutor,
            final ThreadPool processingExecutor, final SchemaResourceManager schemaManager,
            final DataBroker dataBroker, final DOMMountPointService mountPointService,
            final AAAEncryptionService encryptionService, final DeviceActionFactory deviceActionFactory,
            final BaseNetconfSchemas baseSchemas, final Timeout actorResponseWaitTime,
            final ActorSystem actorSystem, final Node node,
            final ServiceGroupIdentifier serviceGroupIdent, final NetconfTopologySetup setup) {
        super(topologyId, clientDispatcher, eventExecutor, keepaliveExecutor, processingExecutor, schemaManager,
                dataBroker, mountPointService, encryptionService, deviceActionFactory, baseSchemas);
        this.serviceGroupIdent = serviceGroupIdent;
        this.actorResponseWaitTime = actorResponseWaitTime;
        this.actorSystem = actorSystem;
        this.node = node;
        this.remoteDeviceId = NetconfTopologyUtils.createRemoteDeviceId(node.getNodeId(),
                node.augmentation(NetconfNode.class));
        this.setup = setup;
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
            final String masterAddress = Cluster.get(actorSystem).selfAddress().toString();
            masterActorRef = actorSystem.actorOf(NetconfNodeActor.props(setup, remoteDeviceId, actorResponseWaitTime,
                    mountPointService), NetconfTopologyUtils.createMasterActorName(remoteDeviceId.getName(),
                    masterAddress));
            connectNode(node.getNodeId(), node);
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
        final NetconfNodeManager ndm = new NetconfNodeManager(setup, remoteDeviceId,
                actorResponseWaitTime, mountPointService);
        ndm.registerDataTreeChangeListener(topologyId, node.key());
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
        this.setup = requireNonNull(setup);
        if (isMaster) {
            disconnectNode(node.getNodeId());
        }
        this.node = setup.getNode();
        this.actorSystem = setup.getActorSystem();
        this.remoteDeviceId = NetconfTopologyUtils.createRemoteDeviceId(node.getNodeId(),
                node.augmentation(NetconfNode.class));

        if (!isMaster) {
            netconfNodeManager.refreshDevice(setup, remoteDeviceId);
        }

        if (isMaster) {
            final Future<Object> future = Patterns.ask(masterActorRef, new RefreshSetupMasterActorData(
                setup, remoteDeviceId), actorResponseWaitTime);
            future.onComplete(new OnComplete<Object>() {
                @Override
                public void onComplete(final Throwable failure, final Object success) {
                    if (failure != null) {
                        LOG.error("Failed to refresh master actor data", failure);
                        return;
                    }
                    connectNode(node.getNodeId(), node);
                }
            }, actorSystem.dispatcher());
        }
    }

    private void stopDeviceConnectorAndActor() {
        if (!stopped.compareAndSet(false, true)) {
            return;
        }

        disconnectNode(node.getNodeId());

        if (masterActorRef != null) {
            actorSystem.stop(masterActorRef);
            masterActorRef = null;
        }
    }

    @Override
    protected RemoteDeviceHandler<NetconfSessionPreferences> createSalFacade(final RemoteDeviceId id) {
        return new MasterSalFacade(remoteDeviceId, actorSystem, masterActorRef, actorResponseWaitTime,
                mountPointService, dataBroker, topologyId);
    }
}