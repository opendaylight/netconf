/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.notifications;

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.notifications.rev120206.NetconfCapabilityChange;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.notifications.rev120206.NetconfSessionEnd;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.notifications.rev120206.NetconfSessionStart;


/**
 * Listener for base netconf notifications defined in https://tools.ietf.org/html/rfc6470.
 * This listener uses generated classes from yang model defined in RFC6470.
 * It alleviates the provisioning of base netconf notifications from the code.
 */
public interface BaseNetconfNotificationListener {

    /**
     * Callback used to notify about a change in used capabilities.
     */
    void onCapabilityChanged(NetconfCapabilityChange capabilityChange);

    /**
     * Callback used to notify about netconf session start.
     */
    void onSessionStarted(NetconfSessionStart start);

    /**
     * Callback used to notify about netconf session end.
     */
    void onSessionEnded(NetconfSessionEnd end);


    // TODO add other base notifications

}
