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
import io.netty.util.concurrent.EventExecutor;
import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.singleton.common.api.ClusterSingletonServiceProvider;
import org.opendaylight.netconf.client.NetconfClientDispatcher;
import org.opendaylight.netconf.client.mdsal.NetconfDevice;
import org.opendaylight.netconf.client.mdsal.api.BaseNetconfSchemas;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public class NetconfTopologySetup {
    private final ClusterSingletonServiceProvider clusterSingletonServiceProvider;
    private final DataBroker dataBroker;
    private final InstanceIdentifier<Node> instanceIdentifier;
    private final Node node;
    private final ScheduledExecutorService keepaliveExecutor;
    private final Executor processingExecutor;
    private final ActorSystem actorSystem;
    private final EventExecutor eventExecutor;
    private final NetconfClientDispatcher netconfClientDispatcher;
    private final String topologyId;
    private final NetconfDevice.SchemaResourcesDTO schemaResourceDTO;
    private final Duration idleTimeout;
    private final BaseNetconfSchemas baseSchemas;

    NetconfTopologySetup(final NetconfTopologySetupBuilder builder) {
        clusterSingletonServiceProvider = builder.getClusterSingletonServiceProvider();
        dataBroker = builder.getDataBroker();
        instanceIdentifier = builder.getInstanceIdentifier();
        node = builder.getNode();
        keepaliveExecutor = builder.getKeepaliveExecutor();
        processingExecutor = builder.getProcessingExecutor();
        actorSystem = builder.getActorSystem();
        eventExecutor = builder.getEventExecutor();
        netconfClientDispatcher = builder.getNetconfClientDispatcher();
        topologyId = builder.getTopologyId();
        schemaResourceDTO = builder.getSchemaResourceDTO();
        idleTimeout = builder.getIdleTimeout();
        baseSchemas = builder.getBaseSchemas();
    }

    public ClusterSingletonServiceProvider getClusterSingletonServiceProvider() {
        return clusterSingletonServiceProvider;
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

    public Executor getProcessingExecutor() {
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

    public BaseNetconfSchemas getBaseSchemas() {
        return baseSchemas;
    }

    public static class NetconfTopologySetupBuilder {
        private ClusterSingletonServiceProvider clusterSingletonServiceProvider;
        private DataBroker dataBroker;
        private InstanceIdentifier<Node> instanceIdentifier;
        private Node node;
        private ScheduledExecutorService keepaliveExecutor;
        private Executor processingExecutor;
        private ActorSystem actorSystem;
        private EventExecutor eventExecutor;
        private String topologyId;
        private NetconfClientDispatcher netconfClientDispatcher;
        private NetconfDevice.SchemaResourcesDTO schemaResourceDTO;
        private Duration idleTimeout;
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

        Executor getProcessingExecutor() {
            return processingExecutor;
        }

        public NetconfTopologySetupBuilder setProcessingExecutor(final Executor processingExecutor) {
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
            netconfClientDispatcher = clientDispatcher;
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

        public static NetconfTopologySetupBuilder create() {
            return new NetconfTopologySetupBuilder();
        }
    }
}