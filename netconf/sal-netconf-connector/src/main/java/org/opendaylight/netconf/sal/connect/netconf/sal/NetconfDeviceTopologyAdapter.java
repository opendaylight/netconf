/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.connect.netconf.sal;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.opendaylight.controller.md.sal.binding.api.BindingTransactionChain;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfDeviceCapabilities;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.network.rev180226.NetworkId;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.network.rev180226.Networks;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.network.rev180226.NetworksBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.network.rev180226.NodeId;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.network.rev180226.networks.Network;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.network.rev180226.networks.NetworkBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.network.rev180226.networks.NetworkKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.network.rev180226.networks.network.Node;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.network.rev180226.networks.network.NodeBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.network.rev180226.networks.network.NodeKey;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.networks.network.network.types.TopologyNetconf;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.KeyedInstanceIdentifier;
import org.opendaylight.yangtools.yang.common.QName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NetconfDeviceTopologyAdapter implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(NetconfDeviceTopologyAdapter.class);

    private final RemoteDeviceId id;
    private BindingTransactionChain txChain;

    private final InstanceIdentifier<Networks> networkTopologyPath = InstanceIdentifier.create(Networks.class);
    private final KeyedInstanceIdentifier<Network, NetworkKey> topologyListPath;
    private static final String UNKNOWN_REASON = "Unknown reason";

    NetconfDeviceTopologyAdapter(final RemoteDeviceId id, final BindingTransactionChain txChain) {
        this.id = id;
        this.txChain = Preconditions.checkNotNull(txChain);

        this.topologyListPath = networkTopologyPath
                .child(Network.class, new NetworkKey(new NetworkId(TopologyNetconf.QNAME.getLocalName())));

        initDeviceData();
    }

    private void initDeviceData() {
        final WriteTransaction writeTx = txChain.newWriteOnlyTransaction();

        createNetworkTopologyIfNotPresent(writeTx);

        final InstanceIdentifier<Node> path = id.getTopologyBindingPath();
        final NodeBuilder nodeBuilder = getNodeIdBuilder(id);
        NetconfNodeBuilder netconfNodeBuilder = new NetconfNodeBuilder();
        netconfNodeBuilder.setConnectionStatus(ConnectionStatus.Connecting);
        netconfNodeBuilder.setHost(id.getHost());
        netconfNodeBuilder.setPort(new PortNumber(id.getAddress().getPort()));
        nodeBuilder.addAugmentation(NetconfNode.class, netconfNodeBuilder.build());
        Node node = nodeBuilder.build();

        LOG.trace("{}: Init device state transaction {} putting if absent operational data started.",
                id, writeTx.getIdentifier());
        writeTx.put(LogicalDatastoreType.OPERATIONAL, path, node);
        LOG.trace("{}: Init device state transaction {} putting operational data ended.", id, writeTx.getIdentifier());
        LOG.trace("{}: Init device state transaction {} putting if absent config data started.",
                id, writeTx.getIdentifier());
        LOG.trace("{}: Init device state transaction {} putting config data ended.", id, writeTx.getIdentifier());

        commitTransaction(writeTx, "init");
    }

    public void updateDeviceData(final boolean up, final NetconfDeviceCapabilities capabilities) {
        final NetconfNode data = buildDataForNetconfNode(up, capabilities);

        final WriteTransaction writeTx = txChain.newWriteOnlyTransaction();
        LOG.trace("{}: Update device state transaction {} merging operational data started.",
                id, writeTx.getIdentifier());
        writeTx.put(LogicalDatastoreType.OPERATIONAL, id.getTopologyBindingPath().augmentation(NetconfNode.class),
            data, true);
        LOG.trace("{}: Update device state transaction {} merging operational data ended.",
                id, writeTx.getIdentifier());

        commitTransaction(writeTx, "update");
    }

    public void updateClusteredDeviceData(final boolean up, final String masterAddress,
                                          final NetconfDeviceCapabilities capabilities) {
        final NetconfNode data = buildDataForNetconfClusteredNode(up, masterAddress, capabilities);

        final WriteTransaction writeTx = txChain.newWriteOnlyTransaction();
        LOG.trace("{}: Update device state transaction {} merging operational data started.",
                id, writeTx.getIdentifier());
        writeTx.put(LogicalDatastoreType.OPERATIONAL,
                id.getTopologyBindingPath().augmentation(NetconfNode.class), data, true);
        LOG.trace("{}: Update device state transaction {} merging operational data ended.",
                id, writeTx.getIdentifier());

        commitTransaction(writeTx, "update");
    }

    public void setDeviceAsFailed(final Throwable throwable) {
        String reason = throwable != null && throwable.getMessage() != null ? throwable.getMessage() : UNKNOWN_REASON;

        final NetconfNode data = new NetconfNodeBuilder()
                .setHost(id.getHost())
                .setPort(new PortNumber(id.getAddress().getPort()))
                .setConnectionStatus(ConnectionStatus.UnableToConnect).setConnectedMessage(reason).build();

        final WriteTransaction writeTx = txChain.newWriteOnlyTransaction();
        LOG.trace(
                "{}: Setting device state as failed {} putting operational data started.",
                id, writeTx.getIdentifier());
        writeTx.put(LogicalDatastoreType.OPERATIONAL,
                id.getTopologyBindingPath().augmentation(NetconfNode.class), data, true);
        LOG.trace(
                "{}: Setting device state as failed {} putting operational data ended.",
                id, writeTx.getIdentifier());

        commitTransaction(writeTx, "update-failed-device");
    }

    private NetconfNode buildDataForNetconfNode(final boolean up, final NetconfDeviceCapabilities capabilities) {
        List<AvailableCapability> capabilityList = new ArrayList<>();
        capabilityList.addAll(capabilities.getNonModuleBasedCapabilities());
        capabilityList.addAll(capabilities.getResolvedCapabilities());

        final AvailableCapabilitiesBuilder avCapabalitiesBuilder = new AvailableCapabilitiesBuilder();
        avCapabalitiesBuilder.setAvailableCapability(capabilityList);

        final NetconfNodeBuilder netconfNodeBuilder = new NetconfNodeBuilder()
                .setHost(id.getHost())
                .setPort(new PortNumber(id.getAddress().getPort()))
                .setConnectionStatus(up ? ConnectionStatus.Connected : ConnectionStatus.Connecting)
                .setAvailableCapabilities(avCapabalitiesBuilder.build())
                .setUnavailableCapabilities(unavailableCapabilities(capabilities.getUnresolvedCapabilites()));

        return netconfNodeBuilder.build();
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
                .setPort(new PortNumber(id.getAddress().getPort()))
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

    public void removeDeviceConfiguration() {
        final WriteTransaction writeTx = txChain.newWriteOnlyTransaction();

        LOG.trace(
                "{}: Close device state transaction {} removing all data started.",
                id, writeTx.getIdentifier());
        writeTx.delete(LogicalDatastoreType.OPERATIONAL, id.getTopologyBindingPath());
        LOG.trace(
                "{}: Close device state transaction {} removing all data ended.",
                id, writeTx.getIdentifier());

        try {
            writeTx.submit().get();
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("{}: Transaction(close) {} FAILED!", id, writeTx.getIdentifier(), e);
            throw new IllegalStateException(id + "  Transaction(close) not committed correctly", e);
        }
    }

    private void createNetworkTopologyIfNotPresent(final WriteTransaction writeTx) {

        LOG.trace("{}: Merging {} container to ensure its presence", id, Networks.QNAME, writeTx.getIdentifier());
        writeTx.merge(LogicalDatastoreType.OPERATIONAL, networkTopologyPath, new NetworksBuilder().build());

        final Network topology =
                new NetworkBuilder().setNetworkId(new NetworkId(TopologyNetconf.QNAME.getLocalName())).build();
        LOG.trace("{}: Merging {} container to ensure its presence", id,
            Network.QNAME, writeTx.getIdentifier());
        writeTx.merge(LogicalDatastoreType.OPERATIONAL, topologyListPath, topology);
    }

    private void commitTransaction(final WriteTransaction transaction, final String txType) {
        LOG.trace("{}: Committing Transaction {}:{}", id, txType,
                transaction.getIdentifier());

        Futures.addCallback(transaction.submit(), new FutureCallback<Void>() {
            @Override
            public void onSuccess(final Void result) {
                LOG.trace("{}: Transaction({}) {} SUCCESSFUL", id, txType,
                        transaction.getIdentifier());
            }

            @Override
            public void onFailure(final Throwable throwable) {
                LOG.error("{}: Transaction({}) {} FAILED!", id, txType,
                        transaction.getIdentifier(), throwable);
                throw new IllegalStateException(
                        id + "  Transaction(" + txType + ") not committed correctly", throwable);
            }
        }, MoreExecutors.directExecutor());
    }

    private static NodeBuilder getNodeIdBuilder(final RemoteDeviceId id) {
        final NodeBuilder nodeBuilder = new NodeBuilder();
        nodeBuilder.withKey(new NodeKey(new NodeId(id.getName())));
        return nodeBuilder;
    }

    @Override
    public void close() throws Exception {
        removeDeviceConfiguration();
    }

    public void setTxChain(final BindingTransactionChain txChain) {
        this.txChain = Preconditions.checkNotNull(txChain);
    }
}
