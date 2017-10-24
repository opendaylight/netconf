/*
 * Copyright (c) 2017 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.util;

import io.netty.channel.local.LocalAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

/**
 * Configuration for NETCONF northbound.
 */
public interface NetconfConfiguration {

    /**
     * LocalAddress constant for NETCONF northbound.
     */
    LocalAddress NETCONF_LOCAL_ADDRESS = new LocalAddress("netconf");

    /**
     * Default timeout for NETCONF northbound connections.
     */
    long DEFAULT_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(30);

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
