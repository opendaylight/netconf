/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf;

import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.sal.connect.api.RemoteDevice;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceHandler;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfDeviceCommunicator;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;
import org.opendaylight.netconf.sal.connect.netconf.sal.SchemalessNetconfDeviceRpc;
import org.opendaylight.netconf.sal.connect.netconf.schema.mapping.BaseSchema;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;

public class SchemalessNetconfDevice implements
        RemoteDevice<NetconfSessionPreferences, NetconfMessage, NetconfDeviceCommunicator> {

    private RemoteDeviceId id;
    private RemoteDeviceHandler<NetconfSessionPreferences> salFacade;

    public SchemalessNetconfDevice(final RemoteDeviceId id,
                                   final RemoteDeviceHandler<NetconfSessionPreferences> salFacade) {
        this.id = id;
        this.salFacade = salFacade;
    }

    @Override public void onRemoteSessionUp(final NetconfSessionPreferences remoteSessionCapabilities,
                                            final NetconfDeviceCommunicator netconfDeviceCommunicator) {

        final SchemalessNetconfDeviceRpc schemalessNetconfDeviceRpc = new SchemalessNetconfDeviceRpc(id,
                netconfDeviceCommunicator);

        salFacade.onDeviceConnected(BaseSchema.BASE_NETCONF_CTX.getSchemaContext(),
                remoteSessionCapabilities, schemalessNetconfDeviceRpc);

    }

    @Override public void onRemoteSessionDown() {
        salFacade.onDeviceDisconnected();
    }

    @Override public void onRemoteSessionFailed(final Throwable throwable) {
        salFacade.onDeviceFailed(throwable);
    }

    @Override public void onNotification(final NetconfMessage notification) {
        // TODO support for notifications
    }
}
