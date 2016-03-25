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

import com.google.common.collect.ImmutableList;

public class NetconfConsoleProviderImpl implements NetconfConsoleProvider {

    private static final Logger LOG = LoggerFactory.getLogger(NetconfConsoleProviderImpl.class);

    private final DataBroker dataBroker;
    private final ConfigPusher configPusher;
    private final NetconfConsoleUtils netconfConsoleUtils;

    public NetconfConsoleProviderImpl(DataBroker db, ConfigPusher configPusher) {
        LOG.debug("NetconfConsoleProviderImpl initialized");
        this.dataBroker = db;
        this.configPusher = configPusher;

        // Create an instance of utils class
        netconfConsoleUtils = new NetconfConsoleUtils(dataBroker);
    }

    @Override
    public Map<String, Map<String, String>> listDevices() {
        final Topology topology = netconfConsoleUtils.read(LogicalDatastoreType.OPERATIONAL, NetconfIidFactory.getNetconfNodeIid());
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
    public Map<String, Map<String, List<String>>> showDevice(String deviceIp, String devicePort) {
        Map<String, Map<String, List<String>>> device = new HashMap<>();
        List<Node> nodeList = (devicePort != null) ? netconfConsoleUtils.getNetconfNodeFromIp(deviceIp, devicePort)
                : netconfConsoleUtils.getNetconfNodeFromIp(deviceIp);
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
    public boolean connectDevice(String deviceIp, String devicePort, String username, String password, Boolean tcpOnly) {
        // create a NETCONF payload with IP/port and username/password embedded into it
        final String configBlueprint = netconfConsoleUtils.connectDeviceNetconfPayload(deviceIp, devicePort, username, password, tcpOnly, null);
        boolean status = NetconfConsoleUtils.pushConfigs(configBlueprint, configPusher);
        NetconfConsoleUtils.waitForUpdate(deviceIp);
        status = status && (netconfConsoleUtils.getNetconfNodeFromIp(deviceIp, devicePort) != null);
        return status;
    }

    @Override
    public boolean disconnectDevice(String deviceIp, String devicePort) {
        List<Node> nodes = netconfConsoleUtils.getNetconfNodeFromIp(deviceIp, devicePort);
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
    public boolean updateDevice(String deviceIp, String devicePort, String username, String password) {
        // TODO Auto-generated method stub
        return false;
    }
}