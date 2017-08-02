/*
 * Copyright (c) 2016 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.console.utils;

import com.google.common.base.Optional;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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

public class NetconfConsoleUtils {

    private static final Logger LOG = LoggerFactory.getLogger(NetconfConsoleUtils.class);

    private NetconfConsoleUtils() {
        throw new IllegalStateException("Instantiating utility class.");
    }

    /**
     * Returns a list of NETCONF nodes for the IP.
     * @param deviceIp :IP address of NETCONF device
     * @param db :An instance of the {@link DataBroker}
     * @return :list on NETCONF nodes
     */
    public static List<Node> getNetconfNodeFromIp(final String deviceIp, final DataBroker db) {
        final Topology topology = read(LogicalDatastoreType.OPERATIONAL, NetconfIidFactory.NETCONF_TOPOLOGY_IID, db);
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
     * Returns the NETCONF node associated with the given nodeId.
     * @param nodeId :Id of the NETCONF device
     * @param db :An instance of the {@link DataBroker}
     * @return :list on NETCONF nodes
     */
    public static List<Node> getNetconfNodeFromId(final String nodeId, final DataBroker db) {
        final Node node = read(LogicalDatastoreType.OPERATIONAL, NetconfIidFactory.netconfNodeIid(nodeId), db);
        if (node != null) {
            return Arrays.asList(node);
        }
        return null;
    }

    /**
     * Returns a list with one NETCONF node for the IP and Port.
     * @param deviceIp :IP address of NETCONF device
     * @param devicePort :Port of NETCONF device
     * @param db :An instance of the {@link DataBroker}
     * @return :NETCONF node instance
     */
    public static Node getNetconfNodeFromIpAndPort(final String deviceIp, final String devicePort,
                                                   final DataBroker db) {
        final Topology topology = read(LogicalDatastoreType.OPERATIONAL, NetconfIidFactory.NETCONF_TOPOLOGY_IID, db);
        if (isNetconfNodesPresent(topology)) {
            for (Node node : topology.getNode()) {
                final NetconfNode netconfNode = node.getAugmentation(NetconfNode.class);
                if (netconfNode != null
                        && netconfNode.getHost().getIpAddress().getIpv4Address().getValue().equals(deviceIp)
                        && devicePort.equals(netconfNode.getPort().getValue().toString())) {
                    return node;
                }
            }
        }
        return null;
    }

    /**
     * Checks if the NETCONF topology contains nodes.
     * @param topology :NETCONF topology instance
     * @return :<code>true</code> if not empty, else, <code>false</code>
     */
    private static boolean isNetconfNodesPresent(final Topology topology) {
        return !(topology == null || topology.getNode() == null || topology.getNode().isEmpty());
    }

    /**
     * Wait for datastore to populate NETCONF data.
     * @param deviceIp :IP address of NETCONF device
     */
    public static void waitForUpdate(final String deviceIp) {
        try {
            Thread.sleep(NetconfConsoleConstants.DEFAULT_TIMEOUT_MILLIS);
        } catch (final InterruptedException e) {
            LOG.warn("Interrupted while waiting after Netconf node {}", deviceIp, e);
        }
    }

    /**
     * Blocking read transaction.
     * @param store :DatastoreType
     * @param path :InstanceIdentifier
     * @param db :An instance of the {@link DataBroker}
     * @return :data read from path
     */
    public static <D extends org.opendaylight.yangtools.yang.binding.DataObject> D read(
            final LogicalDatastoreType store, final InstanceIdentifier<D> path, final DataBroker db) {
        D result = null;
        final ReadOnlyTransaction transaction = db.newReadOnlyTransaction();
        Optional<D> optionalData;
        try {
            optionalData = transaction.read(store, path).checkedGet();
            if (optionalData.isPresent()) {
                result = optionalData.get();
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
