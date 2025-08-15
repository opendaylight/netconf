/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal;

import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceHandler;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices.Rpcs;

public interface InitialRpcsHandler {

    /**
     * Sets the {@link NetconfDeviceCommunicator} that will be used to disconnect the device in case of failure.
     */
    void setListener(NetconfDeviceCommunicator listener);

    /**
     * Returns the underlying {@link RemoteDeviceHandler}, which provides its decorated RPCs with a user-specified
     * timeout after the device is connected.
     */
    RemoteDeviceHandler remoteDeviceHandler();

    /**
     * Wraps the given Rpcs service with the initialization logic.
     */
    Rpcs decorateRpcs(Rpcs service);
}
