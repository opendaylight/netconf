/*
 * Copyright (c) 2016 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.console.utils;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import org.opendaylight.controller.config.persist.api.ConfigPusher;
import org.opendaylight.controller.config.persist.api.ConfigSnapshotHolder;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.io.CharStreams;

public class NetconfConsoleUtils {

    private static final Logger LOG = LoggerFactory.getLogger(NetconfConsoleUtils.class);

    private NetconfConsoleUtils() {
        throw new AssertionError("Instantiating utility class.");
    }

    /**
     * Returns a list of NETCONF nodes for the IP
     * @param deviceIp :IP address of NETCONF device
     * @return :list on NETCONF nodes
     */
    public static List<Node> getNetconfNodeFromIp(String deviceIp, DataBroker db) {
        final Topology topology = read(LogicalDatastoreType.OPERATIONAL, NetconfIidFactory.getNetconfTopologyIid(), db);
        List<Node> nodes = new ArrayList<>();
        if (isNetconfNodesPresent(topology)) {
            for (Node node : topology.getNode()) {
                final NetconfNode netconfNode = node.getAugmentation(NetconfNode.class);
                if (netconfNode != null
                        && netconfNode.getHost().getIpAddress().getIpv4Address().getValue().equals(deviceIp)) {
                    nodes.add(node);
                }
            }
        }
        return (nodes.isEmpty()) ? null : nodes;
    }

    /**
     * Returns a list with one NETCONF node for the IP and Port
     * @param deviceIp :IP address of NETCONF device
     * @param devicePort :Port of NETCONF device
     * @return :list with one NETCONF node
     */
    public static List<Node> getNetconfNodeFromIpAndPort(String deviceIp, String devicePort, DataBroker db) {
        final Topology topology = read(LogicalDatastoreType.OPERATIONAL, NetconfIidFactory.getNetconfTopologyIid(), db);
        if (isNetconfNodesPresent(topology)) {
            for (Node node : topology.getNode()) {
                final NetconfNode netconfNode = node.getAugmentation(NetconfNode.class);
                if (netconfNode != null
                        && netconfNode.getHost().getIpAddress().getIpv4Address().getValue().equals(deviceIp)
                        && devicePort.equals(netconfNode.getPort().getValue().toString()))
                    return ImmutableList.of(node);
            }
        }
        return null;
    }

    /**
     * Checks if the NETCONF topology contains nodes
     * @param topology :NETCONF topology instance
     * @return :<code>true</code> if not empty, else, <code>false</code>
     */
    private static boolean isNetconfNodesPresent(Topology topology) {
        if (topology == null || topology.getNode() == null || topology.getNode().isEmpty()) {
            return false;
        }
        return true;
    }
    /**
     * Push NETCONF connector payload using {@link ConfigPusher#pushConfigs(List)}
     * @param blueprint :NETCONF connector payload
     * @param configPusher :ConfigPusher instance
     * @return :<code>true</code> if succeeded, else, <code>false</code>
     */
    public static boolean pushConfig(String blueprint, ConfigPusher configPusher) {
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

    /**
     * Create the NETCONF connector's payload
     * @param deviceIp :IP address of NETCONF device
     * @param devicePort :Port of NETCONF device
     * @param username :Username for NECTONF connector
     * @param password :Password for NECTONF connector
     * @param tcpOnly :<code>true</code> if tcp-only, else, <code>false</code>
     * @param name :NETCONF nodeId is provided, null by default
     * @return :NETCONF connector payload
     */
    public static String getNetconfConnectorPayload(String deviceIp, String devicePort,
            String username, String password, Boolean tcpOnly, String name) {
        final InputStream stream = NetconfConsoleUtils.class.getResourceAsStream(NetconfConsoleConstants.NETCONF_CONNECTOR_XML);
        Preconditions.checkNotNull(stream, "Cannot load" + NetconfConsoleConstants.NETCONF_CONNECTOR_XML);

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

    /**
     * Wait for datastore to populate NETCONF data
     * @param deviceIp :IP address of NETCONF device
     */
    public static void waitForUpdate(String deviceIp) {
        try {
            Thread.sleep(NetconfConsoleConstants.DEFAULT_TIMEOUT_MILLIS);
        } catch (final InterruptedException e) {
            LOG.warn("Interrupted while waiting after Netconf node {}", deviceIp, e);
        }
    }

    /**
     * Read Topology data from md-sal
     * @param store :DatastoreType
     * @param path :InstanceIdentifier of the NETCONF topology
     * @return :NETCONF topology instance
     */
    public static Topology read(final LogicalDatastoreType store, final InstanceIdentifier<Topology> path, DataBroker db) {
        Topology result = null;
        final ReadOnlyTransaction transaction = db.newReadOnlyTransaction();
        Optional<Topology> optionalTopology;
        try {
            optionalTopology = transaction.read(store, path).checkedGet();
            if (optionalTopology.isPresent()) {
                result = optionalTopology.get();
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
