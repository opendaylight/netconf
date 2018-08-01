/*
 * Copyright (c) 2018 Pantheon Technologies s.r.o. All Rights Reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect;

import org.opendaylight.controller.md.sal.dom.api.DOMActionService;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfDeviceCommunicator;
import org.opendaylight.netconf.sal.connect.netconf.schema.mapping.ActionMessageTransformer;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;

public abstract class AbstractDeviceActionFactory {

    public DOMActionService createDeviceAction() {
        return null;
    }

    public DOMActionService createSchemaLessDeviceAction(RemoteDeviceId id,
            NetconfDeviceCommunicator netconfDeviceCommunicator, ActionMessageTransformer actionMessageTransformer) {
        return null;
    }
}

