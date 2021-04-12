/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl;

import akka.util.Timeout;
import com.google.common.util.concurrent.ListenableFuture;
import io.netty.util.concurrent.EventExecutor;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.controller.config.threadpool.ScheduledThreadPool;
import org.opendaylight.controller.config.threadpool.ThreadPool;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.singleton.common.api.ClusterSingletonService;
import org.opendaylight.mdsal.singleton.common.api.ServiceGroupIdentifier;
import org.opendaylight.netconf.client.NetconfClientDispatcher;
import org.opendaylight.netconf.sal.connect.api.DeviceActionFactory;
import org.opendaylight.netconf.sal.connect.api.SchemaResourceManager;
import org.opendaylight.netconf.sal.connect.netconf.schema.mapping.BaseNetconfSchemas;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologySetup;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologyUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.opendaylight.yangtools.util.concurrent.FluentFutures;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class NetconfTopologyContext implements ClusterSingletonService, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfTopologyContext.class);

    // sometimes node after isolation is for short time again leader (we have two leaders!) and then
    // it is changed to follower without any notification about it to current real leader
    // we decided to not modify operational datastore in first 15 seconds to prevent unwanted modifications
    private static final int LEADER_DELAY_SEC = 15;

    private final ServiceGroupIdentifier serviceGroupIdent;
    private final RemoteDeviceId remoteDeviceId;
    private final NetconfTopologySingletonImpl topologySingleton;
    private final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
    private final AtomicBoolean leader = new AtomicBoolean(false);

    private ScheduledFuture<?> scheduledFuture;

    private volatile boolean closed;

    // we consider every node that is once set to leader to be potentially isolated leader in the future
    private volatile boolean isolatedLeader;

    NetconfTopologyContext(final String topologyId, final NetconfClientDispatcher clientDispatcher,
            final EventExecutor eventExecutor, final ScheduledThreadPool keepaliveExecutor,
            final ThreadPool processingExecutor, final SchemaResourceManager schemaManager,
            final DataBroker dataBroker, final DOMMountPointService mountPointService,
            final AAAEncryptionService encryptionService, final DeviceActionFactory deviceActionFactory,
            final BaseNetconfSchemas baseSchemas, final Timeout actorResponseWaitTime,
            final ServiceGroupIdentifier serviceGroupIdent, final NetconfTopologySetup setup) {
        this.serviceGroupIdent = serviceGroupIdent;
        this.remoteDeviceId = NetconfTopologyUtils.createRemoteDeviceId(setup.getNode().getNodeId(),
                setup.getNode().augmentation(NetconfNode.class));
        this.topologySingleton = new NetconfTopologySingletonImpl(topologyId, clientDispatcher,
                eventExecutor, keepaliveExecutor,
                processingExecutor, schemaManager,
                dataBroker, mountPointService,
                encryptionService, deviceActionFactory,
                baseSchemas, remoteDeviceId, setup, actorResponseWaitTime);
        executor.setRemoveOnCancelPolicy(true);
    }

    @Override
    public synchronized void instantiateServiceInstance() {
        LOG.info("Leader was selected: {}", remoteDeviceId.getHost().getIpAddress());
        if (closed) {
            LOG.warn("Instance is already closed.");
            return;
        }
        if (!leader.compareAndSet(false, true)) {
            LOG.warn("Instance is already leader.");
            return;
        }

        scheduledFuture = executor.schedule(topologySingleton::becomeTopologyLeader,
                isolatedLeader ? LEADER_DELAY_SEC : 0, TimeUnit.SECONDS);

        isolatedLeader = true;
    }

    @Override
    public synchronized ListenableFuture<?> closeServiceInstance() {
        // this method is also called when topology data are deleted
        LOG.info("Follower was selected: {}", remoteDeviceId.getHost().getIpAddress());
        if (closed) {
            LOG.warn("Instance is already closed.");
            return FluentFutures.immediateNullFluentFuture();
        }
        if (!leader.compareAndSet(true, false)) {
            LOG.warn("Instance is already follower.");
            return FluentFutures.immediateNullFluentFuture();
        }

        // do not change operational DS if this leader fails to hold its leadership
        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
        }

        topologySingleton.becomeTopologyFollower();
        return FluentFutures.immediateNullFluentFuture();
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
        executor.shutdown();
        topologySingleton.close();
        closed = true;
    }
}