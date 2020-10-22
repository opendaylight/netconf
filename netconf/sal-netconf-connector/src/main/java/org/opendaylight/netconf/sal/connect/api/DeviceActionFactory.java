/*
 * Copyright (c) 2018 Pantheon Technologies s.r.o. All Rights Reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.api;

import org.opendaylight.mdsal.dom.api.DOMActionService;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.nativ.netconf.communicator.RemoteDeviceCommunicator;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

public interface DeviceActionFactory {

    /**
     * Allows user to create DOMActionService for specific device.
     *
     * @param messageTransformer - message transformer (for action in this case)
     * @param listener - allows specific service to send and receive messages to/from device
     * @param schemaContext - schema context of device
     * @return {@link DOMActionService} of specific device
     */
    default DOMActionService createDeviceAction(final MessageTransformer<NetconfMessage> messageTransformer,
            final RemoteDeviceCommunicator<NetconfMessage> listener, final SchemaContext schemaContext) {
        return null;
    }
}

