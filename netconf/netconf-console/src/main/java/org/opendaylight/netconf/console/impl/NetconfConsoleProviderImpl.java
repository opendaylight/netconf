/*
 * Copyright (c) 2016 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.console.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import org.opendaylight.controller.config.persist.api.ConfigPusher;
import org.opendaylight.controller.config.persist.api.ConfigSnapshotHolder;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.MountPoint;
import org.opendaylight.controller.md.sal.binding.api.MountPointService;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.ProviderContext;
import org.opendaylight.controller.sal.binding.api.BindingAwareProvider;
import org.opendaylight.netconf.console.api.NetconfConsoleProvider;
import org.opendaylight.netconf.console.utils.NetconfConsoleUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNodeConnectionStatus.ConnectionStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.network.topology.topology.topology.types.TopologyNetconf;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
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

    private static final Logger LOG = LoggerFactory.getLogger(NetconfConsoleProviderImpl.class);

    private DataBroker dataBroker;
    private ServiceRegistration<NetconfConsoleProvider> netconfConsoleRegistration;
    private MountPointService mountPointService;
    private ConfigPusher configPusher;

    /**
     * Path to the XML file where th generic NETCONF connector's payload is
     * saved.
     */
    public static final String NETCONF_CONNECTOR_XML = "/connect-device.xml";

    public NetconfConsoleProviderImpl() {
        LOG.info("NetconfConsoleProviderImpl initialized");
    }

    @Override
    public void onSessionInitiated(ProviderContext session) {
        LOG.info("NetconfConsoleProviderImpl Session Initiated");

        // Retrieve DataBroker service to interact with md-sal
        this.dataBroker =  session.getSALService(DataBroker.class);

        // Retrieve MountService to interact with remote netconf devices
        this.mountPointService = session.getSALService(MountPointService.class);

        final BundleContext context = FrameworkUtil.getBundle(this.getClass()).getBundleContext();
        netconfConsoleRegistration = context.registerService(NetconfConsoleProvider.class, this, null);

        // Retrieve ConfigPusher instance from OSGi services
        final ServiceReference<?> serviceReference = context.getServiceReference(ConfigPusher.class.getName());
        this.configPusher = (ConfigPusher) context.getService(serviceReference);
    }

    @Override
    public void close() throws Exception {
        // TODO Auto-generated method stub
    }

    @Override
    public Map<String, Map<String, String>> listDevices(boolean isConfigurationDatastore) {
        final Topology topology = read(LogicalDatastoreType.OPERATIONAL, NetconfConsoleUtils.getNetconfTopologyIid());
        //TODO: maybe display other useful attributes??
        Map<String, Map<String, String>> netconfNodes = new HashMap<>();
        LOG.info("Nodes in topology:{}", topology.getNode());
        for (Node node : topology.getNode()) {
            final NetconfNode netconfNode = node.getAugmentation(NetconfNode.class);
            Map<String, String> attributes = new HashMap<>();
            attributes.put("netconf Id", node.getNodeId().getValue());
            attributes.put("netconf Ip", netconfNode.getHost().getIpAddress().getIpv4Address().getValue());
            attributes.put("netconf Port", netconfNode.getPort().getValue().toString());
            attributes.put("status", netconfNode.getConnectionStatus().name());
           LOG.info("isTcpOnly: {}", netconfNode.isTcpOnly());
//            attributes.put("connection type", ((netconfNode.isTcpOnly() != null && netconfNode.isTcpOnly()) ? "TCP" : "SSH"));
            netconfNodes.put(netconfNode.getHost().getIpAddress().getIpv4Address().getValue(), attributes);
        }
        return netconfNodes;
    }

    @Override
    public Map<String, Map<String, String>> showDevice(String deviceIp) {
        final Node node = read(LogicalDatastoreType.OPERATIONAL, NetconfConsoleUtils.getNetconfTopologyIid(deviceIp));
        if (node == null) {
            return null;
        }

        // TODO: the nodeId is not device IP, need to loop through all nodes and match against IP address
//        final Topology topology = read(LogicalDatastoreType.OPERATIONAL, NetconfConsoleUtils.getNetconfTopologyIid());
//        if (topology == null || topology.getNode() == null || topology.getNode().isEmpty()) {
//            return null;
//        }
//
//        for (Node node : topology.getNode()) {
//
//        }

        Map<String, Map<String, String>> device = new HashMap<>();
        final NetconfNode netconfNode = node.getAugmentation(NetconfNode.class);
        //TODO: add relevant information and format it in a readable way, maybe map is better datastructure for this
        Map<String, String> attributes = new HashMap<>();
        attributes.put("netconf Id", node.getNodeId().getValue());
        attributes.put("netconf Ip", netconfNode.getHost().getIpAddress().getIpv4Address().getValue());
        attributes.put("netconf Port", netconfNode.getPort().getValue().toString());
        attributes.put("status", netconfNode.getConnectionStatus().name());
        attributes.put("connection type", ((netconfNode.isTcpOnly() != null && netconfNode.isTcpOnly()) ? "TCP" : "SSH"));

//        netconfNode.getAvailableCapabilities().getAvailableCapability();
        device.put(netconfNode.getHost().getIpAddress().getIpv4Address().getValue(), attributes);
        return device;
    }

    @Override
    public boolean connectDevice(String deviceIp, String devicePort, String username, String password, Boolean tcpOnly) {
        // create a NETCONF payload with IP/port and username/password embedded into it
        final String configBlueprint = connectDeviceNetconfPayload(deviceIp, devicePort, username, password, tcpOnly);
        return pushConfigs(configBlueprint);
    }


    @Override
    public boolean disconnectDevice(String deviceIp) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean updateDevice(String deviceIp, String devicePort, String username, String password) {
        // TODO Auto-generated method stub
        return false;
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
            LOG.info("pushConfigs input: {}:{}", listConfigSnapshoHolder.get(0).getConfigSnapshot(), listConfigSnapshoHolder.get(0).getCapabilities());
            configPusher.pushConfigs(listConfigSnapshoHolder);
            return true;
        } catch (final Exception e) {
          LOG.error("Failed to push Netconf payload", e);
          return false;
        }
    }

    private String connectDeviceNetconfPayload(String deviceIp, String devicePort,
            String username, String password, Boolean tcpOnly) {
        final InputStream stream = this.getClass().getResourceAsStream(NETCONF_CONNECTOR_XML);
        Preconditions.checkNotNull(stream, "Cannot load" + NETCONF_CONNECTOR_XML);

        String configBlueprint = null;
        try {
            configBlueprint = CharStreams.toString(new InputStreamReader(stream, Charsets.UTF_8));
            configBlueprint = configBlueprint.substring(configBlueprint.indexOf("<data"), configBlueprint.indexOf("</configuration>"));
            String name = "netconf" + deviceIp.replace(".", "") + devicePort;
            configBlueprint = String.format(configBlueprint, name, deviceIp, devicePort, username, password, tcpOnly);
        } catch (final Exception e) {
            LOG.error("Failed to create Netconf payload to connect to device", e);
        }
        return configBlueprint;
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