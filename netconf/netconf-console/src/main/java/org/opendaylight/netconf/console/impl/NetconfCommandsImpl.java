/*
 * Copyright (c) 2016 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.console.impl;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.netconf.console.api.NetconfCommands;
import org.opendaylight.netconf.console.utils.NetconfConsoleConstants;
import org.opendaylight.netconf.console.utils.NetconfConsoleUtils;
import org.opendaylight.netconf.console.utils.NetconfIidFactory;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNodeConnectionStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.connection.status.available.capabilities.AvailableCapability;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.credentials.Credentials;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.credentials.credentials.LoginPasswordBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetconfCommandsImpl implements NetconfCommands {

    private static final Logger LOG = LoggerFactory.getLogger(NetconfCommandsImpl.class);

    private final DataBroker dataBroker;

    public NetconfCommandsImpl(final DataBroker db) {
        LOG.debug("NetconfConsoleProviderImpl initialized");
        this.dataBroker = db;
    }

    @Override
    public Map<String, Map<String, String>> listDevices() {
        final Topology topology = NetconfConsoleUtils.read(LogicalDatastoreType.OPERATIONAL,
                NetconfIidFactory.NETCONF_TOPOLOGY_IID, dataBroker);
        if (topology == null) {
            return new HashMap<>();
        }
        final Map<String, Map<String, String>> netconfNodes = new HashMap<>();
        for (final Node node : topology.getNode()) {
            final NetconfNode netconfNode = node.getAugmentation(NetconfNode.class);
            final Map<String, String> attributes = new HashMap<>();
            attributes.put(NetconfConsoleConstants.NETCONF_ID, node.getNodeId().getValue());
            attributes.put(NetconfConsoleConstants.NETCONF_IP,
                    netconfNode.getHost().getIpAddress().getIpv4Address().getValue());
            attributes.put(NetconfConsoleConstants.NETCONF_PORT, netconfNode.getPort().getValue().toString());
            attributes.put(NetconfConsoleConstants.STATUS, netconfNode.getConnectionStatus().name().toLowerCase());
            netconfNodes.put(node.getNodeId().getValue(), attributes);
        }
        return netconfNodes;
    }

    @Override
    public Map<String, Map<String, List<String>>> showDevice(final String deviceIp, final String devicePort) {
        final Map<String, Map<String, List<String>>> device = new HashMap<>();
        List<Node> nodeList = new ArrayList<>();
        if (devicePort != null) {
            nodeList.add(NetconfConsoleUtils.getNetconfNodeFromIpAndPort(deviceIp, devicePort, dataBroker));
        } else {
            nodeList = NetconfConsoleUtils.getNetconfNodeFromId(deviceIp, dataBroker);
        }
        if (nodeList != null) {
            for (final Node node : nodeList) {
                if (node != null) {
                    final NetconfNode netconfNode = node.getAugmentation(NetconfNode.class);
                    final Map<String, List<String>> attributes = new HashMap<>();
                    attributes.put(NetconfConsoleConstants.NETCONF_ID, ImmutableList.of(node.getNodeId().getValue()));
                    attributes.put(NetconfConsoleConstants.NETCONF_IP,
                            ImmutableList.of(netconfNode.getHost().getIpAddress().getIpv4Address().getValue()));
                    attributes.put(NetconfConsoleConstants.NETCONF_PORT,
                            ImmutableList.of(netconfNode.getPort().getValue().toString()));
                    attributes.put(NetconfConsoleConstants.STATUS,
                            ImmutableList.of(netconfNode.getConnectionStatus().name()));
                    if (netconfNode.getConnectionStatus().equals(
                            NetconfNodeConnectionStatus.ConnectionStatus.Connected)) {
                        attributes.put(NetconfConsoleConstants.AVAILABLE_CAPABILITIES,
                                netconfNode.getAvailableCapabilities().getAvailableCapability().stream()
                                        .map(AvailableCapability::getCapability).collect(Collectors.toList()));
                    } else {
                        attributes.put(NetconfConsoleConstants.AVAILABLE_CAPABILITIES, Collections.singletonList(""));
                    }
                    device.put(node.getNodeId().getValue(), attributes);
                }
            }
        }
        return device;
    }

    @Override
    public Map<String, Map<String, List<String>>> showDevice(final String deviceId) {
        final Map<String, Map<String, List<String>>> device = new HashMap<>();
        final List<Node> nodeList = NetconfConsoleUtils.getNetconfNodeFromId(deviceId, dataBroker);
        if (nodeList != null && nodeList.size() > 0) {
            for (final Node node : nodeList) {
                final NetconfNode netconfNode = node.getAugmentation(NetconfNode.class);
                final Map<String, List<String>> attributes = new HashMap<>();
                attributes.put(NetconfConsoleConstants.NETCONF_ID, ImmutableList.of(node.getNodeId().getValue()));
                attributes.put(NetconfConsoleConstants.NETCONF_IP,
                        ImmutableList.of(netconfNode.getHost().getIpAddress().getIpv4Address().getValue()));
                attributes.put(NetconfConsoleConstants.NETCONF_PORT,
                        ImmutableList.of(netconfNode.getPort().getValue().toString()));
                attributes.put(NetconfConsoleConstants.STATUS,
                        ImmutableList.of(netconfNode.getConnectionStatus().name()));
                if (netconfNode.getConnectionStatus().equals(NetconfNodeConnectionStatus.ConnectionStatus.Connected)) {
                    attributes.put(NetconfConsoleConstants.AVAILABLE_CAPABILITIES,
                            netconfNode.getAvailableCapabilities().getAvailableCapability().stream()
                                    .map(AvailableCapability::getCapability).collect(Collectors.toList()));
                } else {
                    attributes.put(NetconfConsoleConstants.AVAILABLE_CAPABILITIES, Collections.singletonList(""));
                }
                device.put(node.getNodeId().getValue(), attributes);
            }
        }
        return device;
    }

    @Override
    public void connectDevice(NetconfNode netconfNode, String netconfNodeId) {
        final NodeId nodeId;
        if (!Strings.isNullOrEmpty(netconfNodeId)) {
            nodeId = new NodeId(netconfNodeId);
        } else {
            nodeId = new NodeId(UUID.randomUUID().toString().replace("-", ""));
        }
        final Node node = new NodeBuilder()
                .setKey(new NodeKey(nodeId))
                .setNodeId(nodeId)
                .addAugmentation(NetconfNode.class, netconfNode)
                .build();

        final WriteTransaction transaction = dataBroker.newWriteOnlyTransaction();
        transaction.put(LogicalDatastoreType.CONFIGURATION, NetconfIidFactory.netconfNodeIid(nodeId.getValue()), node);

        Futures.addCallback(transaction.submit(), new FutureCallback<Void>() {

            @Override
            public void onSuccess(Void result) {
                LOG.debug("NetconfNode={} created successfully", netconfNode);
            }

            @Override
            public void onFailure(Throwable throwable) {
                LOG.error("Failed to created NetconfNode={}", netconfNode);
                throw new RuntimeException(throwable);
            }
        }, MoreExecutors.directExecutor());
    }

    @Override
    public boolean disconnectDevice(String netconfNodeId) {
        boolean result = false;
        final WriteTransaction transaction = dataBroker.newWriteOnlyTransaction();
        InstanceIdentifier<Node> iid = NetconfIidFactory.netconfNodeIid(netconfNodeId);
        transaction.delete(LogicalDatastoreType.CONFIGURATION, iid);

        try {
            LOG.debug("Deleting netconf node: {}", netconfNodeId);
            transaction.submit().checkedGet();
            result = true;
        } catch (final TransactionCommitFailedException e) {
            LOG.error("Unable to remove node with Iid {}", iid, e);
        }
        return result;
    }

    @Override
    public boolean disconnectDevice(final String deviceIp, final String devicePort) {
        final String netconfNodeId = NetconfConsoleUtils
                .getNetconfNodeFromIpAndPort(deviceIp, devicePort, dataBroker).getNodeId().getValue();
        return disconnectDevice(netconfNodeId);
    }

    @Override
    public String updateDevice(final String netconfNodeId, String username, String password,
                               Map<String, String> updated) {
        final Node node = NetconfConsoleUtils
                .read(LogicalDatastoreType.OPERATIONAL, NetconfIidFactory.netconfNodeIid(netconfNodeId), dataBroker);

        if (node != null && node.getAugmentation(NetconfNode.class) != null) {
            final NetconfNode netconfNode = node.getAugmentation(NetconfNode.class);

            // Get NETCONF attributes to update if present else get their original values from NetconfNode instance
            final String deviceIp = Strings.isNullOrEmpty(updated.get(NetconfConsoleConstants.NETCONF_IP))
                    ? netconfNode.getHost().getIpAddress().getIpv4Address().getValue()
                    : updated.get(NetconfConsoleConstants.NETCONF_IP);
            final String devicePort = Strings.isNullOrEmpty(updated.get(NetconfConsoleConstants.NETCONF_PORT))
                    ? netconfNode.getPort().getValue().toString() : updated.get(NetconfConsoleConstants.NETCONF_PORT);
            final Boolean tcpOnly = updated.get(NetconfConsoleConstants.TCP_ONLY).equals("true");
            final Boolean isSchemaless =
                    updated.get(NetconfConsoleConstants.SCHEMALESS).equals("true");
            final String newUsername = Strings.isNullOrEmpty(updated.get(NetconfConsoleConstants.USERNAME))
                    ? updated.get(NetconfConsoleConstants.USERNAME) : username;
            final String newPassword = Strings.isNullOrEmpty(updated.get(NetconfConsoleConstants.PASSWORD))
                    ? updated.get(NetconfConsoleConstants.PASSWORD) : password;

            final Credentials credentials =
                    new LoginPasswordBuilder().setPassword(newPassword).setUsername(newUsername).build();
            final NetconfNode updatedNetconfNode = new NetconfNodeBuilder()
                    .setHost(new Host(new IpAddress(new Ipv4Address(deviceIp))))
                    .setPort(new PortNumber(Integer.decode(devicePort)))
                    .setTcpOnly(tcpOnly)
                    .setSchemaless(isSchemaless)
                    .setCredentials(credentials)
                    .build();

            final Node updatedNode = new NodeBuilder()
                    .setKey(node.getKey())
                    .setNodeId(node.getNodeId())
                    .addAugmentation(NetconfNode.class, updatedNetconfNode)
                    .build();

            final WriteTransaction transaction = dataBroker.newWriteOnlyTransaction();
            transaction.put(LogicalDatastoreType.CONFIGURATION,
                    NetconfIidFactory.netconfNodeIid(updatedNode.getNodeId().getValue()), updatedNode);

            Futures.addCallback(transaction.submit(), new FutureCallback<Void>() {

                @Override
                public void onSuccess(Void result) {
                    LOG.debug("NetconfNode={} updated successfully", netconfNode);
                }

                @Override
                public void onFailure(Throwable throwable) {
                    LOG.error("Failed to updated NetconfNode={}", netconfNode);
                    throw new RuntimeException(throwable);
                }
            }, MoreExecutors.directExecutor());

            return "NETCONF node: " + netconfNodeId + " updated successfully.";
        } else {
            return "NETCONF node: " + netconfNodeId + " does not exist to update";
        }
    }
}
