/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.impl;

import com.google.common.annotations.VisibleForTesting;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.netty.util.concurrent.EventExecutor;
import java.util.Collection;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.controller.config.threadpool.ScheduledThreadPool;
import org.opendaylight.controller.config.threadpool.ThreadPool;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.DataObjectModification;
import org.opendaylight.mdsal.binding.api.DataTreeChangeListener;
import org.opendaylight.mdsal.binding.api.DataTreeIdentifier;
import org.opendaylight.mdsal.binding.api.DataTreeModification;
import org.opendaylight.mdsal.binding.api.RpcProviderService;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.netconf.client.NetconfClientDispatcher;
import org.opendaylight.netconf.client.mdsal.api.BaseNetconfSchemas;
import org.opendaylight.netconf.client.mdsal.api.DeviceActionFactory;
import org.opendaylight.netconf.client.mdsal.api.SchemaResourceManager;
import org.opendaylight.netconf.topology.spi.AbstractNetconfTopology;
import org.opendaylight.netconf.topology.spi.NetconfClientConfigurationBuilderFactory;
import org.opendaylight.netconf.topology.spi.NetconfNodeUtils;
import org.opendaylight.netconf.topology.spi.NetconfTopologyRPCProvider;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev221225.NetconfNodeTopologyService;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier.IdentifiableItem;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.binding.KeyedInstanceIdentifier;
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
    private Registration rpcReg;

    @Inject
    @Activate
    public NetconfTopologyImpl(
            @Reference(target = "(type=netconf-client-dispatcher)") final NetconfClientDispatcher clientDispatcher,
            @Reference(target = "(type=global-event-executor)") final EventExecutor eventExecutor,
            @Reference(target = "(type=global-netconf-ssh-scheduled-executor)")
            final ScheduledThreadPool keepaliveExecutor,
            @Reference(target = "(type=global-netconf-processing-executor)") final ThreadPool processingExecutor,
            @Reference final SchemaResourceManager schemaRepositoryProvider, @Reference final DataBroker dataBroker,
            @Reference final DOMMountPointService mountPointService,
            @Reference final AAAEncryptionService encryptionService,
            @Reference final NetconfClientConfigurationBuilderFactory builderFactory,
            @Reference final RpcProviderService rpcProviderService, @Reference final BaseNetconfSchemas baseSchemas,
            @Reference final DeviceActionFactory deviceActionFactory) {
        this(NetconfNodeUtils.DEFAULT_TOPOLOGY_NAME, clientDispatcher, eventExecutor, keepaliveExecutor,
            processingExecutor, schemaRepositoryProvider, dataBroker, mountPointService, encryptionService,
            builderFactory, rpcProviderService, baseSchemas, deviceActionFactory);
    }

    public NetconfTopologyImpl(final String topologyId, final NetconfClientDispatcher clientDispatcher,
            final EventExecutor eventExecutor, final ScheduledThreadPool keepaliveExecutor,
            final ThreadPool processingExecutor, final SchemaResourceManager schemaRepositoryProvider,
            final DataBroker dataBroker, final DOMMountPointService mountPointService,
            final AAAEncryptionService encryptionService, final NetconfClientConfigurationBuilderFactory builderFactory,
            final RpcProviderService rpcProviderService, final BaseNetconfSchemas baseSchemas) {
        this(topologyId, clientDispatcher, eventExecutor, keepaliveExecutor, processingExecutor,
            schemaRepositoryProvider, dataBroker, mountPointService, encryptionService, builderFactory,
            rpcProviderService, baseSchemas, null);
    }

    @SuppressFBWarnings(value = "MC_OVERRIDABLE_METHOD_CALL_IN_CONSTRUCTOR",
        justification = "DTCL registration of 'this'")
    public NetconfTopologyImpl(final String topologyId, final NetconfClientDispatcher clientDispatcher,
            final EventExecutor eventExecutor, final ScheduledThreadPool keepaliveExecutor,
            final ThreadPool processingExecutor, final SchemaResourceManager schemaRepositoryProvider,
            final DataBroker dataBroker, final DOMMountPointService mountPointService,
            final AAAEncryptionService encryptionService, final NetconfClientConfigurationBuilderFactory builderFactory,
            final RpcProviderService rpcProviderService, final BaseNetconfSchemas baseSchemas,
            final DeviceActionFactory deviceActionFactory) {
        super(topologyId, clientDispatcher, eventExecutor, keepaliveExecutor, processingExecutor,
            schemaRepositoryProvider, dataBroker, mountPointService, builderFactory, deviceActionFactory, baseSchemas);

        LOG.debug("Registering datastore listener");
        dtclReg = dataBroker.registerDataTreeChangeListener(DataTreeIdentifier.create(
            LogicalDatastoreType.CONFIGURATION, createTopologyListPath(topologyId).child(Node.class)), this);
        rpcReg = rpcProviderService.registerRpcImplementation(NetconfNodeTopologyService.class,
            new NetconfTopologyRPCProvider(dataBroker, encryptionService, topologyId));
    }

    @PreDestroy
    @Deactivate
    @Override
    public void close() {
        if (rpcReg != null) {
            rpcReg.close();
            rpcReg = null;
        }

        // close all existing connectors, delete whole topology in datastore?
        deleteAllNodes();

        if (dtclReg != null) {
            dtclReg.close();
            dtclReg = null;
        }
    }

    @Override
    public void onDataTreeChanged(final Collection<DataTreeModification<Node>> collection) {
        for (final DataTreeModification<Node> change : collection) {
            final DataObjectModification<Node> rootNode = change.getRootNode();
            final NodeId nodeId;
            switch (rootNode.getModificationType()) {
                case SUBTREE_MODIFIED -> {
                    LOG.debug("Config for node {} updated", getNodeId(rootNode.getIdentifier()));
                    ensureNode(rootNode.getDataAfter());
                }
                case WRITE -> {
                    LOG.debug("Config for node {} created", getNodeId(rootNode.getIdentifier()));
                    ensureNode(rootNode.getDataAfter());
                }
                case DELETE -> {
                    nodeId = getNodeId(rootNode.getIdentifier());
                    LOG.debug("Config for node {} deleted", nodeId);
                    deleteNode(nodeId);
                }
                default -> LOG.debug("Unsupported modification type: {}.", rootNode.getModificationType());
            }
        }
    }

    /**
     * Determines the Netconf Node Node ID, given the node's instance
     * identifier.
     *
     * @param pathArgument Node's path argument
     * @return     NodeId for the node
     */
    @VisibleForTesting
    static NodeId getNodeId(final PathArgument pathArgument) {
        if (pathArgument instanceof IdentifiableItem<?, ?> ident && ident.getKey() instanceof NodeKey nodeKey) {
            return nodeKey.getNodeId();
        }
        throw new IllegalStateException("Unable to create NodeId from: " + pathArgument);
    }

    @VisibleForTesting
    static KeyedInstanceIdentifier<Topology, TopologyKey> createTopologyListPath(final String topologyId) {
        return InstanceIdentifier.create(NetworkTopology.class)
            .child(Topology.class, new TopologyKey(new TopologyId(topologyId)));
    }
}
