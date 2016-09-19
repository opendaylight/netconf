/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.impl;

import java.util.ArrayList;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceHandler;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;

public class ConnectionStatusListenerRegistration {

    private final RemoteDeviceHandler<NetconfSessionPreferences> listener;
    private final ArrayList<RemoteDeviceHandler<NetconfSessionPreferences>> connectionStatusListeners;

    public ConnectionStatusListenerRegistration(final RemoteDeviceHandler<NetconfSessionPreferences> listener,
                                                final ArrayList<RemoteDeviceHandler<NetconfSessionPreferences>> connectionStatusListeners) {
        this.listener = listener;
        this.connectionStatusListeners = connectionStatusListeners;
    }

    public void close() {
        connectionStatusListeners.remove(listener);
    }
}