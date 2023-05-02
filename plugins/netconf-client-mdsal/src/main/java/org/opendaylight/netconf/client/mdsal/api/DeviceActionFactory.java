/*
 * Copyright (c) 2018 Pantheon Technologies s.r.o. All Rights Reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.api;

import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices.Actions;

public interface DeviceActionFactory {
    /**
     * Allows user to create DOMActionService for specific device.
     *
     * @param messageTransformer - message transformer (for action in this case)
     * @param listener - allows specific service to send and receive messages to/from device
     * @return {@link Actions} of specific device
     */
    @NonNull Actions createDeviceAction(ActionTransformer messageTransformer, RemoteDeviceCommunicator listener);
}

