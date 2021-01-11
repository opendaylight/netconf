/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.impl;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.MoreExecutors;
import io.netty.util.concurrent.EventExecutor;
import java.util.Collection;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.controller.config.threadpool.ScheduledThreadPool;
import org.opendaylight.controller.config.threadpool.ThreadPool;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.DataObjectModification;
import org.opendaylight.mdsal.binding.api.DataTreeChangeListener;
import org.opendaylight.mdsal.binding.api.DataTreeIdentifier;
import org.opendaylight.mdsal.binding.api.DataTreeModification;
import org.opendaylight.mdsal.binding.api.RpcProviderService;
import org.opendaylight.mdsal.binding.api.WriteTransaction;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.netconf.client.NetconfClientDispatcher;
import org.opendaylight.netconf.sal.connect.api.DeviceActionFactory;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceHandler;
import org.opendaylight.netconf.sal.connect.api.SchemaResourceManager;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;
import org.opendaylight.netconf.sal.connect.netconf.sal.NetconfDeviceSalFacade;
import org.opendaylight.netconf.sal.connect.netconf.schema.mapping.BaseNetconfSchemas;
import org.opendaylight.netconf.sal.connect.util.NetconfTopologyRPCProvider;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.netconf.topology.spi.AbstractNetconfTopology;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNodeTopologyService;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopologyBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.concepts.ObjectRegistration;
import org.opendaylight.yangtools.yang.binding.Identifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.KeyedInstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetconfTopologyImpl extends AbstractNetconfTopology
        implements DataTreeChangeListener<Node>, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(NetconfTopologyImpl.class);

    private final RpcProviderService rpcProviderService;
    private ListenerRegistration<NetconfTopologyImpl> datastoreListenerRegistration = null;
    private ObjectRegistration<?> rpcReg = null;

    public NetconfTopologyImpl(final String topologyId, final NetconfClientDispatcher clientDispatcher,
            final EventExecutor eventExecutor, final ScheduledThreadPool keepaliveExecutor,
            final ThreadPool processingExecutor, final SchemaResourceManager schemaRepositoryProvider,
            final DataBroker dataBroker, final DOMMountPointService mountPointService,
            final AAAEncryptionService encryptionService, final RpcProviderService rpcProviderService,
            final BaseNetconfSchemas baseSchemas) {
        this(topologyId, clientDispatcher, eventExecutor, keepaliveExecutor, processingExecutor,
                schemaRepositoryProvider, dataBroker, mountPointService, encryptionService, rpcProviderService,
                baseSchemas, null);
    }

    public NetconfTopologyImpl(final String topologyId, final NetconfClientDispatcher clientDispatcher,
            final EventExecutor eventExecutor, final ScheduledThreadPool keepaliveExecutor,
            final ThreadPool processingExecutor, final SchemaResourceManager schemaRepositoryProvider,
            final DataBroker dataBroker, final DOMMountPointService mountPointService,
            final AAAEncryptionService encryptionService, final RpcProviderService rpcProviderService,
            final BaseNetconfSchemas baseSchemas, final DeviceActionFactory deviceActionFactory) {
        super(topologyId, clientDispatcher, eventExecutor, keepaliveExecutor, processingExecutor,
                schemaRepositoryProvider, dataBroker, mountPointService, encryptionService, deviceActionFactory,
                baseSchemas);
        this.rpcProviderService = requireNonNull(rpcProviderService);
    }

    @Override
    public void close() {
        if (rpcReg != null) {
            rpcReg.close();
            rpcReg = null;
        }

        // close all existing connectors, delete whole topology in datastore?
        for (final NetconfConnectorDTO connectorDTO : activeConnectors.values()) {
            connectorDTO.close();
        }
        activeConnectors.clear();

        if (datastoreListenerRegistration != null) {
            datastoreListenerRegistration.close();
            datastoreListenerRegistration = null;
        }
    }

    @Override
    protected RemoteDeviceHandler<NetconfSessionPreferences> createSalFacade(final RemoteDeviceId id) {
        return new NetconfDeviceSalFacade(id, mountPointService, dataBroker, topologyId);
    }

    /**
     * Invoked by blueprint.
     */
    public void init() {
        final WriteTransaction wtx = dataBroker.newWriteOnlyTransaction();
        initTopology(wtx, LogicalDatastoreType.CONFIGURATION);
        initTopology(wtx, LogicalDatastoreType.OPERATIONAL);
        wtx.commit().addCallback(new FutureCallback<CommitInfo>() {
            @Override
            public void onSuccess(final CommitInfo result) {
                LOG.debug("topology initialization successful");
            }

            @Override
            public void onFailure(final Throwable throwable) {
                LOG.error("Unable to initialize netconf-topology", throwable);
            }
        }, MoreExecutors.directExecutor());

        LOG.debug("Registering datastore listener");
        datastoreListenerRegistration = dataBroker.registerDataTreeChangeListener(DataTreeIdentifier.create(
            LogicalDatastoreType.CONFIGURATION, createTopologyListPath(topologyId).child(Node.class)), this);
        rpcReg = rpcProviderService.registerRpcImplementation(NetconfNodeTopologyService.class,
            new NetconfTopologyRPCProvider(dataBroker, encryptionService, topologyId));
    }

    @Override
    public void onDataTreeChanged(final Collection<DataTreeModification<Node>> collection) {
        for (final DataTreeModification<Node> change : collection) {
            final DataObjectModification<Node> rootNode = change.getRootNode();
            final NodeId nodeId;
            switch (rootNode.getModificationType()) {
                case SUBTREE_MODIFIED:
                    nodeId = getNodeId(rootNode.getIdentifier());
                    LOG.debug("Config for node {} updated", nodeId);
                    disconnectNode(nodeId);
                    connectNode(nodeId, rootNode.getDataAfter());
                    break;
                case WRITE:
                    nodeId = getNodeId(rootNode.getIdentifier());
                    LOG.debug("Config for node {} created", nodeId);
                    if (activeConnectors.containsKey(nodeId)) {
                        LOG.warn("RemoteDevice{{}} was already configured, reconfiguring..", nodeId);
                        disconnectNode(nodeId);
                    }
                    connectNode(nodeId, rootNode.getDataAfter());
                    break;
                case DELETE:
                    nodeId = getNodeId(rootNode.getIdentifier());
                    LOG.debug("Config for node {} deleted", nodeId);
                    disconnectNode(nodeId);
                    break;
                default:
                    LOG.debug("Unsupported modification type: {}.", rootNode.getModificationType());
            }
        }
    }

    private void initTopology(final WriteTransaction wtx, final LogicalDatastoreType datastoreType) {
        final NetworkTopology networkTopology = new NetworkTopologyBuilder().build();
        final InstanceIdentifier<NetworkTopology> networkTopologyId =
                InstanceIdentifier.builder(NetworkTopology.class).build();
        wtx.merge(datastoreType, networkTopologyId, networkTopology);
        final Topology topology = new TopologyBuilder().setTopologyId(new TopologyId(topologyId)).build();
        wtx.merge(datastoreType,
                networkTopologyId.child(Topology.class, new TopologyKey(new TopologyId(topologyId))), topology);
    }

    /**
     * Determines the Netconf Node Node ID, given the node's instance
     * identifier.
     *
     * @param pathArgument Node's path argument
     * @return     NodeId for the node
     */
    @VisibleForTesting
    static NodeId getNodeId(final InstanceIdentifier.PathArgument pathArgument) {
        if (pathArgument instanceof InstanceIdentifier.IdentifiableItem<?, ?>) {
            final Identifier<?> key = ((InstanceIdentifier.IdentifiableItem<?, ?>) pathArgument).getKey();
            if (key instanceof NodeKey) {
                return ((NodeKey) key).getNodeId();
            }
        }
        throw new IllegalStateException("Unable to create NodeId from: " + pathArgument);
    }

    @VisibleForTesting
    static KeyedInstanceIdentifier<Topology, TopologyKey> createTopologyListPath(final String topologyId) {
        final InstanceIdentifier<NetworkTopology> networkTopology = InstanceIdentifier.create(NetworkTopology.class);
        return networkTopology.child(Topology.class, new TopologyKey(new TopologyId(topologyId)));
    }
}
