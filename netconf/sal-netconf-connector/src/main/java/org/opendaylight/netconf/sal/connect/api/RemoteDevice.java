/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.api;

import org.opendaylight.netconf.api.NetconfMessage;

/**
 * Remote device.
 */
public interface RemoteDevice<PREF, LISTENER extends RemoteDeviceCommunicator> {
    // FIXME: document this node
    void onRemoteSessionUp(PREF remoteSessionCapabilities, LISTENER listener);

    // FIXME: document this node
    void onRemoteSessionDown();

    // FIXME: document this node
    void onRemoteSessionFailed(Throwable throwable);

    // FIXME: document this node
    void onNotification(NetconfMessage notification);
}
