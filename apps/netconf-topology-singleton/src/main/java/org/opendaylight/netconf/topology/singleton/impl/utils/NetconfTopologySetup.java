/*
 * Copyright (c) 2017 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl.utils;

import static java.util.Objects.requireNonNull;

import akka.actor.ActorSystem;
import com.google.common.util.concurrent.ListeningExecutorService;
import io.netty.util.concurrent.EventExecutor;
import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.dom.api.DOMActionProviderService;
import org.opendaylight.mdsal.dom.api.DOMRpcProviderService;
import org.opendaylight.mdsal.singleton.common.api.ClusterSingletonServiceProvider;
import org.opendaylight.netconf.client.NetconfClientDispatcher;
import org.opendaylight.netconf.sal.connect.netconf.NetconfDevice;
import org.opendaylight.netconf.sal.connect.netconf.schema.mapping.BaseNetconfSchemas;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public class NetconfTopologySetup {

    private final ClusterSingletonServiceProvider clusterSingletonServiceProvider;
    private final DOMRpcProviderService rpcProviderRegistry;
    private final DOMActionProviderService actionProviderRegistry;
    private final DataBroker dataBroker;
    private final InstanceIdentifier<Node> instanceIdentifier;
    private final Node node;
    private final ScheduledExecutorService keepaliveExecutor;
    private final ListeningExecutorService processingExecutor;
    private final ActorSystem actorSystem;
    private final EventExecutor eventExecutor;
    private final NetconfClientDispatcher netconfClientDispatcher;
    private final String topologyId;
    private final NetconfDevice.SchemaResourcesDTO schemaResourceDTO;
    private final Duration idleTimeout;
    private final String privateKeyPath;
    private final String privateKeyPassphrase;
    private final AAAEncryptionService encryptionService;
    private final BaseNetconfSchemas baseSchemas;

    NetconfTopologySetup(final NetconfTopologySetupBuilder builder) {
        this.clusterSingletonServiceProvider = builder.getClusterSingletonServiceProvider();
        this.rpcProviderRegistry = builder.getRpcProviderRegistry();
        this.actionProviderRegistry = builder.getActionProviderRegistry();
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
        this.baseSchemas = builder.getBaseSchemas();
    }

    public ClusterSingletonServiceProvider getClusterSingletonServiceProvider() {
        return clusterSingletonServiceProvider;
    }

    public DOMRpcProviderService getRpcProviderRegistry() {
        return rpcProviderRegistry;
    }

    public DOMActionProviderService getActionProviderRegistry() {
        return actionProviderRegistry;
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

    public ListeningExecutorService getProcessingExecutor() {
        return processingExecutor;
    }

    public ScheduledExecutorService getKeepaliveExecutor() {
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

    public BaseNetconfSchemas getBaseSchemas() {
        return baseSchemas;
    }

    public static class NetconfTopologySetupBuilder {
        private ClusterSingletonServiceProvider clusterSingletonServiceProvider;
        private DOMRpcProviderService rpcProviderRegistry;
        private DOMActionProviderService actionProviderRegistry;
        private DataBroker dataBroker;
        private InstanceIdentifier<Node> instanceIdentifier;
        private Node node;
        private ScheduledExecutorService keepaliveExecutor;
        private ListeningExecutorService processingExecutor;
        private ActorSystem actorSystem;
        private EventExecutor eventExecutor;
        private String topologyId;
        private NetconfClientDispatcher netconfClientDispatcher;
        private NetconfDevice.SchemaResourcesDTO schemaResourceDTO;
        private Duration idleTimeout;
        private String privateKeyPath;
        private String privateKeyPassphrase;
        private AAAEncryptionService encryptionService;
        private BaseNetconfSchemas baseSchemas;

        public NetconfTopologySetupBuilder() {

        }

        BaseNetconfSchemas getBaseSchemas() {
            return requireNonNull(baseSchemas, "BaseSchemas not initialized");
        }

        public NetconfTopologySetupBuilder setBaseSchemas(final BaseNetconfSchemas baseSchemas) {
            this.baseSchemas = requireNonNull(baseSchemas);
            return this;
        }

        ClusterSingletonServiceProvider getClusterSingletonServiceProvider() {
            return clusterSingletonServiceProvider;
        }

        public NetconfTopologySetupBuilder setClusterSingletonServiceProvider(
                final ClusterSingletonServiceProvider clusterSingletonServiceProvider) {
            this.clusterSingletonServiceProvider = clusterSingletonServiceProvider;
            return this;
        }

        DOMRpcProviderService getRpcProviderRegistry() {
            return rpcProviderRegistry;
        }

        public NetconfTopologySetupBuilder setRpcProviderRegistry(final DOMRpcProviderService rpcProviderRegistry) {
            this.rpcProviderRegistry = rpcProviderRegistry;
            return this;
        }

        DOMActionProviderService getActionProviderRegistry() {
            return actionProviderRegistry;
        }

        public NetconfTopologySetupBuilder setActionProviderRegistry(
            final DOMActionProviderService actionProviderRegistry) {
            this.actionProviderRegistry = actionProviderRegistry;
            return this;
        }

        DataBroker getDataBroker() {
            return dataBroker;
        }

        public NetconfTopologySetupBuilder setDataBroker(final DataBroker dataBroker) {
            this.dataBroker = dataBroker;
            return this;
        }

        InstanceIdentifier<Node> getInstanceIdentifier() {
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

        ScheduledExecutorService getKeepaliveExecutor() {
            return keepaliveExecutor;
        }

        public NetconfTopologySetupBuilder setKeepaliveExecutor(final ScheduledExecutorService keepaliveExecutor) {
            this.keepaliveExecutor = keepaliveExecutor;
            return this;
        }

        ListeningExecutorService getProcessingExecutor() {
            return processingExecutor;
        }

        public NetconfTopologySetupBuilder setProcessingExecutor(final ListeningExecutorService processingExecutor) {
            this.processingExecutor = processingExecutor;
            return this;
        }

        ActorSystem getActorSystem() {
            return actorSystem;
        }

        public NetconfTopologySetupBuilder setActorSystem(final ActorSystem actorSystem) {
            this.actorSystem = actorSystem;
            return this;
        }

        EventExecutor getEventExecutor() {
            return eventExecutor;
        }

        public NetconfTopologySetupBuilder setEventExecutor(final EventExecutor eventExecutor) {
            this.eventExecutor = eventExecutor;
            return this;
        }

        String getTopologyId() {
            return topologyId;
        }

        public NetconfTopologySetupBuilder setTopologyId(final String topologyId) {
            this.topologyId = topologyId;
            return this;
        }

        NetconfClientDispatcher getNetconfClientDispatcher() {
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

        NetconfDevice.SchemaResourcesDTO getSchemaResourceDTO() {
            return schemaResourceDTO;
        }

        public NetconfTopologySetupBuilder setIdleTimeout(final Duration idleTimeout) {
            this.idleTimeout = idleTimeout;
            return this;
        }

        Duration getIdleTimeout() {
            return idleTimeout;
        }

        public NetconfTopologySetupBuilder setPrivateKeyPath(final String privateKeyPath) {
            this.privateKeyPath = privateKeyPath;
            return this;
        }

        public String getPrivateKeyPath() {
            return this.privateKeyPath;
        }

        public NetconfTopologySetupBuilder setPrivateKeyPassphrase(final String privateKeyPassphrase) {
            this.privateKeyPassphrase = privateKeyPassphrase;
            return this;
        }

        public String getPrivateKeyPassphrase() {
            return this.privateKeyPassphrase;
        }

        AAAEncryptionService getEncryptionService() {
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