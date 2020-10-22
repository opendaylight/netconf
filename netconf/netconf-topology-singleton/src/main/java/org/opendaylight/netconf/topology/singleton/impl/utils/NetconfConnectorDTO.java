/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.singleton.impl.utils;

import org.opendaylight.netconf.client.NetconfClientSessionListener;
import org.opendaylight.netconf.nativ.netconf.communicator.NativeNetconfDeviceCommunicator;
import org.opendaylight.netconf.nativ.netconf.communicator.NetconfSessionPreferences;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceHandler;

public class NetconfConnectorDTO implements AutoCloseable {

    private final NativeNetconfDeviceCommunicator communicator;
    private final RemoteDeviceHandler<NetconfSessionPreferences> facade;

    public NetconfConnectorDTO(
            final NativeNetconfDeviceCommunicator communicator,
                               final RemoteDeviceHandler<NetconfSessionPreferences> facade) {
        this.communicator = communicator;
        this.facade = facade;
    }

    public NativeNetconfDeviceCommunicator getCommunicator() {
        return communicator;
    }

    public RemoteDeviceHandler<NetconfSessionPreferences> getFacade() {
        return facade;
    }

    public NetconfClientSessionListener getSessionListener() {
        return communicator;
    }

    @Override
    public void close() {
        if (communicator != null) {
            communicator.close();
        }
        if (facade != null) {
            facade.close();
        }
    }
}