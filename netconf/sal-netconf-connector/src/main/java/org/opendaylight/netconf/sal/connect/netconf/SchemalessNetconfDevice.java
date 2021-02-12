/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.nativ.netconf.communicator.NativeNetconfDeviceCommunicator;
import org.opendaylight.netconf.nativ.netconf.communicator.NetconfSessionPreferences;
import org.opendaylight.netconf.nativ.netconf.communicator.RemoteDevice;
import org.opendaylight.netconf.nativ.netconf.communicator.util.RemoteDeviceId;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceHandler;
import org.opendaylight.netconf.sal.connect.netconf.sal.SchemalessNetconfDeviceRpc;
import org.opendaylight.netconf.sal.connect.netconf.schema.mapping.BaseNetconfSchemas;
import org.opendaylight.netconf.sal.connect.netconf.schema.mapping.BaseRpcSchemalessTransformer;
import org.opendaylight.netconf.sal.connect.netconf.schema.mapping.SchemalessMessageTransformer;
import org.opendaylight.netconf.sal.connect.util.MessageCounter;

public class SchemalessNetconfDevice implements
        RemoteDevice<NetconfSessionPreferences, NetconfMessage, NativeNetconfDeviceCommunicator> {
    private final BaseNetconfSchemas baseSchemas;
    private final RemoteDeviceId id;
    private final RemoteDeviceHandler<NetconfSessionPreferences> salFacade;
    private final SchemalessMessageTransformer messageTransformer;
    private final BaseRpcSchemalessTransformer rpcTransformer;

    public SchemalessNetconfDevice(final BaseNetconfSchemas baseSchemas, final RemoteDeviceId id,
            final RemoteDeviceHandler<NetconfSessionPreferences> salFacade) {
        this.baseSchemas = requireNonNull(baseSchemas);
        this.id = id;
        this.salFacade = salFacade;
        final MessageCounter counter = new MessageCounter();
        rpcTransformer = new BaseRpcSchemalessTransformer(baseSchemas, counter);
        messageTransformer = new SchemalessMessageTransformer(counter);
    }

    @VisibleForTesting
    SchemalessNetconfDevice(final BaseNetconfSchemas baseSchemas, final RemoteDeviceId id,
            final RemoteDeviceHandler<NetconfSessionPreferences> salFacade,
            final SchemalessMessageTransformer messageTransformer) {
        this.baseSchemas = requireNonNull(baseSchemas);
        this.id = id;
        this.salFacade = salFacade;
        final MessageCounter counter = new MessageCounter();
        rpcTransformer = new BaseRpcSchemalessTransformer(baseSchemas, counter);
        this.messageTransformer = messageTransformer;
    }

    @Override
    public void onRemoteSessionUp(final NetconfSessionPreferences remoteSessionCapabilities,
            final NativeNetconfDeviceCommunicator netconfDeviceCommunicator) {
        final SchemalessNetconfDeviceRpc schemalessNetconfDeviceRpc = new SchemalessNetconfDeviceRpc(id,
                netconfDeviceCommunicator, rpcTransformer, messageTransformer);

        salFacade.onDeviceConnected(baseSchemas.getBaseSchema().getMountPointContext(), remoteSessionCapabilities,
                schemalessNetconfDeviceRpc);

    }

    @Override
    public void onRemoteSessionDown() {
        salFacade.onDeviceDisconnected();
    }

    @Override
    public void onRemoteSessionFailed(final Throwable throwable) {
        salFacade.onDeviceFailed(throwable);
    }

    @Override
    public void onNotification(final NetconfMessage notification) {
        salFacade.onNotification(messageTransformer.toNotification(notification));
    }
}
