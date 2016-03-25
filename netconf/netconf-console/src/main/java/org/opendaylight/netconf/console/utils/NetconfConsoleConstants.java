/*
 * Copyright (c) 2016 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.console.utils;

public class NetconfConsoleConstants {

    public static final String STATUS = "Status";

    public static final String NETCONF_PORT = "NETCONF Port";

    public static final String NETCONF_IP = "NETCONF IP";

    public static final String NETCONF_ID = "NETCONF ID";

    public static final int DEFAULT_INDEX = 0;

    public static final String AVAILABLE_CAPABILITIES = "Available Capabilities";

    public static final long DEFAULT_TIMEOUT_MILLIS = 4000;

    /**
     * Path to the XML file where the generic NETCONF connector's payload is saved
     */
    public static final String NETCONF_CONNECTOR_XML = "/connect-device.xml";

    public static final String DELETE_REST_URL = "http://localhost:8181/restconf/config/network-topology:network-topology"
            + "/topology/topology-netconf/node/controller-config/yang-ext:mount"
            + "/config:modules/module/odl-sal-netconf-connector-cfg:sal-netconf-connector/";

}
