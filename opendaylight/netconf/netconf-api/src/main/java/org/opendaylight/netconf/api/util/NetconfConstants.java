/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.api.util;

import com.google.common.collect.Sets;
import java.util.Set;

/**
 * These constants mark operation service factories that are auto wired with netconf endpoint
 * for config subsystem
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

    public static final Set<String> CONFIG_SERVICE_MARKERS = Sets.newHashSet(SERVICE_NAME, CONFIG_NETCONF_CONNECTOR, NETCONF_MONITORING, NETCONF_NOTIFICATION);
    //Added as part of Bug#3895 fix.
    public static final String NOTIFICATION = "notification";
    public static final String NOTIFICATION_NAMESPACE = "urn:ietf:params:netconf:capability:notification:1.0";
    public static final String RFC3339_DATE_FORMAT_BLUEPRINT = "yyyy-MM-dd'T'HH:mm:ssXXX";
    // The format with milliseconds is a bit fragile, it cannot be used for timestamps without millis (thats why its a separate format)
    // + it might not work properly with more than 6 digits
    // TODO try to find a better solution with Java8
    public static final String RFC3339_DATE_FORMAT_WITH_MILLIS_BLUEPRINT = "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX";
    public static final String EVENT_TIME = "eventTime";
}
