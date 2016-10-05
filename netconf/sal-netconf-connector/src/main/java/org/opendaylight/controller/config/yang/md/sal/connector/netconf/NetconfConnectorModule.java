/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.controller.config.yang.md.sal.connector.netconf;

import static org.opendaylight.controller.config.api.JmxAttributeValidationException.checkCondition;
import static org.opendaylight.controller.config.api.JmxAttributeValidationException.checkNotNull;

import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import javax.annotation.Nullable;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker;
import org.opendaylight.controller.sal.binding.api.BindingAwareConsumer;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.connection.parameters.YangModuleCapabilities;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.connection.parameters.YangModuleCapabilitiesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.credentials.Credentials;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.credentials.credentials.LoginPasswordBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.schema.storage.YangLibrary;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.schema.storage.YangLibraryBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public final class NetconfConnectorModule extends org.opendaylight.controller.config.yang.md.sal.connector.netconf.AbstractNetconfConnectorModule implements BindingAwareConsumer {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfConnectorModule.class);

    private static final InstanceIdentifier<Topology> TOPOLOGY_PATH = InstanceIdentifier.create(NetworkTopology.class)
            .child(Topology.class, new TopologyKey(new TopologyId("topology-netconf")));
    private final String instanceName;
    private InstanceIdentifier<Node> nodePath;
    private DataBroker dataBroker;

    public NetconfConnectorModule(final org.opendaylight.controller.config.api.ModuleIdentifier identifier, final org.opendaylight.controller.config.api.DependencyResolver dependencyResolver) {
        super(identifier, dependencyResolver);
        instanceName = identifier.getInstanceName();
    }

    public NetconfConnectorModule(final org.opendaylight.controller.config.api.ModuleIdentifier identifier, final org.opendaylight.controller.config.api.DependencyResolver dependencyResolver, final NetconfConnectorModule oldModule, final java.lang.AutoCloseable oldInstance) {
        super(identifier, dependencyResolver, oldModule, oldInstance);
        instanceName = identifier.getInstanceName();
    }

    @Override
    protected void customValidation() {
        checkNotNull(getAddress(), addressJmxAttribute);
        checkCondition(isHostAddressPresent(getAddress()), "Host address not present in " + getAddress(), addressJmxAttribute);
        checkNotNull(getPort(), portJmxAttribute);

        checkNotNull(getConnectionTimeoutMillis(), connectionTimeoutMillisJmxAttribute);
        checkCondition(getConnectionTimeoutMillis() > 0, "must be > 0", connectionTimeoutMillisJmxAttribute);

        checkNotNull(getDefaultRequestTimeoutMillis(), defaultRequestTimeoutMillisJmxAttribute);
        checkCondition(getDefaultRequestTimeoutMillis() > 0, "must be > 0", defaultRequestTimeoutMillisJmxAttribute);

        checkNotNull(getBetweenAttemptsTimeoutMillis(), betweenAttemptsTimeoutMillisJmxAttribute);
        checkCondition(getBetweenAttemptsTimeoutMillis() > 0, "must be > 0", betweenAttemptsTimeoutMillisJmxAttribute);

        // Check username + password in case of ssh
        if (!getTcpOnly()) {
            checkNotNull(getUsername(), usernameJmxAttribute);
            checkNotNull(getPassword(), passwordJmxAttribute);
        }
    }

    private boolean isHostAddressPresent(final Host address) {
        return address.getDomainName() != null ||
               address.getIpAddress() != null && (address.getIpAddress().getIpv4Address() != null || address.getIpAddress().getIpv6Address() != null);
    }

    @Override
    public java.lang.AutoCloseable createInstance() {
        getBindingRegistryDependency().registerConsumer(this);
        return this::deleteNode;
    }

    @Override
    public void onSessionInitialized(final BindingAwareBroker.ConsumerContext session) {
        dataBroker = session.getSALService(DataBroker.class);
        final WriteTransaction transaction = dataBroker.newWriteOnlyTransaction();
        final NodeId nodeId = new NodeId(instanceName);
        final NodeKey nodeKey = new NodeKey(nodeId);
        final Node node = createNetconfNode(nodeId, nodeKey);
        nodePath = TOPOLOGY_PATH.child(Node.class, nodeKey);
        transaction.put(LogicalDatastoreType.CONFIGURATION, nodePath, node);
        final CheckedFuture<Void, TransactionCommitFailedException> submitFuture = transaction.submit();
        Futures.addCallback(submitFuture, new FutureCallback<Void>() {
            @Override
            public void onSuccess(@Nullable final Void result) {
                LOG.debug("Node {} was successfully added to the topology", instanceName);
            }

            @Override
            public void onFailure(final Throwable t) {
                LOG.error("Node {} creation failed: {}", instanceName, t);
            }
        });
    }

    private void deleteNode() {
        if (dataBroker != null) {
            final WriteTransaction transaction = dataBroker.newWriteOnlyTransaction();
            transaction.delete(LogicalDatastoreType.CONFIGURATION, nodePath);
            final CheckedFuture<Void, TransactionCommitFailedException> submitFuture = transaction.submit();
            Futures.addCallback(submitFuture, new FutureCallback<Void>() {
                @Override
                public void onSuccess(@Nullable final Void result) {
                    LOG.debug("Node {} was successfully deleted from the topology", instanceName);
                }

                @Override
                public void onFailure(final Throwable t) {
                    LOG.error("Node {} deletion failed: {}", instanceName, t);
                }
            });

        }
    }

    private Node createNetconfNode(final NodeId nodeId, final NodeKey nodeKey) {
        final Credentials credentials = new LoginPasswordBuilder()
                .setUsername(getUsername())
                .setPassword(getPassword())
                .build();
        final YangModuleCapabilities capabilities;
        if (getYangModuleCapabilities() != null) {
            capabilities = new YangModuleCapabilitiesBuilder()
                    .setOverride(getYangModuleCapabilities().getOverride())
                    .setCapability(getYangModuleCapabilities().getCapability())
                    .build();
        } else {
            capabilities = null;
        }
        final YangLibrary yangLibrary;
        if (getYangLibrary() != null) {
            yangLibrary = new YangLibraryBuilder()
                    .setYangLibraryUrl(getYangLibrary().getYangLibraryUrl())
                    .setUsername(getYangLibrary().getUsername())
                    .setPassword(getYangLibrary().getPassword())
                    .build();
        } else {
            yangLibrary = null;
        }
        final NetconfNode netconfNode = new NetconfNodeBuilder()
                .setHost(getAddress())
                .setPort(getPort())
                .setCredentials(credentials)
                .setConnectionTimeoutMillis(getConnectionTimeoutMillis())
                .setDefaultRequestTimeoutMillis(getDefaultRequestTimeoutMillis())
                .setBetweenAttemptsTimeoutMillis(getBetweenAttemptsTimeoutMillis())
                .setConcurrentRpcLimit(getConcurrentRpcLimit())
                .setKeepaliveDelay(getKeepaliveDelay())
                .setMaxConnectionAttempts(getMaxConnectionAttempts())
                .setReconnectOnChangedSchema(getReconnectOnChangedSchema())
                .setSchemaCacheDirectory(getSchemaCacheDirectory())
                .setSleepFactor(getSleepFactor())
                .setTcpOnly(getTcpOnly())
                .setYangModuleCapabilities(capabilities)
                .setYangLibrary(yangLibrary)
                .build();
        return new NodeBuilder()
                .setNodeId(nodeId)
                .setKey(nodeKey)
                .addAugmentation(NetconfNode.class, netconfNode)
                .build();
    }
}
