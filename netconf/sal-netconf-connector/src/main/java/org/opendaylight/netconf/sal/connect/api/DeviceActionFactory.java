/*
 * Copyright (c) 2018 Pantheon Technologies s.r.o. All Rights Reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.api;

import org.opendaylight.controller.md.sal.dom.api.DOMActionService;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

public interface DeviceActionFactory {

    default DOMActionService createDeviceAction(MessageTransformer<NetconfMessage> messageTransformer,
            RemoteDeviceCommunicator<NetconfMessage> listener, SchemaContext schemaContext) {
        return null;
    }
}

