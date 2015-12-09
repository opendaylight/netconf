/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.impl;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import javax.annotation.Nonnull;
import org.opendaylight.controller.md.sal.binding.api.BindingTransactionChain;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.AsyncTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionChain;
import org.opendaylight.controller.md.sal.common.api.data.TransactionChainListener;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.netconf.topology.util.NodeWriter;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopologyBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.KeyedInstanceIdentifier;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TopologyNodeWriter implements NodeWriter{

    private static final Logger LOG = LoggerFactory.getLogger(TopologyNodeWriter.class);

    private final String topologyId;
    private final BindingTransactionChain txChain;

    private final InstanceIdentifier<NetworkTopology> networkTopologyPath;
    private final KeyedInstanceIdentifier<Topology, TopologyKey> topologyListPath;

    public TopologyNodeWriter(final String topologyId, final DataBroker dataBroker) {
        this.topologyId = topologyId;
        this.txChain = Preconditions.checkNotNull(dataBroker).createTransactionChain(new TransactionChainListener() {
            @Override
            public void onTransactionChainFailed(TransactionChain<?, ?> chain, AsyncTransaction<?, ?> transaction, Throwable cause) {
                LOG.error("{}: TransactionChain({}) {} FAILED!", chain,
                        transaction.getIdentifier(), cause);
                throw new IllegalStateException("Clustered topology writer TransactionChain(" + chain + ") not committed correctly", cause);
            }

            @Override
            public void onTransactionChainSuccessful(TransactionChain<?, ?> chain) {
                LOG.trace("Clustered topology writer TransactionChain({}) SUCCESSFUL", chain);
            }
        });

        this.networkTopologyPath = InstanceIdentifier.builder(NetworkTopology.class).build();
        this.topologyListPath = networkTopologyPath.child(Topology.class, new TopologyKey(new TopologyId(topologyId)));
    }

    @Override
    public void init(@Nonnull NodeId id, @Nonnull Node operationalDataNode) {
        final WriteTransaction writeTx = txChain.newWriteOnlyTransaction();

        createNetworkTopologyIfNotPresent(writeTx);
        final InstanceIdentifier<Node> path = createBindingPathForTopology(new NodeKey(id), topologyId);

        LOG.trace("{}: Init device state transaction {} putting if absent operational data started. Putting data on path {}",
                id.getValue(), writeTx.getIdentifier(), path);
        writeTx.put(LogicalDatastoreType.OPERATIONAL, path, operationalDataNode);
        LOG.trace("{}: Init device state transaction {} putting operational data ended.",
                id.getValue(), writeTx.getIdentifier());

        commitTransaction(writeTx, "init", id);
    }

    @Override
    public void update(@Nonnull NodeId id, @Nonnull Node operationalDataNode) {
        final WriteTransaction writeTx = txChain.newWriteOnlyTransaction();

        final InstanceIdentifier<Node> path = createBindingPathForTopology(new NodeKey(id), topologyId);
        LOG.trace("{}: Update device state transaction {} merging operational data started. Putting data on path {}",
                id, writeTx.getIdentifier(), operationalDataNode);
        writeTx.put(LogicalDatastoreType.OPERATIONAL, path, operationalDataNode);
        LOG.trace("{}: Update device state transaction {} merging operational data ended.",
                id, writeTx.getIdentifier());

        commitTransaction(writeTx, "update", id);
    }

    @Override
    public void delete(@Nonnull NodeId id) {
        final WriteTransaction writeTx = txChain.newWriteOnlyTransaction();

        final InstanceIdentifier<Node> path = createBindingPathForTopology(new NodeKey(id), topologyId);

        LOG.trace(
                "{}: Close device state transaction {} removing all data started. Path: {}",
                id, writeTx.getIdentifier(), path);
        writeTx.delete(LogicalDatastoreType.OPERATIONAL, path);
        LOG.trace(
                "{}: Close device state transaction {} removing all data ended.",
                id, writeTx.getIdentifier());

        commitTransaction(writeTx, "close", id);
    }

    private void commitTransaction(final WriteTransaction transaction, final String txType, final NodeId id) {
        LOG.trace("{}: Committing Transaction {}:{}", id.getValue(), txType,
                transaction.getIdentifier());
        final CheckedFuture<Void, TransactionCommitFailedException> result = transaction.submit();

        Futures.addCallback(result, new FutureCallback<Void>() {
            @Override
            public void onSuccess(final Void result) {
                LOG.trace("{}: Transaction({}) {} SUCCESSFUL", id.getValue(), txType,
                        transaction.getIdentifier());
            }

            @Override
            public void onFailure(final Throwable t) {
                LOG.error("{}: Transaction({}) {} FAILED!", id.getValue(), txType,
                        transaction.getIdentifier(), t);
                throw new IllegalStateException(id.getValue() + "  Transaction(" + txType + ") not committed correctly", t);
            }
        });
    }

    private void createNetworkTopologyIfNotPresent(final WriteTransaction writeTx) {

        final NetworkTopology networkTopology = new NetworkTopologyBuilder().build();
        LOG.trace("{}: Merging {} container to ensure its presence", topologyId,
                NetworkTopology.QNAME, writeTx.getIdentifier());
        writeTx.merge(LogicalDatastoreType.OPERATIONAL, networkTopologyPath, networkTopology);

        final Topology topology = new TopologyBuilder().setTopologyId(new TopologyId(topologyId)).build();
        LOG.trace("{}: Merging {} container to ensure its presence", topologyId,
                Topology.QNAME, writeTx.getIdentifier());
        writeTx.merge(LogicalDatastoreType.OPERATIONAL, topologyListPath, topology);
    }

    private static InstanceIdentifier<Node> createBindingPathForTopology(final NodeKey key, final String topologyId) {
        final InstanceIdentifier<NetworkTopology> networkTopology = InstanceIdentifier.builder(NetworkTopology.class).build();
        final KeyedInstanceIdentifier<Topology, TopologyKey> topology = networkTopology.child(Topology.class, new TopologyKey(new TopologyId(topologyId)));
        return topology
                .child(Node.class, new NodeKey(new NodeId(key.getNodeId().getValue())));
    }

    private static org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier createBIPathForTopology(final String name, final String topologyId) {
        final YangInstanceIdentifier.InstanceIdentifierBuilder builder = YangInstanceIdentifier.builder();
        builder
                .node(NetworkTopology.QNAME)
                .node(Topology.QNAME)
                .nodeWithKey(Topology.QNAME, QName.create(Topology.QNAME, "topology-id"), topologyId)
                .node(Node.QNAME)
                .nodeWithKey(Node.QNAME, QName.create(Node.QNAME, "node-id"), name);
        return builder.build();
    }
}
