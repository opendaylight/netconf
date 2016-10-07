/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.singleton.impl;


import akka.actor.ActorSystem;
import com.google.common.base.Preconditions;
import io.netty.util.concurrent.EventExecutor;
import org.opendaylight.controller.cluster.ActorSystemProvider;
import org.opendaylight.controller.config.threadpool.ScheduledThreadPool;
import org.opendaylight.controller.config.threadpool.ThreadPool;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker;
import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;
import org.opendaylight.controller.sal.core.api.Broker;
import org.opendaylight.mdsal.singleton.common.api.ClusterSingletonServiceProvider;
import org.opendaylight.netconf.client.NetconfClientDispatcher;
import org.opendaylight.netconf.topology.singleton.api.NetconfTopologyServicesProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetconfTopologyProvider implements NetconfTopologyServicesProvider {

    private static final Logger LOG = LoggerFactory.getLogger(NetconfTopologyProvider.class);

    private final DataBroker dataBroker;
    private final RpcProviderRegistry rpcProviderRegistry;
    private final ClusterSingletonServiceProvider clusterSingletonServiceProvider;
    private final BindingAwareBroker bindingAwareBroker;
    private final ScheduledThreadPool keepaliveExecutor;
    private final ThreadPool processingExecutor;
    private final Broker domBroker;
    private final ActorSystem actorSystem;
    private final EventExecutor eventExecutor;
    private final NetconfClientDispatcher clientDispatcher;
    private final String topologyId;

    private NetconfTopologyManager netconfTopologyManager;

    public NetconfTopologyProvider(final DataBroker dataBroker, final RpcProviderRegistry rpcProviderRegistry,
                                   final ClusterSingletonServiceProvider clusterSingletonServiceProvider,
                                   final BindingAwareBroker bindingAwareBroker,
                                   final ScheduledThreadPool keepaliveExecutor, final ThreadPool processingExecutor,
                                   final Broker domBroker, final ActorSystemProvider actorSystemProvider,
                                   final EventExecutor eventExecutor, final NetconfClientDispatcher clientDispatcher,
                                   final String topologyId) {

        this.dataBroker = Preconditions.checkNotNull(dataBroker);
        this.rpcProviderRegistry = Preconditions.checkNotNull(rpcProviderRegistry);
        this.clusterSingletonServiceProvider = Preconditions.checkNotNull(clusterSingletonServiceProvider);
        this.bindingAwareBroker =  Preconditions.checkNotNull(bindingAwareBroker);
        this.keepaliveExecutor =  Preconditions.checkNotNull(keepaliveExecutor);
        this.processingExecutor =  Preconditions.checkNotNull(processingExecutor);
        this.domBroker =  Preconditions.checkNotNull(domBroker);
        this.actorSystem =  Preconditions.checkNotNull(actorSystemProvider).getActorSystem();
        this.eventExecutor =  Preconditions.checkNotNull(eventExecutor);
        this.clientDispatcher =  Preconditions.checkNotNull(clientDispatcher);
        this.topologyId =  Preconditions.checkNotNull(topologyId);
    }

    public void init() {
        LOG.info("NetconfTopologyProvider Session Initiated");
        netconfTopologyManager = new NetconfTopologyManager(dataBroker, rpcProviderRegistry,
                clusterSingletonServiceProvider, bindingAwareBroker, keepaliveExecutor,
                processingExecutor, domBroker, actorSystem, eventExecutor, clientDispatcher,
                topologyId);
    }

    public void close() {
        LOG.info("NetconfTopologyProvider Closed");
        try {
            netconfTopologyManager.close();
        } catch (final Exception e) {
            LOG.error("Unexpected error by closing NetconfDeviceManager", e);
        }
    }

}
