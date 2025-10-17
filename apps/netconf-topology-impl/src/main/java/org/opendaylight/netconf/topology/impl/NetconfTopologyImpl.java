/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.impl;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.DataObjectDeleted;
import org.opendaylight.mdsal.binding.api.DataObjectModified;
import org.opendaylight.mdsal.binding.api.DataObjectWritten;
import org.opendaylight.mdsal.binding.api.DataTreeChangeListener;
import org.opendaylight.mdsal.binding.api.DataTreeModification;
import org.opendaylight.mdsal.binding.api.RpcProviderService;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.netconf.client.NetconfClientFactory;
import org.opendaylight.netconf.client.mdsal.api.BaseNetconfSchemaProvider;
import org.opendaylight.netconf.client.mdsal.api.DeviceActionFactory;
import org.opendaylight.netconf.client.mdsal.api.SchemaResourceManager;
import org.opendaylight.netconf.common.NetconfTimer;
import org.opendaylight.netconf.topology.spi.AbstractNetconfTopology;
import org.opendaylight.netconf.topology.spi.NetconfClientConfigurationBuilderFactory;
import org.opendaylight.netconf.topology.spi.NetconfNodeUtils;
import org.opendaylight.netconf.topology.spi.NetconfTopologyRPCProvider;
import org.opendaylight.netconf.topology.spi.NetconfTopologySchemaAssembler;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yangtools.binding.DataObjectIdentifier.WithKey;
import org.opendaylight.yangtools.binding.DataObjectReference;
import org.opendaylight.yangtools.concepts.Registration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Non-final for testing
@Singleton
@Component(service = { })
public class NetconfTopologyImpl extends AbstractNetconfTopology
        implements DataTreeChangeListener<Node>, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfTopologyImpl.class);

    private Registration dtclReg;
    private NetconfTopologyRPCProvider rpcProvider;

    @Inject
    @Activate
    public NetconfTopologyImpl(
            @Reference final NetconfClientFactory clientFactory, @Reference final NetconfTimer timer,
            @Reference final NetconfTopologySchemaAssembler schemaAssembler,
            @Reference final SchemaResourceManager schemaRepositoryProvider, @Reference final DataBroker dataBroker,
            @Reference final DOMMountPointService mountPointService,
            @Reference final AAAEncryptionService encryptionService,
            @Reference final NetconfClientConfigurationBuilderFactory builderFactory,
            @Reference final RpcProviderService rpcProviderService,
            @Reference final BaseNetconfSchemaProvider baseSchemaProvider,
            @Reference final DeviceActionFactory deviceActionFactory) {
        this(NetconfNodeUtils.DEFAULT_TOPOLOGY_NAME, clientFactory, timer, schemaAssembler, schemaRepositoryProvider,
            dataBroker, mountPointService, encryptionService, builderFactory, rpcProviderService, baseSchemaProvider,
            deviceActionFactory);
    }

    public NetconfTopologyImpl(final String topologyId, final NetconfClientFactory clientclientFactory,
            final NetconfTimer timer, final NetconfTopologySchemaAssembler schemaAssembler,
            final SchemaResourceManager schemaRepositoryProvider, final DataBroker dataBroker,
            final DOMMountPointService mountPointService, final AAAEncryptionService encryptionService,
            final NetconfClientConfigurationBuilderFactory builderFactory, final RpcProviderService rpcProviderService,
            final BaseNetconfSchemaProvider baseSchemaProvider) {
        this(topologyId, clientclientFactory, timer, schemaAssembler, schemaRepositoryProvider, dataBroker,
            mountPointService, encryptionService, builderFactory, rpcProviderService, baseSchemaProvider, null);
    }

    @SuppressFBWarnings(value = "MC_OVERRIDABLE_METHOD_CALL_IN_CONSTRUCTOR",
        justification = "DTCL registration of 'this'")
    public NetconfTopologyImpl(final String topologyId, final NetconfClientFactory clientFactory,
            final NetconfTimer timer, final NetconfTopologySchemaAssembler schemaAssembler,
            final SchemaResourceManager schemaRepositoryProvider, final DataBroker dataBroker,
            final DOMMountPointService mountPointService, final AAAEncryptionService encryptionService,
            final NetconfClientConfigurationBuilderFactory builderFactory, final RpcProviderService rpcProviderService,
            final BaseNetconfSchemaProvider baseSchemaProvider, final DeviceActionFactory deviceActionFactory) {
        super(topologyId, clientFactory, timer, schemaAssembler, schemaRepositoryProvider, dataBroker,
            mountPointService, builderFactory, deviceActionFactory, baseSchemaProvider);

        LOG.debug("Registering datastore listener");
        dtclReg = dataBroker.registerLegacyTreeChangeListener(LogicalDatastoreType.CONFIGURATION,
            DataObjectReference.builder(NetworkTopology.class)
                .child(Topology.class, new TopologyKey(new TopologyId(topologyId)))
                .child(Node.class)
                .build(), this);
        rpcProvider = new NetconfTopologyRPCProvider(rpcProviderService, dataBroker, encryptionService, topologyId);
    }

    @PreDestroy
    @Deactivate
    @Override
    public void close() {
        if (rpcProvider != null) {
            rpcProvider.close();
            rpcProvider = null;
        }

        // close all existing connectors, delete whole topology in datastore?
        deleteAllNodes();

        if (dtclReg != null) {
            dtclReg.close();
            dtclReg = null;
        }
    }

    @Override
    public void onDataTreeChanged(final List<DataTreeModification<Node>> changes) {
        for (var change : changes) {
            final var rootNode = change.getRootNode();
            switch (rootNode) {
                case DataObjectModified<Node> modified -> ensureNode("updated", modified.dataAfter());
                case DataObjectWritten<Node> written -> ensureNode("created", written.dataAfter());
                case DataObjectDeleted<Node> ignored -> {
                    final var nodeId = ((WithKey<Node, NodeKey>) change.path()).key().getNodeId();
                    LOG.debug("Config for node {} deleted", nodeId);
                    deleteNode(nodeId);
                }
            }
        }
    }

    private void ensureNode(final String operation, final Node node) {
        final var nodeId = node.getNodeId();
        LOG.debug("Config for node {} {}", nodeId, operation);
        ensureNode(node);
    }
}
