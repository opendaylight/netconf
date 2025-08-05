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
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import org.checkerframework.checker.lock.qual.GuardedBy;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.TransactionChain;
import org.opendaylight.mdsal.binding.api.WriteTransaction;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netconf.client.mdsal.NetconfDeviceCapabilities;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev250805.ConnectionOper.ConnectionStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev250805.connection.oper.AvailableCapabilitiesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev250805.connection.oper.ClusteredConnectionStatusBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev250805.connection.oper.UnavailableCapabilitiesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev250805.connection.oper.available.capabilities.AvailableCapability;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev250805.connection.oper.unavailable.capabilities.UnavailableCapabilityBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev250805.credentials.Credentials;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev240911.NetconfNodeAugment;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev240911.NetconfNodeAugmentBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev240911.netconf.node.augment.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev240911.netconf.node.augment.NetconfNodeBuilder;
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

public final class NetconfDeviceTopologyAdapter implements FutureCallback<Empty> {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfDeviceTopologyAdapter.class);

    private final @NonNull KeyedInstanceIdentifier<Topology, TopologyKey> topologyPath;
    private final DataBroker dataBroker;
    private final RemoteDeviceId id;
    private final Credentials credentials;

    @GuardedBy("this")
    private SettableFuture<Empty> closeFuture;
    @GuardedBy("this")
    private TransactionChain txChain;

    /**
     * NetconfDeviceTopologyAdapter is responsible for managing the state of a NETCONF device
     * within the topology, handling updates to its operational data in the datastore.
     *
     * @param dataBroker   the DataBroker used to interact with the datastore
     * @param topologyPath the InstanceIdentifier pointing to the topology this device belongs to
     * @param id           the unique identifier of the remote device
     * @param credentials  the credentials used to authenticate the remote device
     */
    public NetconfDeviceTopologyAdapter(final DataBroker dataBroker,
            final KeyedInstanceIdentifier<Topology, TopologyKey> topologyPath, final RemoteDeviceId id,
            final Credentials credentials) {
        this.dataBroker = requireNonNull(dataBroker);
        this.topologyPath = requireNonNull(topologyPath);
        this.id = requireNonNull(id);
        this.credentials = requireNonNull(credentials);
        txChain = dataBroker.createMergingTransactionChain();
        txChain.addCallback(this);

        final var tx = txChain.newWriteOnlyTransaction();
        LOG.trace("{}: Init device state transaction {} putting if absent operational data started.", id,
            tx.getIdentifier());
        final var nodePath = nodePath();
        tx.put(LogicalDatastoreType.OPERATIONAL, nodePath, new NodeBuilder()
            .withKey(nodePath.getKey())
            .addAugmentation(new NetconfNodeAugmentBuilder()
                .setNetconfNode(new NetconfNodeBuilder()
                    .setConnectionStatus(ConnectionStatus.Connecting)
                    .setHost(id.host())
                    .setCredentials(credentials)
                    .setPort(new PortNumber(Uint16.valueOf(id.address().getPort()))).build())
                .build())
            .build());
        LOG.trace("{}: Init device state transaction {} putting operational data ended.", id, tx.getIdentifier());
        commitTransaction(tx, "init");
    }

    public synchronized void updateDeviceData(final boolean up, final NetconfDeviceCapabilities capabilities,
            final SessionIdType sessionId) {
        final var tx = txChain.newWriteOnlyTransaction();
        LOG.trace("{}: Update device state transaction {} merging operational data started.", id, tx.getIdentifier());
        tx.put(LogicalDatastoreType.OPERATIONAL, netconfNodePath(),
            newNetconfNodeBuilder(up, capabilities, sessionId).build());
        LOG.trace("{}: Update device state transaction {} merging operational data ended.", id, tx.getIdentifier());
        commitTransaction(tx, "update");
    }

    public synchronized void updateClusteredDeviceData(final boolean up, final String masterAddress,
            final NetconfDeviceCapabilities capabilities, final SessionIdType sessionId) {
        final var tx = txChain.newWriteOnlyTransaction();
        LOG.trace("{}: Update device state transaction {} merging operational data started.", id, tx.getIdentifier());
        tx.put(LogicalDatastoreType.OPERATIONAL, netconfNodePath(), newNetconfNodeBuilder(up, capabilities, sessionId)
            .setClusteredConnectionStatus(new ClusteredConnectionStatusBuilder()
                .setNetconfMasterNode(masterAddress)
                .build())
            .build());
        LOG.trace("{}: Update device state transaction {} merging operational data ended.", id, tx.getIdentifier());
        commitTransaction(tx, "update");
    }

    public synchronized void setDeviceAsFailed(final Throwable throwable) {
        final var data = new NetconfNodeBuilder()
                .setHost(id.host())
                .setPort(new PortNumber(Uint16.valueOf(id.address().getPort())))
                .setCredentials(credentials)
                .setConnectionStatus(ConnectionStatus.UnableToConnect)
                .setConnectedMessage(extractReason(throwable))
                .build();

        final var tx = txChain.newWriteOnlyTransaction();
        LOG.trace("{}: Setting device state as failed {} putting operational data started.", id, tx.getIdentifier());
        tx.put(LogicalDatastoreType.OPERATIONAL, netconfNodePath(), data);
        LOG.trace("{}: Setting device state as failed {} putting operational data ended.", id, tx.getIdentifier());
        commitTransaction(tx, "update-failed-device");
    }

    public synchronized ListenableFuture<Empty> shutdown() {
        if (closeFuture != null) {
            return closeFuture;
        }

        closeFuture = SettableFuture.create();

        final var tx = txChain.newWriteOnlyTransaction();
        LOG.trace("{}: Close device state transaction {} removing all data started.", id, tx.getIdentifier());
        tx.delete(LogicalDatastoreType.OPERATIONAL, nodePath());
        LOG.trace("{}: Close device state transaction {} removing all data ended.", id, tx.getIdentifier());
        commitTransaction(tx, "close");

        txChain.close();
        return closeFuture;
    }

    private @NonNull KeyedInstanceIdentifier<Node, NodeKey> nodePath() {
        return topologyPath.child(Node.class, new NodeKey(new NodeId(id.name())));
    }

    private @NonNull InstanceIdentifier<NetconfNode> netconfNodePath() {
        return nodePath().augmentation(NetconfNodeAugment.class).child(NetconfNode.class);
    }

    private NetconfNodeBuilder newNetconfNodeBuilder(final boolean up, final NetconfDeviceCapabilities capabilities,
            final SessionIdType sessionId) {
        return new NetconfNodeBuilder()
            .setHost(id.host())
            .setPort(new PortNumber(Uint16.valueOf(id.address().getPort())))
            .setCredentials(credentials)
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
    public synchronized void onFailure(final Throwable cause) {
        LOG.warn("{}: transaction chain FAILED!", id, cause);
        if (closeFuture != null) {
            closeFuture.setException(cause);
            return;
        }

        // FIXME: move this up once we have MDSAL-838 fixed
        txChain.close();

        txChain = dataBroker.createMergingTransactionChain();
        LOG.info("{}: TransactionChain reset to {}", id, txChain);
        txChain.addCallback(this);
        // FIXME: restart last update
    }

    @Override
    public synchronized void onSuccess(final Empty result) {
        LOG.trace("{}: transaction chain SUCCESSFUL", id);
        closeFuture.set(Empty.value());
    }

    private static @NonNull String extractReason(final Throwable throwable) {
        if (throwable != null) {
            final var message = throwable.getMessage();
            if (message != null) {
                return message;
            }
        }
        return "Unknown reason";
    }
}
