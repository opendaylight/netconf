/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.api;

import org.opendaylight.netconf.nativ.netconf.communicator.NetconfSessionPreferences;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceHandler;

/**
 * Provides API for connection ODL (master) with device.
 */
// FIXME: way more documentation is needed here
public interface RemoteDeviceConnector {

    /**
     * Create device communicator and open device connection.
     *
     * @param deviceHandler Device handler
     * @throws NullPointerException if {@code deviceHandler} is null
     */
    // FIXME: this should return a resource corresponding to the device connection
    void startRemoteDeviceConnection(RemoteDeviceHandler<NetconfSessionPreferences> deviceHandler);

    /**
     * Stop device communicator.
     */
    // FIXME: see above, this should live in the returned resource
    void stopRemoteDeviceConnection();
}
