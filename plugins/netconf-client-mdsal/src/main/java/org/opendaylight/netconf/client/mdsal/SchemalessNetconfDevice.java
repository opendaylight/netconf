/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal;

import static java.util.Objects.requireNonNull;

import org.opendaylight.netconf.api.messages.NetconfMessage;
import org.opendaylight.netconf.client.mdsal.api.BaseNetconfSchemaProvider;
import org.opendaylight.netconf.client.mdsal.api.NetconfSessionPreferences;
import org.opendaylight.netconf.client.mdsal.api.RemoteDevice;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceConnection;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceHandler;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices;
import org.opendaylight.netconf.client.mdsal.impl.BaseRpcSchemalessTransformer;
import org.opendaylight.netconf.client.mdsal.impl.MessageCounter;
import org.opendaylight.netconf.client.mdsal.impl.SchemalessMessageTransformer;
import org.opendaylight.netconf.client.mdsal.spi.SchemalessNetconfDeviceRpc;

public class SchemalessNetconfDevice implements RemoteDevice<NetconfDeviceCommunicator> {
    private final BaseNetconfSchemaProvider baseSchemas;
    private final RemoteDeviceId id;
    private final RemoteDeviceHandler salFacade;

    private SchemalessMessageTransformer messageTransformer;
    private RemoteDeviceConnection connection;

    public SchemalessNetconfDevice(final BaseNetconfSchemaProvider baseSchemas, final RemoteDeviceId id,
            final RemoteDeviceHandler salFacade) {
        this.baseSchemas = requireNonNull(baseSchemas);
        this.id = requireNonNull(id);
        this.salFacade = requireNonNull(salFacade);
    }

    @Override
    public void onRemoteSessionUp(final NetconfSessionPreferences remoteSessionCapabilities,
            final NetconfDeviceCommunicator netconfDeviceCommunicator) {
        final var baseSchema = baseSchemas.baseSchemaForCapabilities(remoteSessionCapabilities);
        final var mountContext = baseSchema.mountPointContext();

        final var counter = new MessageCounter();
        final var rpcTransformer = new BaseRpcSchemalessTransformer(baseSchema, counter);
        messageTransformer = new SchemalessMessageTransformer(counter);

        connection = salFacade.onDeviceConnected(
            // FIXME: or bound from base schema rather?
            new NetconfDeviceSchema(NetconfDeviceCapabilities.empty(), mountContext), remoteSessionCapabilities,
            new RemoteDeviceServices(
                new SchemalessNetconfDeviceRpc(id, netconfDeviceCommunicator, rpcTransformer, messageTransformer),
                null));
    }

    @Override
    public void onRemoteSessionDown() {
        connection.close();
        salFacade.close();
        messageTransformer = null;
    }

    @Override
    public void onNotification(final NetconfMessage notification) {
        connection.onNotification(messageTransformer.toNotification(notification));
    }
}
