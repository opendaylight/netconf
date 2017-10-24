/*
 * Copyright (c) 2017 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.util;

import java.net.InetSocketAddress;

/**
 * Configuration for NETCONF northbound.
 */
public interface NetconfConfiguration {

    /**
     * NETCONF SSH server address.
     *
     * @return NETCONF SSH server address
     */
    InetSocketAddress getSshServerAddress();

    /**
     * NETCONF TCP server address.
     *
     * @return NETCONF TCP server address.
     */
    InetSocketAddress getTcpServerAddress();

    /**
     * Private key path for NETCONF.
     *
     * @return Private key path for NETCONF.
     */
    String getPrivateKeyPath();
}
