/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.sal;

import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.Transaction;
import org.opendaylight.mdsal.binding.api.TransactionChain;
import org.opendaylight.mdsal.binding.api.TransactionChainListener;
import org.opendaylight.mdsal.binding.api.WriteTransaction;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfDeviceCapabilities;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNodeConnectionStatus.ConnectionStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.connection.status.AvailableCapabilitiesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.connection.status.ClusteredConnectionStatusBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.connection.status.UnavailableCapabilities;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.connection.status.UnavailableCapabilitiesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.connection.status.available.capabilities.AvailableCapability;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.connection.status.unavailable.capabilities.UnavailableCapability.FailureReason;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.connection.status.unavailable.capabilities.UnavailableCapabilityBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yangtools.yang.common.Empty;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Non-final for testing
public class NetconfDeviceTopologyAdapter implements TransactionChainListener, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfDeviceTopologyAdapter.class);

    private final SettableFuture<Empty> closeFuture = SettableFuture.create();
    private final DataBroker dataBroker;
    private final RemoteDeviceId id;

    private TransactionChain txChain;

    NetconfDeviceTopologyAdapter(final DataBroker dataBroker, final RemoteDeviceId id) {
        this.dataBroker = requireNonNull(dataBroker);
        this.id = requireNonNull(id);
        txChain = dataBroker.createMergingTransactionChain(this);

        final WriteTransaction writeTx = txChain.newWriteOnlyTransaction();
        LOG.trace("{}: Init device state transaction {} putting if absent operational data started.", id,
            writeTx.getIdentifier());
        writeTx.put(LogicalDatastoreType.OPERATIONAL, id.getTopologyBindingPath(), getNodeIdBuilder(id)
            .addAugmentation(new NetconfNodeBuilder()
                .setConnectionStatus(ConnectionStatus.Connecting)
                .setHost(id.getHost())
                .setPort(new PortNumber(Uint16.valueOf(id.getAddress().getPort()))).build())
            .build());
        LOG.trace("{}: Init device state transaction {} putting operational data ended.", id, writeTx.getIdentifier());

        commitTransaction(writeTx, "init");
    }

    public void updateDeviceData(final ConnectionStatus connectionStatus,
            final NetconfDeviceCapabilities capabilities, final LogicalDatastoreType dsType, final NetconfNode node) {
        NetconfNode data;
        if (node != null && dsType == LogicalDatastoreType.CONFIGURATION) {
            data = node;
        } else {
            data = buildDataForNetconfNode(connectionStatus, capabilities, dsType, node);
        }

        final WriteTransaction writeTx = txChain.newWriteOnlyTransaction();
        LOG.trace("{}: Update device state transaction {} merging operational data started.",
                id, writeTx.getIdentifier());
        writeTx.mergeParentStructurePut(dsType, id.getTopologyBindingPath().augmentation(NetconfNode.class), data);
        LOG.trace("{}: Update device state transaction {} merging operational data ended.",
                id, writeTx.getIdentifier());

        commitTransaction(writeTx, "update");
    }

    public void updateDeviceData(final boolean up, final NetconfDeviceCapabilities capabilities) {
        updateDeviceData(up ? ConnectionStatus.Connected : ConnectionStatus.Connecting, capabilities,
                LogicalDatastoreType.OPERATIONAL, null);
    }

    public void updateClusteredDeviceData(final boolean up, final String masterAddress,
                                          final NetconfDeviceCapabilities capabilities) {
        final NetconfNode data = buildDataForNetconfClusteredNode(up, masterAddress, capabilities);

        final WriteTransaction writeTx = txChain.newWriteOnlyTransaction();
        LOG.trace("{}: Update device state transaction {} merging operational data started.",
                id, writeTx.getIdentifier());
        writeTx.mergeParentStructurePut(LogicalDatastoreType.OPERATIONAL,
                id.getTopologyBindingPath().augmentation(NetconfNode.class), data);
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
                .setHost(id.getHost())
                .setPort(new PortNumber(Uint16.valueOf(id.getAddress().getPort())))
                .setConnectionStatus(ConnectionStatus.UnableToConnect).setConnectedMessage(reason).build();

        final WriteTransaction writeTx = txChain.newWriteOnlyTransaction();
        LOG.trace(
                "{}: Setting device state as failed {} putting operational data started.",
                id, writeTx.getIdentifier());
        writeTx.mergeParentStructurePut(LogicalDatastoreType.OPERATIONAL,
                id.getTopologyBindingPath().augmentation(NetconfNode.class), data);
        LOG.trace(
                "{}: Setting device state as failed {} putting operational data ended.",
                id, writeTx.getIdentifier());

        commitTransaction(writeTx, "update-failed-device");
    }

    private NetconfNode buildDataForNetconfNode(final ConnectionStatus connectionStatus,
            final NetconfDeviceCapabilities capabilities, final LogicalDatastoreType dsType, final NetconfNode node) {
        List<AvailableCapability> capabilityList = new ArrayList<>();
        capabilityList.addAll(capabilities.getNonModuleBasedCapabilities());
        capabilityList.addAll(capabilities.getResolvedCapabilities());

        final AvailableCapabilitiesBuilder avCapabalitiesBuilder = new AvailableCapabilitiesBuilder();
        avCapabalitiesBuilder.setAvailableCapability(capabilityList);

        return new NetconfNodeBuilder()
            .setHost(id.getHost())
            .setPort(new PortNumber(Uint16.valueOf(id.getAddress().getPort())))
            .setConnectionStatus(connectionStatus)
            .setAvailableCapabilities(avCapabalitiesBuilder.build())
            .setUnavailableCapabilities(unavailableCapabilities(capabilities.getUnresolvedCapabilites()))
            .build();
    }

    private NetconfNode buildDataForNetconfClusteredNode(final boolean up, final String masterNodeAddress,
                                                         final NetconfDeviceCapabilities capabilities) {
        List<AvailableCapability> capabilityList = new ArrayList<>();
        capabilityList.addAll(capabilities.getNonModuleBasedCapabilities());
        capabilityList.addAll(capabilities.getResolvedCapabilities());
        final AvailableCapabilitiesBuilder avCapabalitiesBuilder = new AvailableCapabilitiesBuilder();
        avCapabalitiesBuilder.setAvailableCapability(capabilityList);

        final NetconfNodeBuilder netconfNodeBuilder = new NetconfNodeBuilder()
                .setHost(id.getHost())
                .setPort(new PortNumber(Uint16.valueOf(id.getAddress().getPort())))
                .setConnectionStatus(up ? ConnectionStatus.Connected : ConnectionStatus.Connecting)
                .setAvailableCapabilities(avCapabalitiesBuilder.build())
                .setUnavailableCapabilities(unavailableCapabilities(capabilities.getUnresolvedCapabilites()))
                .setClusteredConnectionStatus(
                        new ClusteredConnectionStatusBuilder().setNetconfMasterNode(masterNodeAddress).build());

        return netconfNodeBuilder.build();
    }

    private static UnavailableCapabilities unavailableCapabilities(final Map<QName, FailureReason> input) {
        return new UnavailableCapabilitiesBuilder().setUnavailableCapability(input.entrySet().stream().map(
            e -> new UnavailableCapabilityBuilder().setCapability(e.getKey().toString()).setFailureReason(
                e.getValue()).build()).collect(Collectors.toList())).build();
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

    private static NodeBuilder getNodeIdBuilder(final RemoteDeviceId id) {
        return new NodeBuilder().withKey(new NodeKey(new NodeId(id.getName())));
    }

    @Override
    public void close() {
        final WriteTransaction writeTx = txChain.newWriteOnlyTransaction();
        LOG.trace("{}: Close device state transaction {} removing all data started.", id, writeTx.getIdentifier());
        writeTx.delete(LogicalDatastoreType.OPERATIONAL, id.getTopologyBindingPath());
        LOG.trace("{}: Close device state transaction {} removing all data ended.", id, writeTx.getIdentifier());
        writeTx.commit();

        txChain.close();

        try {
            closeFuture.get();
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("{}: Transaction(close) {} FAILED!", id, writeTx.getIdentifier(), e);
            throw new IllegalStateException(id + "  Transaction(close) not committed correctly", e);
        }
    }
}
