/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.api;

import org.opendaylight.mdsal.dom.api.DOMActionService;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.opendaylight.yangtools.rfc8528.data.api.MountPointContext;

public interface RemoteDeviceHandler extends AutoCloseable {
    /**
     * When device connected, init new mount point with specific schema context and DOM services.
     *
     * @param remoteSchemaContext - schema context of connected device
     * @param netconfSessionPreferences - session of device
     * @param deviceRpc - {@link DOMRpcService} of device
     */
    default void onDeviceConnected(final MountPointContext remoteSchemaContext,
            final NetconfSessionPreferences sessionPreferences, final DOMRpcService deviceRpc) {
        // DO NOTHING
    }

    /**
     * When device connected, init new mount point with specific schema context and DOM services.
     *
     * @param mountContext - MountPointContext of connected device
     * @param netconfSessionPreferences - session of device
     * @param deviceRpc - {@link DOMRpcService} of device
     * @param deviceAction - {@link DOMActionService} of device
     */
    default void onDeviceConnected(final MountPointContext mountContext,
            final NetconfSessionPreferences sessionPreferences,
            final DOMRpcService deviceRpc, final DOMActionService deviceAction) {
        // DO NOTHING
    }

    default void onDeviceReconnected(final NetconfSessionPreferences sessionPreferences, final NetconfNode node) {
        // DO NOTHING
    }

    // FIXME: document this node
    void onDeviceDisconnected();

    // FIXME: document this node
    void onDeviceFailed(Throwable throwable);

    // FIXME: document this node
    void onNotification(DOMNotification domNotification);

    @Override
    void close();
}
