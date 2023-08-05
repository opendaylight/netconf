/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.api;

import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.netconf.client.mdsal.NetconfDeviceSchema;

/**
 * A handler for a particular logical remote device.
 */
public interface RemoteDeviceHandler extends AutoCloseable {
    /**
     * Create a logical connection to a {@link RemoteDevice}, allocating whatever resources and performing whatever
     * actions are necessary to logically attach the remote device to node-local state.
     *
     * <p>This can include, for example, creating YANG operational datastore state, setting up a {@link DOMMountPoint}
     * with services, and similar.
     *
     * <p>Implementations of this method must not invoke {@link #close()} or cause it to be invoked.
     *
     * @param deviceSchema {@link NetconfDeviceSchema} of connected device
     * @param sessionPreferences session of device
     * @param services {@link RemoteDeviceServices} available
     * @return A {@link RemoteDeviceConnection}, which needs to be closed at some point
     */
    @NonNull RemoteDeviceConnection onDeviceConnected(NetconfDeviceSchema deviceSchema,
            NetconfSessionPreferences sessionPreferences, RemoteDeviceServices services);

    // FIXME: document this node
    void onDeviceFailed(Throwable throwable);

    @Override
    void close();
}
