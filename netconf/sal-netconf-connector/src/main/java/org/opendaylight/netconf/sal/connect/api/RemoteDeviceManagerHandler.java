/*
 * Copyright (c) 2020 OpendayLight and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.connect.api;

import org.opendaylight.mdsal.dom.api.DOMActionService;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.opendaylight.yangtools.rfc8528.data.api.MountPointContext;

/**
 * This interface provides methods which gets called by NetconfMountPointManager class when corresponding
 * device events occurs.
 */
public interface RemoteDeviceManagerHandler<PREF>  {

    default void onDeviceReconnected(String nodeId, PREF netconfSessionPreferences,
            NetconfNode node) {

    }

    default void onDeviceConnected(final String nodeId, final PREF netconfSessionPreferences,
            final DOMRpcService deviceRpc, final DOMActionService deviceAction) {

    }

    default void onDeviceConnected(String nodeId, final MountPointContext remoteSchemaContext,
            final PREF netconfSessionPreferences, final DOMRpcService deviceRpc) {

    }

    default void onDeviceDisconnected(final String nodeId) {

    }

    default void onDeviceFailed(String nodeId, Throwable throwable) {
        // DO NOTHING
    }

    default void onNotification(String id, DOMNotification domNotification) {
        // DO NOTHING
    }

    default void close(String nodeId) {

    }

}
