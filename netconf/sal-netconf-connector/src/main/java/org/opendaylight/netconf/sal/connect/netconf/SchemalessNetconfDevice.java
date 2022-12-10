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
import org.opendaylight.netconf.sal.connect.api.RemoteDevice;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceHandler;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceServices;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfDeviceCapabilities;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfDeviceCommunicator;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;
import org.opendaylight.netconf.sal.connect.netconf.sal.SchemalessNetconfDeviceRpc;
import org.opendaylight.netconf.sal.connect.netconf.schema.mapping.BaseNetconfSchemas;
import org.opendaylight.netconf.sal.connect.netconf.schema.mapping.BaseRpcSchemalessTransformer;
import org.opendaylight.netconf.sal.connect.netconf.schema.mapping.SchemalessMessageTransformer;
import org.opendaylight.netconf.sal.connect.util.MessageCounter;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;

public class SchemalessNetconfDevice implements RemoteDevice<NetconfDeviceCommunicator> {
    private final BaseNetconfSchemas baseSchemas;
    private final RemoteDeviceId id;
    private final RemoteDeviceHandler salFacade;
    private final SchemalessMessageTransformer messageTransformer;
    private final BaseRpcSchemalessTransformer rpcTransformer;

    public SchemalessNetconfDevice(final BaseNetconfSchemas baseSchemas, final RemoteDeviceId id,
            final RemoteDeviceHandler salFacade) {
        this.baseSchemas = requireNonNull(baseSchemas);
        this.id = id;
        this.salFacade = salFacade;
        final MessageCounter counter = new MessageCounter();
        rpcTransformer = new BaseRpcSchemalessTransformer(baseSchemas, counter);
        messageTransformer = new SchemalessMessageTransformer(counter);
    }

    @VisibleForTesting
    SchemalessNetconfDevice(final BaseNetconfSchemas baseSchemas, final RemoteDeviceId id,
            final RemoteDeviceHandler salFacade, final SchemalessMessageTransformer messageTransformer) {
        this.baseSchemas = requireNonNull(baseSchemas);
        this.id = id;
        this.salFacade = salFacade;
        final MessageCounter counter = new MessageCounter();
        rpcTransformer = new BaseRpcSchemalessTransformer(baseSchemas, counter);
        this.messageTransformer = messageTransformer;
    }

    @Override
    public void onRemoteSessionUp(final NetconfSessionPreferences remoteSessionCapabilities,
            final NetconfDeviceCommunicator netconfDeviceCommunicator) {
        salFacade.onDeviceConnected(
            // FIXME: or bound from base schema rather?
            new NetconfDeviceSchema(NetconfDeviceCapabilities.empty(),
            baseSchemas.getBaseSchema().getMountPointContext()),
            remoteSessionCapabilities, new RemoteDeviceServices(
                new SchemalessNetconfDeviceRpc(id,netconfDeviceCommunicator, rpcTransformer, messageTransformer),
                null));
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
