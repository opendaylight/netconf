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
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker;
import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;
import org.opendaylight.controller.sal.core.api.Broker;
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
    private final BindingAwareBroker bindingAwareBroker;
    private final ScheduledThreadPool keepaliveExecutor;
    private final ThreadPool processingExecutor;
    private final Broker domBroker;
    private final ActorSystem actorSystem;
    private final EventExecutor eventExecutor;
    private final NetconfClientDispatcher netconfClientDispatcher;
    private final String topologyId;
    private NetconfTopologySetup(final NetconfTopologySetupBuilder builder) {
        this.clusterSingletonServiceProvider = builder.getClusterSingletonServiceProvider();
        this.rpcProviderRegistry = builder.getRpcProviderRegistry();
        this.dataBroker = builder.getDataBroker();
        this.instanceIdentifier = builder.getInstanceIdentifier();
        this.node = builder.getNode();
        this.bindingAwareBroker = builder.getBindingAwareBroker();
        this.keepaliveExecutor = builder.getKeepaliveExecutor();
        this.processingExecutor = builder.getProcessingExecutor();
        this.domBroker = builder.getDomBroker();
        this.actorSystem = builder.getActorSystem();
        this.eventExecutor = builder.getEventExecutor();
        this.netconfClientDispatcher = builder.getNetconfClientDispatcher();
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

    public BindingAwareBroker getBindingAwareBroker() {
        return bindingAwareBroker;
    }

    public ThreadPool getProcessingExecutor() {
        return processingExecutor;
    }

    public ScheduledThreadPool getKeepaliveExecutor() {
        return keepaliveExecutor;
    }

    public Broker getDomBroker() {
        return domBroker;
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

    public static class NetconfTopologySetupBuilder {

        private ClusterSingletonServiceProvider clusterSingletonServiceProvider;
        private RpcProviderRegistry rpcProviderRegistry;
        private DataBroker dataBroker;
        private InstanceIdentifier<Node> instanceIdentifier;
        private Node node;
        private BindingAwareBroker bindingAwareBroker;
        private ScheduledThreadPool keepaliveExecutor;
        private ThreadPool processingExecutor;
        private Broker domBroker;
        private ActorSystem actorSystem;
        private EventExecutor eventExecutor;
        private String topologyId;
        private NetconfClientDispatcher netconfClientDispatcher;

        private NetconfTopologySetupBuilder(){
        }

        private ClusterSingletonServiceProvider getClusterSingletonServiceProvider() {
            return clusterSingletonServiceProvider;
        }

        public void setClusterSingletonServiceProvider(
                final ClusterSingletonServiceProvider clusterSingletonServiceProvider) {
            this.clusterSingletonServiceProvider = clusterSingletonServiceProvider;
        }

        private RpcProviderRegistry getRpcProviderRegistry() {
            return rpcProviderRegistry;
        }

        public void setRpcProviderRegistry(final RpcProviderRegistry rpcProviderRegistry) {
            this.rpcProviderRegistry = rpcProviderRegistry;
        }

        private DataBroker getDataBroker() {
            return dataBroker;
        }

        public void setDataBroker(final DataBroker dataBroker) {
            this.dataBroker = dataBroker;
        }

        private InstanceIdentifier<Node> getInstanceIdentifier() {
            return instanceIdentifier;
        }

        public void setInstanceIdentifier(final InstanceIdentifier<Node> instanceIdentifier) {
            this.instanceIdentifier = instanceIdentifier;
        }

        public Node getNode() {
            return node;
        }

        public void setNode(final Node node) {
            this.node = node;
        }

        public NetconfTopologySetup build() {
            return new NetconfTopologySetup(this);
        }

        private BindingAwareBroker getBindingAwareBroker() {
            return bindingAwareBroker;
        }

        public void setBindingAwareBroker(BindingAwareBroker bindingAwareBroker) {
            this.bindingAwareBroker = bindingAwareBroker;
        }

        private ScheduledThreadPool getKeepaliveExecutor() {
            return keepaliveExecutor;
        }

        public void setKeepaliveExecutor(ScheduledThreadPool keepaliveExecutor) {
            this.keepaliveExecutor = keepaliveExecutor;
        }

        private ThreadPool getProcessingExecutor() {
            return processingExecutor;
        }

        public void setProcessingExecutor(ThreadPool processingExecutor) {
            this.processingExecutor = processingExecutor;
        }

        private Broker getDomBroker() {
            return domBroker;
        }

        public void setDomBroker(Broker domBroker) {
            this.domBroker = domBroker;
        }

        private ActorSystem getActorSystem() {
            return actorSystem;
        }

        public void setActorSystem(ActorSystem actorSystem) {
            this.actorSystem = actorSystem;
        }

        private EventExecutor getEventExecutor() {
            return eventExecutor;
        }

        public void setEventExecutor(EventExecutor eventExecutor) {
            this.eventExecutor = eventExecutor;
        }

        private String getTopologyId() {
            return topologyId;
        }

        public void setTopologyId(String topologyId) {
            this.topologyId = topologyId;
        }

        private NetconfClientDispatcher getNetconfClientDispatcher() {
            return netconfClientDispatcher;
        }

        public void setNetconfClientDispatcher(NetconfClientDispatcher clientDispatcher) {
            this.netconfClientDispatcher = clientDispatcher;
        }

        public static NetconfTopologySetupBuilder create() {
            return new NetconfTopologySetupBuilder();
        }
    }


}
