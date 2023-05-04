/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.api;

import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.netconf.client.mdsal.NetconfDeviceSchema;

public interface RemoteDeviceHandler extends AutoCloseable {
    /**
     * When device connected, init new mount point with specific schema context and DOM services.
     *
     * @param deviceSchema {@link NetconfDeviceSchema} of connected device
     * @param sessionPreferences session of device
     * @param services {@link RemoteDeviceServices} available
     */
    void onDeviceConnected(NetconfDeviceSchema deviceSchema, NetconfSessionPreferences sessionPreferences,
            RemoteDeviceServices services);

    // FIXME: document this node
    void onDeviceDisconnected();

    // FIXME: document this node
    void onDeviceFailed(Throwable throwable);

    // FIXME: document this node
    void onNotification(DOMNotification domNotification);

    @Override
    void close();
}
