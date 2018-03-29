/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.api.util;

import com.google.common.collect.ImmutableSet;
import java.util.Set;

/**
 * These constants mark operation service factories that are auto wired with netconf endpoint
 * for config subsystem.
 */
public final class NetconfConstants {
    /*
     * TODO define marker interface in mapping-api that the serviceFactories in cofing subsystem
     * will implement so we can check for services with instanceof instead of constants
     */
    public static final String SERVICE_NAME = "name";
    public static final String CONFIG_NETCONF_CONNECTOR = "config-netconf-connector";
    public static final String NETCONF_MONITORING = "ietf-netconf-monitoring";
    public static final String NETCONF_NOTIFICATION = "ietf-netconf-notifications";

    public static final Set<String> CONFIG_SERVICE_MARKERS = ImmutableSet.of(SERVICE_NAME, CONFIG_NETCONF_CONNECTOR,
            NETCONF_MONITORING, NETCONF_NOTIFICATION);

    private NetconfConstants() {

    }
}
