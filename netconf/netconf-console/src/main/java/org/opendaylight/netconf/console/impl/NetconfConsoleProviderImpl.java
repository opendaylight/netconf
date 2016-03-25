/*
 * Copyright (c) 2016 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.console.impl;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import org.opendaylight.controller.config.persist.api.ConfigPusher;
import org.opendaylight.controller.config.persist.api.ConfigSnapshotHolder;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.ProviderContext;
import org.opendaylight.controller.sal.binding.api.BindingAwareProvider;
import org.opendaylight.netconf.console.api.NetconfConsoleProvider;
import org.opendaylight.netconf.console.utils.NetconfConsoleUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.io.CharStreams;
import com.google.common.util.concurrent.CheckedFuture;

public class NetconfConsoleProviderImpl implements BindingAwareProvider, AutoCloseable, NetconfConsoleProvider {

    private static final String AVAILABLE_CAPABILITIES = "Available Capabilities";
    private static final String STATUS = "Status";
    private static final String NETCONF_PORT = "NETCONF Port";
    private static final String NETCONF_IP = "NETCONF IP";
    private static final String NETCONF_ID = "NETCONF ID";
    private static final int DEFAULT_INDEX = 0;
    private static final long DEFAULT_TIMEOUT_MILLIS = 4000;

    private static final Logger LOG = LoggerFactory.getLogger(NetconfConsoleProviderImpl.class);

    private DataBroker dataBroker;
    private ConfigPusher configPusher;
    private ServiceRegistration<NetconfConsoleProvider> netconfConsoleRegistration;

    /**
     * Path to the XML file where the generic NETCONF connector's payload is saved
     */
    public static final String NETCONF_CONNECTOR_XML = "/connect-device.xml";

    public NetconfConsoleProviderImpl() {
        LOG.debug("NetconfConsoleProviderImpl initialized");
    }

    @Override
    public void onSessionInitiated(ProviderContext session) {
        LOG.info("NetconfConsoleProviderImpl Session Initiated");

        // Retrieve DataBroker service to interact with md-sal
        this.dataBroker =  session.getSALService(DataBroker.class);

        final BundleContext context = FrameworkUtil.getBundle(this.getClass()).getBundleContext();
        this.netconfConsoleRegistration = context.registerService(NetconfConsoleProvider.class, this, null);

        // Retrieve ConfigPusher instance from OSGi services
        final ServiceReference<?> serviceReference = context.getServiceReference(ConfigPusher.class.getName());
        this.configPusher = (ConfigPusher) context.getService(serviceReference);
    }

    @Override
    public void close() throws Exception {
        // TODO Auto-generated method stub
    }

    @Override
    public Map<String, Map<String, String>> listDevices() {
        final Topology topology = read(LogicalDatastoreType.OPERATIONAL, NetconfConsoleUtils.getNetconfTopologyIid());
        Map<String, Map<String, String>> netconfNodes = new HashMap<>();
        for (Node node : topology.getNode()) {
            final NetconfNode netconfNode = node.getAugmentation(NetconfNode.class);
            Map<String, String> attributes = new HashMap<>();
            attributes.put(NETCONF_ID, node.getNodeId().getValue());
            attributes.put(NETCONF_IP, netconfNode.getHost().getIpAddress().getIpv4Address().getValue());
            attributes.put(NETCONF_PORT, netconfNode.getPort().getValue().toString());
            attributes.put(STATUS, netconfNode.getConnectionStatus().name().toLowerCase());
//            attributes.put("connection type", ((netconfNode.isTcpOnly() != null && netconfNode.isTcpOnly()) ? "TCP" : "SSH"));
            netconfNodes.put(node.getNodeId().getValue(), attributes);
        }
        return netconfNodes;
    }

    @Override
    public Map<String, Map<String, List<String>>> showDevice(String deviceIp, String devicePort) {
        Map<String, Map<String, List<String>>> device = new HashMap<>();
        List<Node> nodeList = (devicePort != null) ? getNetconfNodeFromIp(deviceIp, devicePort) : getNetconfNodeFromIp(deviceIp);

        if (nodeList != null) {
            for (Node node : nodeList) {
                final NetconfNode netconfNode = node.getAugmentation(NetconfNode.class);
                Map<String, List<String>> attributes = new HashMap<>();
                attributes.put(NETCONF_ID, ImmutableList.of(node.getNodeId().getValue()));
                attributes.put(NETCONF_IP, ImmutableList.of(netconfNode.getHost().getIpAddress().getIpv4Address().getValue()));
                attributes.put(NETCONF_PORT, ImmutableList.of(netconfNode.getPort().getValue().toString()));
                attributes.put(STATUS, ImmutableList.of(netconfNode.getConnectionStatus().name()));
//                attributes.put("connection type", ((netconfNode.isTcpOnly() != null && netconfNode.isTcpOnly()) ? "TCP" : "SSH"));
                attributes.put(AVAILABLE_CAPABILITIES, netconfNode.getAvailableCapabilities().getAvailableCapability());
                device.put(node.getNodeId().getValue(), attributes);
            }
        }
        return device;
    }

    @Override
    public boolean connectDevice(String deviceIp, String devicePort, String username, String password, Boolean tcpOnly) {
        // create a NETCONF payload with IP/port and username/password embedded into it
        final String configBlueprint = connectDeviceNetconfPayload(deviceIp, devicePort, username, password, tcpOnly, null);
        boolean status = pushConfigs(configBlueprint);
        waitForUpdate(deviceIp);
        status = status && (getNetconfNodeFromIp(deviceIp, devicePort) != null);
        return status;
    }

    @Override
    public boolean disconnectDevice(String deviceIp, String devicePort) {
        List<Node> nodes = getNetconfNodeFromIp(deviceIp, devicePort);
        if (nodes == null) {
            LOG.debug("Nothing to delete");
            return false;
        }

        String mountId = nodes.get(DEFAULT_INDEX).getNodeId().getValue();
        try {
            LOG.debug("Deleting node: {}", mountId);
            URL url = new URL("http://localhost:8181/restconf/config/network-topology:network-topology/topology/topology-netconf"
                    + "/node/controller-config/yang-ext:mount/config:modules/module/odl-sal-netconf-connector-cfg:sal-netconf-connector/"
                    + mountId);
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

    private List<Node> getNetconfNodeFromIp(String deviceIp) {
        final Topology topology = read(LogicalDatastoreType.OPERATIONAL, NetconfConsoleUtils.getNetconfTopologyIid());
        if (topology == null || topology.getNode() == null || topology.getNode().isEmpty()) {
            return null;
        }

        List<Node> nodes = new ArrayList<>();
        for (Node node : topology.getNode()) {
            final NetconfNode netconfNode = node.getAugmentation(NetconfNode.class);
            if (netconfNode != null
                    && netconfNode.getHost().getIpAddress().getIpv4Address().getValue().equals(deviceIp)) {
                nodes.add(node);
            }
        }
        return (nodes.isEmpty()) ? null : nodes;
    }

    private List<Node> getNetconfNodeFromIp(String deviceIp, String devicePort) {
        final Topology topology = read(LogicalDatastoreType.OPERATIONAL, NetconfConsoleUtils.getNetconfTopologyIid());
        if (topology == null || topology.getNode() == null || topology.getNode().isEmpty()) {
            return null;
        }

        for (Node node : topology.getNode()) {
            final NetconfNode netconfNode = node.getAugmentation(NetconfNode.class);
            if (netconfNode != null
                    && netconfNode.getHost().getIpAddress().getIpv4Address().getValue().equals(deviceIp)
                    && devicePort.equals(netconfNode.getPort().getValue().toString()))
                    return ImmutableList.of(node);
            }
        return null;
    }

    private boolean pushConfigs(String blueprint) {
        if (blueprint == null) {
            return false;
        }

        // Build a ConfigSnapshotHolder containing NETCONF payload and empty capabilities
        final ConfigSnapshotHolder configSnapshotHolder = new ConfigSnapshotHolder() {
            @Override
            public String getConfigSnapshot() {
                return blueprint;
            }
            @Override
            public SortedSet<String> getCapabilities() {
                return new TreeSet<>();
            }
        };

        // push the payload using configpusher
        try {
            final List<ConfigSnapshotHolder> listConfigSnapshoHolder = ImmutableList.of(configSnapshotHolder);
            configPusher.pushConfigs(listConfigSnapshoHolder);
            return true;
        } catch (final Exception e) {
          LOG.error("Failed to push Netconf payload", e);
          return false;
        }
    }

    private String connectDeviceNetconfPayload(String deviceIp, String devicePort,
            String username, String password, Boolean tcpOnly, String name) {
        final InputStream stream = this.getClass().getResourceAsStream(NETCONF_CONNECTOR_XML);
        Preconditions.checkNotNull(stream, "Cannot load" + NETCONF_CONNECTOR_XML);

        String configBlueprint = null;
        try {
            configBlueprint = CharStreams.toString(new InputStreamReader(stream, Charsets.UTF_8));
            configBlueprint = configBlueprint.substring(configBlueprint.indexOf("<data"), configBlueprint.indexOf("</configuration>"));
            if (name == null || name.isEmpty()) {
                name = "netconf" + deviceIp.replace(".", "") + devicePort;
            }
            configBlueprint = String.format(configBlueprint, name, deviceIp, devicePort, username, password, tcpOnly);
        } catch (final Exception e) {
            LOG.error("Failed to create Netconf payload to connect to device", e);
        }
        return configBlueprint;
    }

    private void waitForUpdate(String deviceIp) {
        try {
            Thread.sleep(DEFAULT_TIMEOUT_MILLIS);
        } catch (final InterruptedException e) {
            LOG.warn("Interrupted while waiting after Netconf node {}", deviceIp, e);
        }
    }

    private <D extends org.opendaylight.yangtools.yang.binding.DataObject> D read(
            final LogicalDatastoreType store, final InstanceIdentifier<D> path)  {
        D result = null;
        final ReadOnlyTransaction transaction = dataBroker.newReadOnlyTransaction();
        Optional<D> optionalDataObject;
        CheckedFuture<Optional<D>, ReadFailedException> future = transaction.read(store, path);
        try {
            optionalDataObject = future.checkedGet();
            if (optionalDataObject.isPresent()) {
                result = optionalDataObject.get();
            } else {
                LOG.debug("{}: Failed to read {}", Thread.currentThread().getStackTrace()[1], path);
            }
        } catch (ReadFailedException e) {
            LOG.warn("Failed to read {} ", path, e);
        }
        transaction.close();
        return result;
    }
}