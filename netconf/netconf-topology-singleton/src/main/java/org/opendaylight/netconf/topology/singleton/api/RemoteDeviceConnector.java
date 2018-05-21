/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.singleton.api;

import org.opendaylight.netconf.sal.connect.api.RemoteDeviceHandler;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;

/**
 * Provides API for connection odl (master) with device.
 */
public interface RemoteDeviceConnector {

    /**
     * Create device communicator and open device connection.
     */
    void startRemoteDeviceConnection(RemoteDeviceHandler<NetconfSessionPreferences> deviceHandler);

    /**
     * Stop device communicator.
     */
    void stopRemoteDeviceConnection();
}
