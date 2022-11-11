/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.api;

import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;

public interface RemoteDeviceHandler<PREF> extends AutoCloseable {

    /**
     * When device connected, init new mount point with specific schema context and DOM services.
     *
     * @param netconfSessionPreferences - session of device
     * @param deviceAccess - {@link RemoteDeviceAccess} of device
     */
    default void onDeviceConnected(final PREF netconfSessionPreferences, final RemoteDeviceAccess deviceAccess) {
        // DO NOTHING
    }

    default void onDeviceReconnected(final PREF netconfSessionPreferences, final NetconfNode node) {
        // DO NOTHING
    }

    void onDeviceDisconnected();

    void onDeviceFailed(Throwable throwable);

    void onNotification(DOMNotification domNotification);

    @Override
    void close();
}
