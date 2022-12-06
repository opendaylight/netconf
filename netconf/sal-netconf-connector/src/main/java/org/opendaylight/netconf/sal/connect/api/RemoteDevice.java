/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.api;

import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfDeviceCapabilities;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;

/**
 * Remote device.
 */
public interface RemoteDevice<L extends RemoteDeviceCommunicator> {
    // FIXME: document this node
    @NonNull NetconfDeviceCapabilities onRemoteSessionUp(NetconfSessionPreferences remoteSessionCapabilities,
        L listener);

    // FIXME: document this node
    void onRemoteSessionDown();

    // FIXME: document this node
    void onRemoteSessionFailed(Throwable throwable);

    // FIXME: document this node
    void onNotification(NetconfMessage notification);
}
