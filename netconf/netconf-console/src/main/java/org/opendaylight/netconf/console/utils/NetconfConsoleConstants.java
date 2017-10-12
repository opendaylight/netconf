/*
 * Copyright (c) 2016 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.console.utils;

public final class NetconfConsoleConstants {

    private NetconfConsoleConstants() {
        throw new IllegalStateException("Instantiating utility class.");
    }

    public static final String STATUS = "Status";

    public static final String NETCONF_PORT = "NETCONF Port";

    public static final String NETCONF_IP = "NETCONF IP";

    public static final String NETCONF_ID = "NETCONF ID";

    public static final String USERNAME = "username";

    public static final String PASSWORD = "password";

    public static final String TCP_ONLY = "tcp-only";

    public static final String SCHEMALESS = "schemaless";

    public static final int DEFAULT_INDEX = 0;

    public static final String AVAILABLE_CAPABILITIES = "Available Capabilities";

    public static final long DEFAULT_TIMEOUT_MILLIS = 4000;

    public static final String NETCONF_NODE_CONTROLLER = "controller-config";

}
