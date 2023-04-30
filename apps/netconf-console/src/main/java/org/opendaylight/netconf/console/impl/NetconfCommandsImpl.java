/*
 * Copyright (c) 2016 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.console.impl;

import static java.util.Objects.requireNonNull;

import com.google.common.base.Strings;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.WriteTransaction;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netconf.console.api.NetconfCommands;
import org.opendaylight.netconf.console.utils.NetconfConsoleConstants;
import org.opendaylight.netconf.console.utils.NetconfConsoleUtils;
import org.opendaylight.netconf.console.utils.NetconfIidFactory;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev230430.ConnectionOper.ConnectionStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev230430.connection.oper.available.capabilities.AvailableCapability;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev230430.credentials.credentials.LoginPasswordBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev221225.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev221225.NetconfNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@Component(immediate = true)
public class NetconfCommandsImpl implements NetconfCommands {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfCommandsImpl.class);

    private final DataBroker dataBroker;

    @Inject
    @Activate
    public NetconfCommandsImpl(@Reference final DataBroker db) {
        dataBroker = requireNonNull(db);
        LOG.debug("NetconfConsoleProviderImpl initialized");
    }

    @Override
    public Map<String, Map<String, String>> listDevices() {
        final Topology topology = NetconfConsoleUtils.read(LogicalDatastoreType.OPERATIONAL,
                NetconfIidFactory.NETCONF_TOPOLOGY_IID, dataBroker);
        if (topology == null) {
            return new HashMap<>();
        }
        final Map<String, Map<String, String>> netconfNodes = new HashMap<>();
        for (final Node node : topology.nonnullNode().values()) {
            final NetconfNode netconfNode = node.augmentation(NetconfNode.class);
            final Map<String, String> attributes = new HashMap<>();
            attributes.put(NetconfConsoleConstants.NETCONF_ID, node.getNodeId().getValue());
            attributes.put(NetconfConsoleConstants.NETCONF_IP,
                    netconfNode.getHost().getIpAddress().getIpv4Address().getValue());
            attributes.put(NetconfConsoleConstants.NETCONF_PORT, netconfNode.getPort().getValue().toString());
            attributes.put(NetconfConsoleConstants.STATUS, netconfNode.getConnectionStatus().name()
                    .toLowerCase(Locale.ROOT));
            netconfNodes.put(node.getNodeId().getValue(), attributes);
        }
        return netconfNodes;
    }

    @Override
    public Map<String, Map<String, List<String>>> showDevice(final String deviceIp, final String devicePort) {
        final Map<String, Map<String, List<String>>> device = new HashMap<>();
        final Node node;
        if (devicePort != null) {
            node = NetconfConsoleUtils.getNetconfNodeFromIpAndPort(deviceIp, devicePort, dataBroker);
        } else {
            node = NetconfConsoleUtils.getNetconfNodeFromId(deviceIp, dataBroker);
        }
        if (node != null) {
            final NetconfNode netconfNode = node.augmentation(NetconfNode.class);
            final Map<String, List<String>> attributes = new HashMap<>();
            attributes.put(NetconfConsoleConstants.NETCONF_ID, List.of(node.getNodeId().getValue()));
            attributes.put(NetconfConsoleConstants.NETCONF_IP,
                List.of(netconfNode.getHost().getIpAddress().getIpv4Address().getValue()));
            attributes.put(NetconfConsoleConstants.NETCONF_PORT, List.of(netconfNode.getPort().getValue().toString()));
            attributes.put(NetconfConsoleConstants.STATUS, List.of(netconfNode.getConnectionStatus().name()));
            if (netconfNode.getConnectionStatus().equals(
                ConnectionStatus.Connected)) {
                attributes.put(NetconfConsoleConstants.AVAILABLE_CAPABILITIES, netconfNode.getAvailableCapabilities()
                    .getAvailableCapability().stream()
                        .map(AvailableCapability::getCapability)
                        .collect(Collectors.toList()));
            } else {
                attributes.put(NetconfConsoleConstants.AVAILABLE_CAPABILITIES, List.of(""));
            }
            device.put(node.getNodeId().getValue(), attributes);
        }
        return device;
    }

    @Override
    public Map<String, Map<String, List<String>>> showDevice(final String deviceId) {
        final Map<String, Map<String, List<String>>> device = new HashMap<>();
        final Node node = NetconfConsoleUtils.getNetconfNodeFromId(deviceId, dataBroker);
        if (node != null) {
            final NetconfNode netconfNode = node.augmentation(NetconfNode.class);
            final Map<String, List<String>> attributes = new HashMap<>();
            attributes.put(NetconfConsoleConstants.NETCONF_ID, List.of(node.getNodeId().getValue()));
            attributes.put(NetconfConsoleConstants.NETCONF_IP,
                List.of(netconfNode.getHost().getIpAddress().getIpv4Address().getValue()));
            attributes.put(NetconfConsoleConstants.NETCONF_PORT, List.of(netconfNode.getPort().getValue().toString()));
            attributes.put(NetconfConsoleConstants.STATUS, List.of(netconfNode.getConnectionStatus().name()));
            if (netconfNode.getConnectionStatus() == ConnectionStatus.Connected) {
                attributes.put(NetconfConsoleConstants.AVAILABLE_CAPABILITIES, netconfNode.getAvailableCapabilities()
                    .getAvailableCapability().stream()
                        .map(AvailableCapability::getCapability).collect(Collectors.toList()));
            } else {
                attributes.put(NetconfConsoleConstants.AVAILABLE_CAPABILITIES, List.of(""));
            }
            device.put(node.getNodeId().getValue(), attributes);
        }
        return device;
    }

    @Override
    public void connectDevice(final NetconfNode netconfNode, final String netconfNodeId) {
        final NodeId nodeId;
        if (!Strings.isNullOrEmpty(netconfNodeId)) {
            nodeId = new NodeId(netconfNodeId);
        } else {
            nodeId = new NodeId(UUID.randomUUID().toString().replace("-", ""));
        }
        final Node node = new NodeBuilder()
                .withKey(new NodeKey(nodeId))
                .setNodeId(nodeId)
                .addAugmentation(netconfNode)
                .build();

        final WriteTransaction transaction = dataBroker.newWriteOnlyTransaction();
        transaction.put(LogicalDatastoreType.CONFIGURATION, NetconfIidFactory.netconfNodeIid(nodeId.getValue()), node);

        transaction.commit().addCallback(new FutureCallback<CommitInfo>() {
            @Override
            public void onSuccess(final CommitInfo result) {
                LOG.debug("NetconfNode={} created successfully", netconfNode);
            }

            @Override
            public void onFailure(final Throwable throwable) {
                LOG.error("Failed to created NetconfNode={}", netconfNode, throwable);
            }
        }, MoreExecutors.directExecutor());
    }

    @Override
    public boolean disconnectDevice(final String netconfNodeId) {
        final WriteTransaction transaction = dataBroker.newWriteOnlyTransaction();
        final InstanceIdentifier<Node> iid = NetconfIidFactory.netconfNodeIid(netconfNodeId);
        transaction.delete(LogicalDatastoreType.CONFIGURATION, iid);

        try {
            LOG.debug("Deleting netconf node: {}", netconfNodeId);
            transaction.commit().get();
            return true;
        } catch (final InterruptedException | ExecutionException e) {
            LOG.error("Unable to remove node with Iid {}", iid, e);
            return false;
        }
    }

    @Override
    public boolean disconnectDevice(final String deviceIp, final String devicePort) {
        final String netconfNodeId = NetconfConsoleUtils.getNetconfNodeFromIpAndPort(deviceIp, devicePort, dataBroker)
            .getNodeId().getValue();
        return disconnectDevice(netconfNodeId);
    }

    @Override
    public String updateDevice(final String netconfNodeId, final String username, final String password,
                               final Map<String, String> updated) {
        final Node node = NetconfConsoleUtils.read(LogicalDatastoreType.OPERATIONAL,
            NetconfIidFactory.netconfNodeIid(netconfNodeId), dataBroker);

        if (node != null && node.augmentation(NetconfNode.class) != null) {
            final NetconfNode netconfNode = node.augmentation(NetconfNode.class);

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

            final Node updatedNode = new NodeBuilder()
                    .withKey(node.key())
                    .setNodeId(node.getNodeId())
                    .addAugmentation(new NetconfNodeBuilder()
                        .setHost(new Host(new IpAddress(new Ipv4Address(deviceIp))))
                        .setPort(new PortNumber(Uint16.valueOf(Integer.decode(devicePort))))
                        .setTcpOnly(tcpOnly)
                        .setSchemaless(isSchemaless)
                        .setCredentials(new LoginPasswordBuilder()
                            .setUsername(newUsername)
                            .setPassword(newPassword)
                            .build())
                        .build())
                    .build();

            final WriteTransaction transaction = dataBroker.newWriteOnlyTransaction();
            transaction.put(LogicalDatastoreType.CONFIGURATION,
                    NetconfIidFactory.netconfNodeIid(updatedNode.getNodeId().getValue()), updatedNode);

            transaction.commit().addCallback(new FutureCallback<CommitInfo>() {
                @Override
                public void onSuccess(final CommitInfo result) {
                    LOG.debug("NetconfNode={} updated successfully", netconfNode);
                }

                @Override
                public void onFailure(final Throwable throwable) {
                    LOG.error("Failed to updated NetconfNode={}", netconfNode, throwable);
                }
            }, MoreExecutors.directExecutor());

            return "NETCONF node: " + netconfNodeId + " updated successfully.";
        } else {
            return "NETCONF node: " + netconfNodeId + " does not exist to update";
        }
    }
}
