/*
 * Copyright (c) 2017 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl.utils;

import static java.util.Objects.requireNonNull;

import java.time.Duration;
import org.apache.pekko.actor.ActorSystem;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.singleton.api.ClusterSingletonServiceProvider;
import org.opendaylight.netconf.client.NetconfClientFactory;
import org.opendaylight.netconf.client.mdsal.api.BaseNetconfSchemaProvider;
import org.opendaylight.netconf.client.mdsal.api.DeviceNetconfSchemaProvider;
import org.opendaylight.netconf.common.NetconfTimer;
import org.opendaylight.netconf.topology.spi.NetconfTopologySchemaAssembler;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.binding.DataObjectIdentifier;

public final class NetconfTopologySetup {
    private final ClusterSingletonServiceProvider clusterSingletonServiceProvider;
    private final DataBroker dataBroker;
    private final DataObjectIdentifier<Node> instanceIdentifier;
    private final Node node;
    private final NetconfTimer timer;
    private final NetconfTopologySchemaAssembler schemaAssembler;
    private final ActorSystem actorSystem;
    private final NetconfClientFactory netconfClientFactory;
    private final String topologyId;
    private final DeviceNetconfSchemaProvider deviceSchemaProvider;
    private final Duration idleTimeout;
    private final BaseNetconfSchemaProvider baseSchemaProvider;

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
        deviceSchemaProvider = builder.getDeviceSchemaProvider();
        idleTimeout = builder.getIdleTimeout();
        baseSchemaProvider = builder.getBaseSchemaProvider();
    }

    public ClusterSingletonServiceProvider getClusterSingletonServiceProvider() {
        return clusterSingletonServiceProvider;
    }

    public DataBroker getDataBroker() {
        return dataBroker;
    }

    public DataObjectIdentifier<Node> getInstanceIdentifier() {
        return instanceIdentifier;
    }

    public Node getNode() {
        return node;
    }

    public NetconfTopologySchemaAssembler getSchemaAssembler() {
        return schemaAssembler;
    }

    public NetconfTimer getTimer() {
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

    public DeviceNetconfSchemaProvider getDeviceSchemaProvider() {
        return deviceSchemaProvider;
    }

    public Duration getIdleTimeout() {
        return idleTimeout;
    }

    public BaseNetconfSchemaProvider getBaseSchemaProvider() {
        return baseSchemaProvider;
    }

    public static @NonNull Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private ClusterSingletonServiceProvider clusterSingletonServiceProvider;
        private DataBroker dataBroker;
        private DataObjectIdentifier<Node> instanceIdentifier;
        private Node node;
        private NetconfTimer timer;
        private NetconfTopologySchemaAssembler schemaAssembler;
        private ActorSystem actorSystem;
        private String topologyId;
        private NetconfClientFactory netconfClientFactory;
        private DeviceNetconfSchemaProvider deviceSchemaProvider;
        private Duration idleTimeout;
        private BaseNetconfSchemaProvider baseSchemaProvider;

        private Builder() {
            // Hidden on purpose
        }

        BaseNetconfSchemaProvider getBaseSchemaProvider() {
            return requireNonNull(baseSchemaProvider, "BaseSchemas not initialized");
        }

        public Builder setBaseSchemaProvider(final BaseNetconfSchemaProvider baseSchemaProvider) {
            this.baseSchemaProvider = requireNonNull(baseSchemaProvider);
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

        DataObjectIdentifier<Node> getInstanceIdentifier() {
            return instanceIdentifier;
        }

        public Builder setInstanceIdentifier(final DataObjectIdentifier<Node> instanceIdentifier) {
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

        NetconfTimer getTimer() {
            return timer;
        }

        public Builder setTimer(final NetconfTimer timer) {
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

        public Builder setDeviceSchemaProvider(final DeviceNetconfSchemaProvider deviceSchemaProvider) {
            this.deviceSchemaProvider = deviceSchemaProvider;
            return this;
        }

        DeviceNetconfSchemaProvider getDeviceSchemaProvider() {
            return deviceSchemaProvider;
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