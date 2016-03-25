/*
 * Copyright (c) 2016 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.console.impl;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opendaylight.controller.config.persist.api.ConfigPusher;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netconf.console.api.NetconfConsoleProvider;
import org.opendaylight.netconf.console.utils.NetconfConsoleConstants;
import org.opendaylight.netconf.console.utils.NetconfConsoleUtils;
import org.opendaylight.netconf.console.utils.NetconfIidFactory;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

public class NetconfConsoleProviderImpl implements NetconfConsoleProvider {

    private static final Logger LOG = LoggerFactory.getLogger(NetconfConsoleProviderImpl.class);

    private final DataBroker dataBroker;
    private final ConfigPusher configPusher;

    public NetconfConsoleProviderImpl(DataBroker db, ConfigPusher configPusher) {
        LOG.debug("NetconfConsoleProviderImpl initialized");
        this.dataBroker = db;
        this.configPusher = configPusher;
    }

    @Override
    public Map<String, Map<String, String>> listDevices() {
        final Topology topology = NetconfConsoleUtils.read(LogicalDatastoreType.OPERATIONAL, NetconfIidFactory.getNetconfTopologyIid(), dataBroker);
        Map<String, Map<String, String>> netconfNodes = new HashMap<>();
        for (Node node : topology.getNode()) {
            final NetconfNode netconfNode = node.getAugmentation(NetconfNode.class);
            Map<String, String> attributes = new HashMap<>();
            attributes.put(NetconfConsoleConstants.NETCONF_ID, node.getNodeId().getValue());
            attributes.put(NetconfConsoleConstants.NETCONF_IP, netconfNode.getHost().getIpAddress().getIpv4Address().getValue());
            attributes.put(NetconfConsoleConstants.NETCONF_PORT, netconfNode.getPort().getValue().toString());
            attributes.put(NetconfConsoleConstants.STATUS, netconfNode.getConnectionStatus().name().toLowerCase());
//            attributes.put("connection type", ((netconfNode.isTcpOnly() != null && netconfNode.isTcpOnly()) ? "TCP" : "SSH"));
            netconfNodes.put(node.getNodeId().getValue(), attributes);
        }
        return netconfNodes;
    }

    @Override
    public Map<String, Map<String, List<String>>> showDevice(final String deviceIp, final String devicePort) {
        Map<String, Map<String, List<String>>> device = new HashMap<>();
        List<Node> nodeList = (devicePort != null) ? NetconfConsoleUtils.getNetconfNodeFromIpAndPort(deviceIp, devicePort, dataBroker)
                : NetconfConsoleUtils.getNetconfNodeFromIp(deviceIp, dataBroker);
        if (nodeList != null) {
            for (Node node : nodeList) {
                final NetconfNode netconfNode = node.getAugmentation(NetconfNode.class);
                Map<String, List<String>> attributes = new HashMap<>();
                attributes.put(NetconfConsoleConstants.NETCONF_ID, ImmutableList.of(node.getNodeId().getValue()));
                attributes.put(NetconfConsoleConstants.NETCONF_IP, ImmutableList.of(netconfNode.getHost().getIpAddress().getIpv4Address().getValue()));
                attributes.put(NetconfConsoleConstants.NETCONF_PORT, ImmutableList.of(netconfNode.getPort().getValue().toString()));
                attributes.put(NetconfConsoleConstants.STATUS, ImmutableList.of(netconfNode.getConnectionStatus().name()));
//                attributes.put("connection type", ((netconfNode.isTcpOnly() != null && netconfNode.isTcpOnly()) ? "TCP" : "SSH"));
                attributes.put(NetconfConsoleConstants.AVAILABLE_CAPABILITIES, netconfNode.getAvailableCapabilities().getAvailableCapability());
                device.put(node.getNodeId().getValue(), attributes);
            }
        }
        return device;
    }

    @Override
    public boolean connectDevice(final String deviceIp, final String devicePort,
            final String username, final String password, final Boolean tcpOnly) {
        // create a NETCONF payload with IP/port and username/password embedded into it
        final String configBlueprint = NetconfConsoleUtils.getNetconfConnectorPayload(deviceIp, devicePort, username, password, tcpOnly, null);
        boolean status = NetconfConsoleUtils.pushConfig(configBlueprint, configPusher);
        NetconfConsoleUtils.waitForUpdate(deviceIp);
        status = status && (NetconfConsoleUtils.getNetconfNodeFromIpAndPort(deviceIp, devicePort, dataBroker) != null);
        return status;
    }

    @Override
    public boolean disconnectDevice(final String deviceIp, final String devicePort) {
        List<Node> nodes = NetconfConsoleUtils.getNetconfNodeFromIpAndPort(deviceIp, devicePort, dataBroker);
        if (nodes == null) {
            LOG.debug("Nothing to delete");
            return false;
        }
        String mountId = nodes.get(NetconfConsoleConstants.DEFAULT_INDEX).getNodeId().getValue();
        try {
            LOG.debug("Deleting node: {}", mountId);
            URL url = new URL(NetconfConsoleConstants.DELETE_REST_URL + mountId);
            final HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("DELETE");
            connection.setRequestProperty( "Authorization", "Basic YWRtaW46YWRtaW4=");
            connection.setRequestProperty( "Accept", "application/xml");
            connection.setRequestProperty( "Content-Type", "application/xml");
            return connection.getResponseCode() == 200;
        } catch (Exception e) {
            LOG.warn("Failed to delete netconf connector {}", mountId, e);
        }
        return false;
    }

    @Override
    public String updateDevice(final String netconfNodeId, String username, String password, Map<String, String> updated) {
        final Node node = NetconfConsoleUtils.read(LogicalDatastoreType.OPERATIONAL, NetconfIidFactory.getNetconfNodeIid(netconfNodeId), dataBroker);

        if (node != null && node.getAugmentation(NetconfNode.class) != null) {
            final NetconfNode netconfNode = node.getAugmentation(NetconfNode.class);

            // Get NETCONF attributes to update if present else get their original values from NetconfNode instance
            final String deviceIp = Strings.isNullOrEmpty(updated.get(NetconfConsoleConstants.NETCONF_IP)) ?
                    netconfNode.getHost().getIpAddress().getIpv4Address().getValue() : updated.get(NetconfConsoleConstants.NETCONF_IP);
            final String devicePort = Strings.isNullOrEmpty(updated.get(NetconfConsoleConstants.NETCONF_PORT)) ?
                    netconfNode.getPort().getValue().toString() : updated.get(NetconfConsoleConstants.NETCONF_PORT);
            final Boolean tcpOnly = (updated.get(NetconfConsoleConstants.TCP_ONLY).equals("true")) ? true : false;
            username = Strings.isNullOrEmpty(updated.get(NetconfConsoleConstants.USERNAME)) ? updated.get(NetconfConsoleConstants.USERNAME) : username;
            password = Strings.isNullOrEmpty(updated.get(NetconfConsoleConstants.PASSWORD)) ? updated.get(NetconfConsoleConstants.PASSWORD) : password;

            final String configBlueprint = NetconfConsoleUtils.getNetconfConnectorPayload(deviceIp, devicePort, username, password, tcpOnly, netconfNodeId);
            boolean status = NetconfConsoleUtils.pushConfig(configBlueprint, configPusher);
            NetconfConsoleUtils.waitForUpdate(deviceIp);
            status = status && (NetconfConsoleUtils.getNetconfNodeFromIpAndPort(deviceIp, devicePort, dataBroker) != null);

            return status ? "NETCONF node: " + netconfNodeId + " updated successfully."
                    : "Failed to update NETCONF node: " + netconfNodeId;
        } else {
            return "NETCONF node: " + netconfNodeId + " does not exist to update";
        }
    }
}