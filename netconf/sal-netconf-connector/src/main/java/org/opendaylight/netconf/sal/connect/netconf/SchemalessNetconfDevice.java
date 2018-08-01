/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf;

import com.google.common.annotations.VisibleForTesting;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.sal.connect.AbstractDeviceActionFactory;
import org.opendaylight.netconf.sal.connect.api.RemoteDevice;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceHandler;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfDeviceCommunicator;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;
import org.opendaylight.netconf.sal.connect.netconf.sal.SchemalessNetconfDeviceRpc;
import org.opendaylight.netconf.sal.connect.netconf.schema.mapping.ActionMessageTransformer;
import org.opendaylight.netconf.sal.connect.netconf.schema.mapping.BaseRpcSchemalessTransformer;
import org.opendaylight.netconf.sal.connect.netconf.schema.mapping.BaseSchema;
import org.opendaylight.netconf.sal.connect.netconf.schema.mapping.SchemalessMessageTransformer;
import org.opendaylight.netconf.sal.connect.util.MessageCounter;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;

public class SchemalessNetconfDevice implements
        RemoteDevice<NetconfSessionPreferences, NetconfMessage, NetconfDeviceCommunicator> {

    private RemoteDeviceId id;
    private RemoteDeviceHandler<NetconfSessionPreferences> salFacade;
    private final SchemalessMessageTransformer messageTransformer;
    private final BaseRpcSchemalessTransformer rpcTransformer;
    private final AbstractDeviceActionFactory actionFactory;
    private final ActionMessageTransformer actionMessageTransformer;

    public SchemalessNetconfDevice(final RemoteDeviceId id,
                                   final RemoteDeviceHandler<NetconfSessionPreferences> salFacade) {
        this(id, salFacade, (AbstractDeviceActionFactory)null);
    }

    public SchemalessNetconfDevice(final RemoteDeviceId id,
            final RemoteDeviceHandler<NetconfSessionPreferences> salFacade, AbstractDeviceActionFactory actionFactory) {
        this.id = id;
        this.salFacade = salFacade;
        this.actionFactory = actionFactory;
        final MessageCounter counter = new MessageCounter();
        this.rpcTransformer = new BaseRpcSchemalessTransformer(counter);
        this.messageTransformer = new SchemalessMessageTransformer(counter);
        this.actionMessageTransformer = new ActionMessageTransformer();
    }

    @VisibleForTesting
    SchemalessNetconfDevice(final RemoteDeviceId id, final RemoteDeviceHandler<NetconfSessionPreferences> salFacade,
                            final SchemalessMessageTransformer messageTransformer) {
        this.id = id;
        this.salFacade = salFacade;
        final MessageCounter counter = new MessageCounter();
        this.rpcTransformer = new BaseRpcSchemalessTransformer(counter);
        this.messageTransformer = messageTransformer;
        this.actionFactory = null;
        this.actionMessageTransformer = new ActionMessageTransformer();
    }

    @Override
    public void onRemoteSessionUp(final NetconfSessionPreferences remoteSessionCapabilities,
                                            final NetconfDeviceCommunicator netconfDeviceCommunicator) {
        final SchemalessNetconfDeviceRpc schemalessNetconfDeviceRpc = new SchemalessNetconfDeviceRpc(id,
                netconfDeviceCommunicator, rpcTransformer, messageTransformer);

        salFacade.onDeviceConnected(BaseSchema.BASE_NETCONF_CTX.getSchemaContext(), remoteSessionCapabilities,
                schemalessNetconfDeviceRpc,
                actionFactory == null ? null : this.actionFactory.createSchemaLessDeviceAction(id,
                        netconfDeviceCommunicator, actionMessageTransformer));
    }

    @Override public void onRemoteSessionDown() {
        salFacade.onDeviceDisconnected();
    }

    @Override public void onRemoteSessionFailed(final Throwable throwable) {
        salFacade.onDeviceFailed(throwable);
    }

    @Override public void onNotification(final NetconfMessage notification) {
        salFacade.onNotification(messageTransformer.toNotification(notification));
    }
}
