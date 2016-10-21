/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.singleton.impl.utils;

import akka.actor.ActorSystem;
import io.netty.util.concurrent.EventExecutor;
import org.opendaylight.controller.config.threadpool.ScheduledThreadPool;
import org.opendaylight.controller.config.threadpool.ThreadPool;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPointService;
import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;
import org.opendaylight.mdsal.singleton.common.api.ClusterSingletonServiceProvider;
import org.opendaylight.netconf.client.NetconfClientDispatcher;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public class NetconfTopologySetup {

    private final ClusterSingletonServiceProvider clusterSingletonServiceProvider;
    private final RpcProviderRegistry rpcProviderRegistry;
    private final DataBroker dataBroker;
    private final InstanceIdentifier<Node> instanceIdentifier;
    private final Node node;
    private final ScheduledThreadPool keepaliveExecutor;
    private final ThreadPool processingExecutor;
    private final ActorSystem actorSystem;
    private final EventExecutor eventExecutor;
    private final NetconfClientDispatcher netconfClientDispatcher;
    private final DOMMountPointService domMountPointService;
    private final String topologyId;
    private NetconfTopologySetup(final NetconfTopologySetupBuilder builder) {
        this.clusterSingletonServiceProvider = builder.getClusterSingletonServiceProvider();
        this.rpcProviderRegistry = builder.getRpcProviderRegistry();
        this.dataBroker = builder.getDataBroker();
        this.instanceIdentifier = builder.getInstanceIdentifier();
        this.node = builder.getNode();
        this.keepaliveExecutor = builder.getKeepaliveExecutor();
        this.processingExecutor = builder.getProcessingExecutor();
        this.actorSystem = builder.getActorSystem();
        this.eventExecutor = builder.getEventExecutor();
        this.netconfClientDispatcher = builder.getNetconfClientDispatcher();
        this.domMountPointService = builder.getDomMountPointService();
        this.topologyId = builder.getTopologyId();
    }

    public ClusterSingletonServiceProvider getClusterSingletonServiceProvider() {
        return clusterSingletonServiceProvider;
    }

    public RpcProviderRegistry getRpcProviderRegistry() {
        return rpcProviderRegistry;
    }

    public DataBroker getDataBroker() {
        return dataBroker;
    }

    public InstanceIdentifier<Node> getInstanceIdentifier() {
        return instanceIdentifier;
    }

    public Node getNode() {
        return node;
    }

    public ThreadPool getProcessingExecutor() {
        return processingExecutor;
    }

    public ScheduledThreadPool getKeepaliveExecutor() {
        return keepaliveExecutor;
    }

    public ActorSystem getActorSystem() {
        return actorSystem;
    }

    public EventExecutor getEventExecutor() {
        return eventExecutor;
    }

    public String getTopologyId() {
        return topologyId;
    }

    public NetconfClientDispatcher getNetconfClientDispatcher() {
        return netconfClientDispatcher;
    }

    public DOMMountPointService getDOMMountPointService() {
        return domMountPointService;
    }

    public static class NetconfTopologySetupBuilder {

        private ClusterSingletonServiceProvider clusterSingletonServiceProvider;
        private RpcProviderRegistry rpcProviderRegistry;
        private DataBroker dataBroker;
        private InstanceIdentifier<Node> instanceIdentifier;
        private Node node;
        private ScheduledThreadPool keepaliveExecutor;
        private ThreadPool processingExecutor;
        private ActorSystem actorSystem;
        private EventExecutor eventExecutor;
        private String topologyId;
        private NetconfClientDispatcher netconfClientDispatcher;
        private DOMMountPointService domMountPointService;

        public NetconfTopologySetupBuilder(){
        }

        private ClusterSingletonServiceProvider getClusterSingletonServiceProvider() {
            return clusterSingletonServiceProvider;
        }

        public NetconfTopologySetupBuilder setClusterSingletonServiceProvider(
                final ClusterSingletonServiceProvider clusterSingletonServiceProvider) {
            this.clusterSingletonServiceProvider = clusterSingletonServiceProvider;
            return this;
        }

        private RpcProviderRegistry getRpcProviderRegistry() {
            return rpcProviderRegistry;
        }

        public NetconfTopologySetupBuilder setRpcProviderRegistry(final RpcProviderRegistry rpcProviderRegistry) {
            this.rpcProviderRegistry = rpcProviderRegistry;
            return this;
        }

        private DataBroker getDataBroker() {
            return dataBroker;
        }

        public NetconfTopologySetupBuilder setDataBroker(final DataBroker dataBroker) {
            this.dataBroker = dataBroker;
            return this;
        }

        private InstanceIdentifier<Node> getInstanceIdentifier() {
            return instanceIdentifier;
        }

        private DOMMountPointService getDomMountPointService() {
            return domMountPointService;
        }

        public NetconfTopologySetupBuilder setInstanceIdentifier(final InstanceIdentifier<Node> instanceIdentifier) {
            this.instanceIdentifier = instanceIdentifier;
            return this;
        }

        public Node getNode() {
            return node;
        }

        public NetconfTopologySetupBuilder setNode(final Node node) {
            this.node = node;
            return this;
        }

        public NetconfTopologySetup build() {
            return new NetconfTopologySetup(this);
        }

        private ScheduledThreadPool getKeepaliveExecutor() {
            return keepaliveExecutor;
        }

        public NetconfTopologySetupBuilder setKeepaliveExecutor(ScheduledThreadPool keepaliveExecutor) {
            this.keepaliveExecutor = keepaliveExecutor;
            return this;
        }

        private ThreadPool getProcessingExecutor() {
            return processingExecutor;
        }

        public NetconfTopologySetupBuilder setProcessingExecutor(ThreadPool processingExecutor) {
            this.processingExecutor = processingExecutor;
            return this;
        }

        private ActorSystem getActorSystem() {
            return actorSystem;
        }

        public NetconfTopologySetupBuilder setActorSystem(ActorSystem actorSystem) {
            this.actorSystem = actorSystem;
            return this;
        }

        private EventExecutor getEventExecutor() {
            return eventExecutor;
        }

        public NetconfTopologySetupBuilder setEventExecutor(EventExecutor eventExecutor) {
            this.eventExecutor = eventExecutor;
            return this;
        }

        private String getTopologyId() {
            return topologyId;
        }

        public NetconfTopologySetupBuilder setTopologyId(String topologyId) {
            this.topologyId = topologyId;
            return this;
        }

        private NetconfClientDispatcher getNetconfClientDispatcher() {
            return netconfClientDispatcher;
        }

        public NetconfTopologySetupBuilder setNetconfClientDispatcher(NetconfClientDispatcher clientDispatcher) {
            this.netconfClientDispatcher = clientDispatcher;
            return this;
        }

        public NetconfTopologySetupBuilder setDOMMountPointService(final DOMMountPointService domMountPointService) {
            this.domMountPointService = domMountPointService;
            return this;
        }

        public static NetconfTopologySetupBuilder create() {
            return new NetconfTopologySetupBuilder();
        }
    }


}
