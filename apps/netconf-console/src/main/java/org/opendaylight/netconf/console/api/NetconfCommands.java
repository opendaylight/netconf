/*
 * Copyright (c) 2016 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.console.api;

import java.util.List;
import java.util.Map;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev231121.NetconfNode;

public interface NetconfCommands {
    /**
     * Returns a Hashmap with NETCONF ID as outer key and
     * inner keys representing attributes of a NETCONF device.
     * @return :Hashmap with two keys for all NETCONF devices in topology
     */
    Map<String, Map<String, String>> listDevices();

    /**
     * Returns a Hashmap with NETCONF ID as outer key and inner keys representing
     * attributes of a NETCONF device for the requested IP and Port. If port is not
     * specified, all NETCONF devices with requested IP address are returned.
     * @param deviceIp :IP address of NETCONF device
     * @param devicePort :Port of the NETCONF device
     * @return :Hashmap with two keys for the requested device IP and/or Port
     */
    Map<String, Map<String, List<String>>> showDevice(String deviceIp, String devicePort);

    /**
     * Returns a Hashmap with NETCONF ID as outer key and inner keys representing
     * attributes of a NETCONF device for the requested netconf device ID.
     * @param deviceId :Node id of NETCONF device
     * @return :Hashmap with two keys for the requested device Id
     */
    Map<String, Map<String, List<String>>> showDevice(String deviceId);

    /**
     * Add a NETCONF connector.
     * @param netconfNode :An instance of {@link NetconfNode} containing
     *     all required information
     * @param deviceId :NETCONF node ID
     */
    void connectDevice(NetconfNode netconfNode, String deviceId);

    /**
     * Disconnect a NETCONF connector.
     * @param deviceIp :IP address of NETCONF device
     * @param devicePort :Port of NETCONF device
     * @return :Status of disconnect NETCONF connector
     */
    boolean disconnectDevice(String deviceIp, String devicePort);

    /**
     * Disconnect a NETCONF connector.
     * @param deviceId :Node id of NETCONF device
     * @return :Status of disconnect NETCONF connector
     */
    boolean disconnectDevice(String deviceId);

    /**
     * Update the NETCONF device for requested values.
     * @param deviceId :NETCONF node ID
     * @param username :Username for NETCONF device
     * @param password :Password for NETCONF device
     * @param updated :HashMap of attributes to update
     * @return :Status of update NETCONF connector
     */
    String updateDevice(String deviceId, String username, String password, Map<String, String> updated);
}