/*
 * Copyright (c) 2017 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.singleton.impl.utils;

import akka.actor.ActorSystem;
import io.netty.util.concurrent.EventExecutor;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.controller.config.threadpool.ScheduledThreadPool;
import org.opendaylight.controller.config.threadpool.ThreadPool;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;
import org.opendaylight.mdsal.singleton.common.api.ClusterSingletonServiceProvider;
import org.opendaylight.netconf.client.NetconfClientDispatcher;
import org.opendaylight.netconf.sal.connect.netconf.NetconfDevice;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import scala.concurrent.duration.Duration;

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
    private final String topologyId;
    private final NetconfDevice.SchemaResourcesDTO schemaResourceDTO;
    private final Duration idleTimeout;
    private final String privateKeyPath;
    private final String privateKeyPassphrase;
    private final AAAEncryptionService encryptionService;

    NetconfTopologySetup(final NetconfTopologySetupBuilder builder) {
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
        this.topologyId = builder.getTopologyId();
        this.schemaResourceDTO = builder.getSchemaResourceDTO();
        this.idleTimeout = builder.getIdleTimeout();
        this.privateKeyPath = builder.getPrivateKeyPath();
        this.privateKeyPassphrase = builder.getPrivateKeyPassphrase();
        this.encryptionService = builder.getEncryptionService();
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

    public NetconfDevice.SchemaResourcesDTO getSchemaResourcesDTO() {
        return schemaResourceDTO;
    }

    public Duration getIdleTimeout() {
        return idleTimeout;
    }

    public String getPrivateKeyPath() {
        return privateKeyPath;
    }

    public String getPrivateKeyPassphrase() {
        return privateKeyPassphrase;
    }

    public AAAEncryptionService getEncryptionService() {
        return encryptionService;
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
        private NetconfDevice.SchemaResourcesDTO schemaResourceDTO;
        private Duration idleTimeout;
        private String privateKeyPath;
        private String privateKeyPassphrase;
        private AAAEncryptionService encryptionService;

        public NetconfTopologySetupBuilder() {
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

        public NetconfTopologySetupBuilder setKeepaliveExecutor(final ScheduledThreadPool keepaliveExecutor) {
            this.keepaliveExecutor = keepaliveExecutor;
            return this;
        }

        private ThreadPool getProcessingExecutor() {
            return processingExecutor;
        }

        public NetconfTopologySetupBuilder setProcessingExecutor(final ThreadPool processingExecutor) {
            this.processingExecutor = processingExecutor;
            return this;
        }

        private ActorSystem getActorSystem() {
            return actorSystem;
        }

        public NetconfTopologySetupBuilder setActorSystem(final ActorSystem actorSystem) {
            this.actorSystem = actorSystem;
            return this;
        }

        private EventExecutor getEventExecutor() {
            return eventExecutor;
        }

        public NetconfTopologySetupBuilder setEventExecutor(final EventExecutor eventExecutor) {
            this.eventExecutor = eventExecutor;
            return this;
        }

        private String getTopologyId() {
            return topologyId;
        }

        public NetconfTopologySetupBuilder setTopologyId(final String topologyId) {
            this.topologyId = topologyId;
            return this;
        }

        private NetconfClientDispatcher getNetconfClientDispatcher() {
            return netconfClientDispatcher;
        }

        public NetconfTopologySetupBuilder setNetconfClientDispatcher(final NetconfClientDispatcher clientDispatcher) {
            this.netconfClientDispatcher = clientDispatcher;
            return this;
        }

        public NetconfTopologySetupBuilder setSchemaResourceDTO(
                final NetconfDevice.SchemaResourcesDTO schemaResourceDTO) {
            this.schemaResourceDTO = schemaResourceDTO;
            return this;
        }

        private NetconfDevice.SchemaResourcesDTO getSchemaResourceDTO() {
            return schemaResourceDTO;
        }

        public NetconfTopologySetupBuilder setIdleTimeout(final Duration idleTimeout) {
            this.idleTimeout = idleTimeout;
            return this;
        }

        private Duration getIdleTimeout() {
            return idleTimeout;
        }

        public NetconfTopologySetupBuilder setPrivateKeyPath(String privateKeyPath) {
            this.privateKeyPath = privateKeyPath;
            return this;
        }

        public String getPrivateKeyPath() {
            return this.privateKeyPath;
        }

        public NetconfTopologySetupBuilder setPrivateKeyPassphrase(String privateKeyPassphrase) {
            this.privateKeyPassphrase = privateKeyPassphrase;
            return this;
        }

        public String getPrivateKeyPassphrase() {
            return this.privateKeyPassphrase;
        }

        private AAAEncryptionService getEncryptionService() {
            return this.encryptionService;
        }

        public NetconfTopologySetupBuilder setEncryptionService(final AAAEncryptionService encryptionService) {
            this.encryptionService = encryptionService;
            return this;
        }

        public static NetconfTopologySetupBuilder create() {
            return new NetconfTopologySetupBuilder();
        }
    }


}
