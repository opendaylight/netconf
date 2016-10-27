/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.singleton.impl.notifications;

import java.util.List;
import org.opendaylight.netconf.topology.singleton.impl.utils.ListenerRegistrationHolder;

/**
 * API provides notification listeners routines.
 */
public interface NetconfNotificationListenerRegistrar {

    /**
     * Register all listeners to notification service
     * @param listeners list of all saved services
     */
    void registerNotificationListeners(List<ListenerRegistrationHolder> listeners);

    /**
     * Method should invoke rpc create-subscription on ale stream names in the list
     * @param netconfDeviceRegisteredStreams list of device streams
     */
    void registerStreams(List<String> netconfDeviceRegisteredStreams);
}
