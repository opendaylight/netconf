/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.api;

import org.opendaylight.netconf.client.mdsal.NetconfDeviceCommunicator;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices.Rpcs;

/**
 * Handle initial RPCs, adding required logic to this stage of providing RPCs. It also contains the next stage handler,
 * {@link RemoteDeviceHandler}, which will handle RPCs after initialization.
 */
public interface InitialRpcsHandler {

    /**
     * Set the {@link NetconfDeviceCommunicator} that will be used to disconnect the device in case of failure.
     *
     * @param listener {@link NetconfDeviceCommunicator}
     */
    void setListener(NetconfDeviceCommunicator listener);

    /**
     * Return the underlying {@link RemoteDeviceHandler}, which provides its decorated RPCs with a user-specified
     * timeout after the device is connected.
     */
    RemoteDeviceHandler remoteDeviceHandler();

    /**
     * Wrap the given Rpcs service with the initialization logic.
     *
     *  @param service {@link Rpcs}
     */
    Rpcs decorateRpcs(Rpcs service);
}
