/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.api;

import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.netconf.client.mdsal.NetconfDeviceSchema;

public interface RemoteDeviceHandler extends AutoCloseable {
    /**
     * When device connected, init new mount point with specific schema context and DOM services. The negotiated SSH
     * algorithms are carried alongside the connect event so that implementations which report them to the operational
     * datastore can do so atomically, without relying on a separate callback.
     *
     * @param deviceSchema {@link NetconfDeviceSchema} of connected device
     * @param sessionPreferences session of device
     * @param services {@link RemoteDeviceServices} available
     * @param negotiatedSshAlg {@link NegotiatedSshAlg} negotiated on the transport session, or {@code null} if not
     *                         applicable (e.g. non-SSH transport)
     */
    void onDeviceConnected(NetconfDeviceSchema deviceSchema, NetconfSessionPreferences sessionPreferences,
            RemoteDeviceServices services, @Nullable NegotiatedSshAlg negotiatedSshAlg);

    /**
     * When device connected, init new mount point with specific schema context and DOM services. Convenience variant
     * for callers that do not have any negotiated SSH algorithms to report; delegates to
     * {@link #onDeviceConnected(NetconfDeviceSchema, NetconfSessionPreferences, RemoteDeviceServices,
     * NegotiatedSshAlg)} with a {@code null} algorithm.
     *
     * @param deviceSchema {@link NetconfDeviceSchema} of connected device
     * @param sessionPreferences session of device
     * @param services {@link RemoteDeviceServices} available
     */
    default void onDeviceConnected(final NetconfDeviceSchema deviceSchema,
            final NetconfSessionPreferences sessionPreferences, final RemoteDeviceServices services) {
        onDeviceConnected(deviceSchema, sessionPreferences, services, null);
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
