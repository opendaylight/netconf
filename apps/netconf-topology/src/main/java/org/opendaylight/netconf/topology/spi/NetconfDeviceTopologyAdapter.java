/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.spi;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import java.util.concurrent.ExecutionException;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.Transaction;
import org.opendaylight.mdsal.binding.api.TransactionChain;
import org.opendaylight.mdsal.binding.api.TransactionChainListener;
import org.opendaylight.mdsal.binding.api.WriteTransaction;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netconf.client.mdsal.NetconfDeviceCapabilities;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev230430.ConnectionOper.ConnectionStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev230430.connection.oper.AvailableCapabilitiesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev230430.connection.oper.ClusteredConnectionStatusBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev230430.connection.oper.UnavailableCapabilitiesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev230430.connection.oper.available.capabilities.AvailableCapability;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev230430.connection.oper.unavailable.capabilities.UnavailableCapabilityBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev221225.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev221225.NetconfNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.KeyedInstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Empty;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NetconfDeviceTopologyAdapter implements TransactionChainListener, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfDeviceTopologyAdapter.class);

    private final SettableFuture<Empty> closeFuture = SettableFuture.create();
    private final @NonNull KeyedInstanceIdentifier<Topology, TopologyKey> topologyPath;
    private final DataBroker dataBroker;
    private final RemoteDeviceId id;

    private TransactionChain txChain;

    public NetconfDeviceTopologyAdapter(final DataBroker dataBroker,
            final KeyedInstanceIdentifier<Topology, TopologyKey> topologyPath, final RemoteDeviceId id) {
        this.dataBroker = requireNonNull(dataBroker);
        this.topologyPath = requireNonNull(topologyPath);
        this.id = requireNonNull(id);
        txChain = dataBroker.createMergingTransactionChain(this);

        final WriteTransaction writeTx = txChain.newWriteOnlyTransaction();
        LOG.trace("{}: Init device state transaction {} putting if absent operational data started.", id,
            writeTx.getIdentifier());
        final var nodePath = nodePath();
        writeTx.put(LogicalDatastoreType.OPERATIONAL, nodePath, new NodeBuilder()
            .withKey(nodePath.getKey())
            .addAugmentation(new NetconfNodeBuilder()
                .setConnectionStatus(ConnectionStatus.Connecting)
                .setHost(id.host())
                .setPort(new PortNumber(Uint16.valueOf(id.address().getPort()))).build())
            .build());
        LOG.trace("{}: Init device state transaction {} putting operational data ended.", id, writeTx.getIdentifier());

        commitTransaction(writeTx, "init");
    }

    private @NonNull KeyedInstanceIdentifier<Node, NodeKey> nodePath() {
        return topologyPath.child(Node.class, new NodeKey(new NodeId(id.name())));
    }

    private @NonNull InstanceIdentifier<NetconfNode> netconfNodePath() {
        return nodePath().augmentation(NetconfNode.class);
    }

    public void updateDeviceData(final boolean up, final NetconfDeviceCapabilities capabilities,
            final SessionIdType sessionId) {
        final WriteTransaction writeTx = txChain.newWriteOnlyTransaction();
        LOG.trace("{}: Update device state transaction {} merging operational data started.",
                id, writeTx.getIdentifier());

        // FIXME: this needs to be tied together with node's operational existence
        writeTx.mergeParentStructurePut(LogicalDatastoreType.OPERATIONAL, netconfNodePath(),
            newNetconfNodeBuilder(up, capabilities, sessionId).build());
        LOG.trace("{}: Update device state transaction {} merging operational data ended.",
                id, writeTx.getIdentifier());

        commitTransaction(writeTx, "update");
    }

    public void updateClusteredDeviceData(final boolean up, final String masterAddress,
            final NetconfDeviceCapabilities capabilities, final SessionIdType sessionId) {
        final WriteTransaction writeTx = txChain.newWriteOnlyTransaction();
        LOG.trace("{}: Update device state transaction {} merging operational data started.",
                id, writeTx.getIdentifier());
        writeTx.mergeParentStructurePut(LogicalDatastoreType.OPERATIONAL, netconfNodePath(),
            newNetconfNodeBuilder(up, capabilities, sessionId)
                .setClusteredConnectionStatus(new ClusteredConnectionStatusBuilder()
                    .setNetconfMasterNode(masterAddress)
                    .build())
                .build());
        LOG.trace("{}: Update device state transaction {} merging operational data ended.",
                id, writeTx.getIdentifier());

        commitTransaction(writeTx, "update");
    }

    @Override
    public void onTransactionChainFailed(final TransactionChain chain, final Transaction transaction,
            final Throwable cause) {
        LOG.warn("{}: TransactionChain({}) {} FAILED!", id, chain, transaction.getIdentifier(), cause);
        chain.close();

        txChain = dataBroker.createMergingTransactionChain(this);
        LOG.info("{}: TransactionChain reset to {}", id, txChain);
        // FIXME: restart last update
    }

    @Override
    public void onTransactionChainSuccessful(final TransactionChain chain) {
        LOG.trace("{}: TransactionChain({}) SUCCESSFUL", id, chain);
        closeFuture.set(Empty.value());
    }

    public void setDeviceAsFailed(final Throwable throwable) {
        String reason = throwable != null && throwable.getMessage() != null ? throwable.getMessage() : "Unknown reason";

        final NetconfNode data = new NetconfNodeBuilder()
                .setHost(id.host())
                .setPort(new PortNumber(Uint16.valueOf(id.address().getPort())))
                .setConnectionStatus(ConnectionStatus.UnableToConnect).setConnectedMessage(reason).build();

        final WriteTransaction writeTx = txChain.newWriteOnlyTransaction();
        LOG.trace(
                "{}: Setting device state as failed {} putting operational data started.",
                id, writeTx.getIdentifier());
        writeTx.mergeParentStructurePut(LogicalDatastoreType.OPERATIONAL, netconfNodePath(), data);
        LOG.trace(
                "{}: Setting device state as failed {} putting operational data ended.",
                id, writeTx.getIdentifier());

        commitTransaction(writeTx, "update-failed-device");
    }

    private NetconfNodeBuilder newNetconfNodeBuilder(final boolean up, final NetconfDeviceCapabilities capabilities,
            final SessionIdType sessionId) {
        return new NetconfNodeBuilder()
            .setHost(id.host())
            .setPort(new PortNumber(Uint16.valueOf(id.address().getPort())))
            .setConnectionStatus(up ? ConnectionStatus.Connected : ConnectionStatus.Connecting)
            .setAvailableCapabilities(new AvailableCapabilitiesBuilder()
                .setAvailableCapability(ImmutableList.<AvailableCapability>builder()
                    .addAll(capabilities.nonModuleBasedCapabilities())
                    .addAll(capabilities.resolvedCapabilities())
                    .build())
                .build())
            .setUnavailableCapabilities(new UnavailableCapabilitiesBuilder()
                .setUnavailableCapability(capabilities.unresolvedCapabilites().entrySet().stream()
                    .map(unresolved -> new UnavailableCapabilityBuilder()
                        // FIXME: better conversion than 'toString' ?
                        .setCapability(unresolved.getKey().toString())
                        .setFailureReason(unresolved.getValue())
                        .build())
                    .toList())
                .build())
            .setSessionId(sessionId);
    }

    private void commitTransaction(final WriteTransaction transaction, final String txType) {
        LOG.trace("{}: Committing Transaction {}:{}", id, txType, transaction.getIdentifier());

        transaction.commit().addCallback(new FutureCallback<CommitInfo>() {
            @Override
            public void onSuccess(final CommitInfo result) {
                LOG.trace("{}: Transaction({}) {} SUCCESSFUL", id, txType, transaction.getIdentifier());
            }

            @Override
            public void onFailure(final Throwable throwable) {
                LOG.error("{}: Transaction({}) {} FAILED!", id, txType, transaction.getIdentifier(), throwable);
            }
        }, MoreExecutors.directExecutor());
    }

    @Override
    public void close() {
        final WriteTransaction writeTx = txChain.newWriteOnlyTransaction();
        LOG.trace("{}: Close device state transaction {} removing all data started.", id, writeTx.getIdentifier());
        writeTx.delete(LogicalDatastoreType.OPERATIONAL, nodePath());
        LOG.trace("{}: Close device state transaction {} removing all data ended.", id, writeTx.getIdentifier());
        commitTransaction(writeTx, "close");

        txChain.close();

        try {
            closeFuture.get();
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("{}: Transaction(close) {} FAILED!", id, writeTx.getIdentifier(), e);
            throw new IllegalStateException(id + "  Transaction(close) not committed correctly", e);
        }
    }
}
