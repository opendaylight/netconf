/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl;

import static java.util.Objects.requireNonNull;

import akka.util.Timeout;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.ListenableFuture;
import io.netty.util.concurrent.EventExecutor;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.controller.config.threadpool.ScheduledThreadPool;
import org.opendaylight.controller.config.threadpool.ThreadPool;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.singleton.common.api.ClusterSingletonService;
import org.opendaylight.mdsal.singleton.common.api.ServiceGroupIdentifier;
import org.opendaylight.netconf.client.NetconfClientDispatcher;
import org.opendaylight.netconf.client.mdsal.api.BaseNetconfSchemas;
import org.opendaylight.netconf.client.mdsal.api.CredentialProvider;
import org.opendaylight.netconf.client.mdsal.api.DeviceActionFactory;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.api.SchemaResourceManager;
import org.opendaylight.netconf.client.mdsal.api.SslHandlerFactoryProvider;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologySetup;
import org.opendaylight.netconf.topology.spi.NetconfNodeUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev221225.NetconfNode;
import org.opendaylight.yangtools.util.concurrent.FluentFutures;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class NetconfTopologyContext implements ClusterSingletonService, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfTopologyContext.class);

    private final @NonNull ServiceGroupIdentifier serviceGroupIdent;
    private final NetconfTopologySingletonImpl topologySingleton;

    private volatile boolean closed;
    private volatile boolean isMaster;

    private RemoteDeviceId remoteDeviceId;

    NetconfTopologyContext(final String topologyId, final NetconfClientDispatcher clientDispatcher,
            final EventExecutor eventExecutor, final ScheduledThreadPool keepaliveExecutor,
            final ThreadPool processingExecutor, final SchemaResourceManager schemaManager,
            final DataBroker dataBroker, final DOMMountPointService mountPointService,
            final AAAEncryptionService encryptionService, final DeviceActionFactory deviceActionFactory,
            final BaseNetconfSchemas baseSchemas, final Timeout actorResponseWaitTime,
            final ServiceGroupIdentifier serviceGroupIdent, final NetconfTopologySetup setup,
            final CredentialProvider credentialProvider, final SslHandlerFactoryProvider sslHandlerFactoryProvider) {
        this.serviceGroupIdent = requireNonNull(serviceGroupIdent);
        remoteDeviceId = NetconfNodeUtils.toRemoteDeviceId(setup.getNode().getNodeId(),
                setup.getNode().augmentation(NetconfNode.class));

        topologySingleton = new NetconfTopologySingletonImpl(topologyId, clientDispatcher,
                eventExecutor, keepaliveExecutor, processingExecutor, schemaManager, dataBroker, mountPointService,
                encryptionService, deviceActionFactory, baseSchemas, remoteDeviceId, setup, actorResponseWaitTime,
                credentialProvider, sslHandlerFactoryProvider);
    }

    @VisibleForTesting
    protected NetconfTopologySingletonImpl getTopologySingleton() {
        // FIXME we have to access topology singleton via this method because of mocking in MountPointEndToEndTest
        return topologySingleton;
    }

    @Override
    public void instantiateServiceInstance() {
        LOG.info("Leader was selected: {}", remoteDeviceId.host().getIpAddress());
        if (closed) {
            LOG.warn("Instance is already closed.");
            return;
        }
        isMaster = true;
        getTopologySingleton().becomeTopologyLeader();
    }

    // called when master is down/changed to slave
    @Override
    public ListenableFuture<?> closeServiceInstance() {
        // this method is also called when topology data are deleted
        LOG.info("Follower was selected: {}", remoteDeviceId.host().getIpAddress());
        if (closed) {
            LOG.warn("Instance is already closed.");
            return FluentFutures.immediateNullFluentFuture();
        }
        getTopologySingleton().becomeTopologyFollower();
        return FluentFutures.immediateNullFluentFuture();
    }

    void refresh(final @NonNull NetconfTopologySetup setup) {
        final var node = requireNonNull(setup).getNode();
        remoteDeviceId = NetconfNodeUtils.toRemoteDeviceId(node.getNodeId(), node.augmentation(NetconfNode.class));

        if (isMaster) {
            getTopologySingleton().dropNode(setup.getNode().getNodeId());
            getTopologySingleton().refreshSetupConnection(setup, remoteDeviceId);
        } else {
            getTopologySingleton().refreshDevice(setup, remoteDeviceId);
        }
    }

    @Override
    public ServiceGroupIdentifier getIdentifier() {
        return serviceGroupIdent;
    }

    @Override
    public void close() {
        if (closed) {
            LOG.warn("Instance is already closed.");
            return;
        }
        getTopologySingleton().close();
        closed = true;
    }
}
