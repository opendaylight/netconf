/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.topology;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.opendaylight.controller.config.threadpool.ScheduledThreadPool;
import org.opendaylight.controller.config.threadpool.ThreadPool;
import org.opendaylight.controller.md.sal.binding.api.BindingTransactionChain;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataObjectModification;
import org.opendaylight.controller.md.sal.binding.api.DataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataTreeIdentifier;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.AsyncTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionChain;
import org.opendaylight.controller.md.sal.common.api.data.TransactionChainListener;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPointService;
import org.opendaylight.restconfsb.communicator.impl.sender.SenderFactory;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.node.rev160511.node.status.Module;
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
import org.opendaylight.yangtools.yang.binding.Identifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.KeyedInstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * BaseRestconfTopology watches changes in /network-topology/topology/topology-restconf datastore subtree and manages
 * restconf connectors according to these changes.
 */
public class BaseRestconfTopology implements RestconfTopology, DataTreeChangeListener<Node>, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(BaseRestconfTopology.class);

    private static final String TOPOLOGY_ID = "topology-restconf";
    private static final InstanceIdentifier<Node> topologyId = InstanceIdentifier.create(NetworkTopology.class)
            .child(Topology.class, new TopologyKey(new TopologyId(TOPOLOGY_ID)))
            .child(Node.class);

    private static final TransactionChainListener listener = new TransactionChainListener() {
        @Override
        public void onTransactionChainFailed(final TransactionChain<?, ?> transactionChain,
                                             final AsyncTransaction<?, ?> asyncTransaction,
                                             final Throwable throwable) {
            LOG.warn("Transaction failed");
        }

        @Override
        public void onTransactionChainSuccessful(final TransactionChain<?, ?> transactionChain) {
            LOG.info("Transaction successful");
        }
    };

    private final DOMMountPointService mountPointService;

    private final Map<NodeId, RestconfNodeManager> activeConnectors = new HashMap<>();
    private final SenderFactory senderFactory;
    private final ThreadPool processingExecutor;
    private final ScheduledThreadPool reconnectExecutor;
    private final ListenerRegistration<BaseRestconfTopology> dataTreeChangeListenerRegistration;
    private final DataBroker dataBroker;

    public BaseRestconfTopology(final SenderFactory senderFactory, final ThreadPool processingExecutor, final ScheduledThreadPool reconnectExecutor,
                                final DataBroker dataBroker, final DOMMountPointService mountPointService) {
        this.senderFactory = senderFactory;
        this.processingExecutor = processingExecutor;
        this.reconnectExecutor = reconnectExecutor;
        this.mountPointService = mountPointService;
        final DataTreeIdentifier<Node> id = new DataTreeIdentifier<>(LogicalDatastoreType.CONFIGURATION, topologyId);
        this.dataBroker = dataBroker;
        dataTreeChangeListenerRegistration = dataBroker.registerDataTreeChangeListener(id, this);
        final BindingTransactionChain transactionChain = dataBroker.createTransactionChain(listener);
        createParentData(transactionChain.newWriteOnlyTransaction());
    }

    @Override
    public void onDataTreeChanged(final @Nonnull Collection<DataTreeModification<Node>> collection) {
        for (final DataTreeModification<Node> change : collection) {
            final DataObjectModification<Node> rootNode = change.getRootNode();
            final NodeId nodeId = getNodeId(rootNode.getIdentifier());
            switch (rootNode.getModificationType()) {
                case SUBTREE_MODIFIED:
                    LOG.debug("Config for node {} updated", nodeId);
                    disconnectNode(nodeId);
                    connectNode(nodeId, rootNode.getDataAfter());
                    break;
                case WRITE:
                    LOG.debug("Config for node {} created", nodeId);
                    if (activeConnectors.containsKey(nodeId)) {
                        LOG.warn("RemoteDevice{{}} was already configured, reconfiguring..", nodeId);
                        disconnectNode(nodeId);
                    }
                    connectNode(nodeId, rootNode.getDataAfter());
                    break;
                case DELETE:
                    LOG.debug("Config for node {} deleted", nodeId);
                    disconnectNode(nodeId);
                    break;
            }
        }
    }

    @Override
    public String getTopologyId() {
        return TOPOLOGY_ID;
    }

    /**
     * Creates connector to device specified by parameters and register its mount point.
     *
     * @param nodeId     node id
     * @param configNode node configuration
     * @return supported yang modules list
     */
    public ListenableFuture<List<Module>> connectNode(final NodeId nodeId, final Node configNode) {
        LOG.info("Connecting RemoteDevice{{}} , with config {}", nodeId, configNode);
        final RestconfNodeManager nodeManager =
                new RestconfNodeManager(configNode, mountPointService, dataBroker, senderFactory,
                        processingExecutor, reconnectExecutor);
        activeConnectors.put(nodeId, nodeManager);
        return nodeManager.connect();
    }

    /**
     * Deregisters mount point and closes device connector.
     *
     * @param nodeId node id
     * @return future
     */
    public ListenableFuture<Void> disconnectNode(final NodeId nodeId) {
        return activeConnectors.remove(nodeId).disconnect();
    }

    private NodeId getNodeId(final InstanceIdentifier.PathArgument pathArgument) {
        if (pathArgument instanceof InstanceIdentifier.IdentifiableItem<?, ?>) {

            final Identifier key = ((InstanceIdentifier.IdentifiableItem) pathArgument).getKey();
            if (key instanceof NodeKey) {
                return ((NodeKey) key).getNodeId();
            }
        }
        throw new IllegalStateException("Unable to create NodeId from: " + pathArgument);
    }

    @Override
    public void close() throws Exception {
        for (final RestconfNodeManager restconfDeviceManager : activeConnectors.values()) {
            restconfDeviceManager.disconnect();
        }
        activeConnectors.clear();
        if (dataTreeChangeListenerRegistration != null) {
            dataTreeChangeListenerRegistration.close();
        }
    }

    private void createParentData(final WriteTransaction writeTransaction) {
        final InstanceIdentifier<NetworkTopology> networkTopologyPath = InstanceIdentifier.create(NetworkTopology.class);
        final NetworkTopology networkTopology = new NetworkTopologyBuilder().build();

        writeTransaction.merge(LogicalDatastoreType.CONFIGURATION, networkTopologyPath, networkTopology);
        writeTransaction.merge(LogicalDatastoreType.OPERATIONAL, networkTopologyPath, networkTopology);

        final TopologyId topologyId = new TopologyId(TOPOLOGY_ID);
        final Topology topology = new TopologyBuilder().setTopologyId(topologyId).setKey(new TopologyKey(topologyId)).build();
        final KeyedInstanceIdentifier<Topology, TopologyKey> topologyPath =
                InstanceIdentifier.create(NetworkTopology.class).child(Topology.class, new TopologyKey(topologyId));

        writeTransaction.merge(LogicalDatastoreType.CONFIGURATION, topologyPath, topology);
        writeTransaction.merge(LogicalDatastoreType.OPERATIONAL, topologyPath, topology);
        Futures.addCallback(writeTransaction.submit(), new FutureCallback<Void>() {
            @Override
            public void onSuccess(@Nullable final Void result) {

            }

            @Override
            public void onFailure(final Throwable t) {

            }
        });
    }

}