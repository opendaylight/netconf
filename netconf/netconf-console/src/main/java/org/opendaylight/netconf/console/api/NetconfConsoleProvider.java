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

public interface NetconfConsoleProvider {

    /**
     * Returns a Hashmap with NETCONF ID as outer key and
     * inner keys representing attributes of a NETCONF device
     * @return :Hashmap with two keys for all NETCONF devices in topology
     */
    Map<String, Map<String, String>> listDevices();

    /**
     * Returns a Hashmap with NETCONF ID as outer key andinner keys representing
     * attributes of a NETCONF device for the requested IP and Port. If port is not
     * specified, all NETCONF devices with requested IP address are returned.
     * @param deviceIp :IP address of NETCONF device
     * @param devicePort :Port of the NETCONF device
     * @return :Hashmap with two keys for the requested device IP and/or Port
     */
    Map<String, Map<String, List<String>>> showDevice(String deviceIp, String devicePort);

    /**
     * Add a NETCONF connector
     * @param deviceIp :IP address of NETCONF device
     * @param devicePort :Port of NETCONF device
     * @param username :Username for NETCONF device
     * @param password :Password for NETCONF device
     * @param tcpOnly :Type of connection, true if tcp-only else false. False by default.
     * @return :Status of add NETCONF connector
     */
    boolean connectDevice(String deviceIp, String devicePort, String username, String password, Boolean tcpOnly);

    /**
     * Disconnect a NETCONF connector
     * @param deviceIp :IP address of NETCONF device
     * @param devicePort :Port of NETCONF device
     * @return :Status of disconnect NETCONF connector
     */
    boolean disconnectDevice(String deviceIp, String devicePort);

    /**
     * Update the NETCONF device for requested values
     * @param deviceIp :IP address of NETCONF device
     * @param devicePort :Port of NETCONF device
     * @param username :Username for NETCONF device
     * @param password :Password for NETCONF device
     * @return :Status of update NETCONF connector
     */
    // TODO
    boolean updateDevice(String deviceIp, String devicePort, String username, String password);

}
