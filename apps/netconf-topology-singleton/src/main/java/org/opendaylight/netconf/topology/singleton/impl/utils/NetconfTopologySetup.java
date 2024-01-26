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
import io.netty.util.Timer;
import java.time.Duration;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.singleton.common.api.ClusterSingletonServiceProvider;
import org.opendaylight.netconf.client.NetconfClientFactory;
import org.opendaylight.netconf.client.mdsal.NetconfDevice;
import org.opendaylight.netconf.client.mdsal.api.BaseNetconfSchemas;
import org.opendaylight.netconf.topology.spi.NetconfTopologySchemaAssembler;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public final class NetconfTopologySetup {
    private final ClusterSingletonServiceProvider clusterSingletonServiceProvider;
    private final DataBroker dataBroker;
    private final InstanceIdentifier<Node> instanceIdentifier;
    private final Node node;
    private final Timer timer;
    private final NetconfTopologySchemaAssembler schemaAssembler;
    private final ActorSystem actorSystem;
    private final NetconfClientFactory netconfClientFactory;
    private final String topologyId;
    private final NetconfDevice.SchemaResourcesDTO schemaResourceDTO;
    private final Duration idleTimeout;
    private final BaseNetconfSchemas baseSchemas;

    private NetconfTopologySetup(final Builder builder) {
        clusterSingletonServiceProvider = builder.getClusterSingletonServiceProvider();
        dataBroker = builder.getDataBroker();
        instanceIdentifier = builder.getInstanceIdentifier();
        node = builder.getNode();
        timer = builder.getTimer();
        schemaAssembler = builder.getSchemaAssembler();
        actorSystem = builder.getActorSystem();
        netconfClientFactory = builder.getNetconfClientFactory();
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

    public NetconfTopologySchemaAssembler getSchemaAssembler() {
        return schemaAssembler;
    }

    public Timer getTimer() {
        return timer;
    }

    public ActorSystem getActorSystem() {
        return actorSystem;
    }

    public String getTopologyId() {
        return topologyId;
    }

    public NetconfClientFactory getNetconfClientFactory() {
        return netconfClientFactory;
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

    public static @NonNull Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private ClusterSingletonServiceProvider clusterSingletonServiceProvider;
        private DataBroker dataBroker;
        private InstanceIdentifier<Node> instanceIdentifier;
        private Node node;
        private Timer timer;
        private NetconfTopologySchemaAssembler schemaAssembler;
        private ActorSystem actorSystem;
        private String topologyId;
        private NetconfClientFactory netconfClientFactory;
        private NetconfDevice.SchemaResourcesDTO schemaResourceDTO;
        private Duration idleTimeout;
        private BaseNetconfSchemas baseSchemas;

        private Builder() {
            // Hidden on purpose
        }

        BaseNetconfSchemas getBaseSchemas() {
            return requireNonNull(baseSchemas, "BaseSchemas not initialized");
        }

        public Builder setBaseSchemas(final BaseNetconfSchemas baseSchemas) {
            this.baseSchemas = requireNonNull(baseSchemas);
            return this;
        }

        ClusterSingletonServiceProvider getClusterSingletonServiceProvider() {
            return clusterSingletonServiceProvider;
        }

        public Builder setClusterSingletonServiceProvider(
                final ClusterSingletonServiceProvider clusterSingletonServiceProvider) {
            this.clusterSingletonServiceProvider = clusterSingletonServiceProvider;
            return this;
        }

        DataBroker getDataBroker() {
            return dataBroker;
        }

        public Builder setDataBroker(final DataBroker dataBroker) {
            this.dataBroker = dataBroker;
            return this;
        }

        InstanceIdentifier<Node> getInstanceIdentifier() {
            return instanceIdentifier;
        }

        public Builder setInstanceIdentifier(final InstanceIdentifier<Node> instanceIdentifier) {
            this.instanceIdentifier = instanceIdentifier;
            return this;
        }

        public Node getNode() {
            return node;
        }

        public Builder setNode(final Node node) {
            this.node = node;
            return this;
        }

        public NetconfTopologySetup build() {
            return new NetconfTopologySetup(this);
        }

        Timer getTimer() {
            return timer;
        }

        public Builder setTimer(final Timer timer) {
            this.timer = requireNonNull(timer);
            return this;
        }


        NetconfTopologySchemaAssembler getSchemaAssembler() {
            return schemaAssembler;
        }

        public Builder setSchemaAssembler(final NetconfTopologySchemaAssembler schemaAssembler) {
            this.schemaAssembler = schemaAssembler;
            return this;
        }

        ActorSystem getActorSystem() {
            return actorSystem;
        }

        public Builder setActorSystem(final ActorSystem actorSystem) {
            this.actorSystem = actorSystem;
            return this;
        }

        String getTopologyId() {
            return topologyId;
        }

        public Builder setTopologyId(final String topologyId) {
            this.topologyId = topologyId;
            return this;
        }

        NetconfClientFactory getNetconfClientFactory() {
            return netconfClientFactory;
        }

        public Builder setNetconfClientFactory(final NetconfClientFactory clientFactory) {
            netconfClientFactory = clientFactory;
            return this;
        }

        public Builder setSchemaResourceDTO(
                final NetconfDevice.SchemaResourcesDTO schemaResourceDTO) {
            this.schemaResourceDTO = schemaResourceDTO;
            return this;
        }

        NetconfDevice.SchemaResourcesDTO getSchemaResourceDTO() {
            return schemaResourceDTO;
        }

        public Builder setIdleTimeout(final Duration idleTimeout) {
            this.idleTimeout = idleTimeout;
            return this;
        }

        Duration getIdleTimeout() {
            return idleTimeout;
        }
    }
}