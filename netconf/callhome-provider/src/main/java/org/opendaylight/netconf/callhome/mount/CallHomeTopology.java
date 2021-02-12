/*
 * Copyright (c) 2016 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.mount;

import io.netty.util.concurrent.EventExecutor;
import org.opendaylight.controller.config.threadpool.ScheduledThreadPool;
import org.opendaylight.controller.config.threadpool.ThreadPool;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.netconf.client.NetconfClientDispatcher;
import org.opendaylight.netconf.nativ.netconf.communicator.NetconfDeviceCommunicatorInitializerFactory;
import org.opendaylight.netconf.nativ.netconf.communicator.NetconfSessionPreferences;
import org.opendaylight.netconf.nativ.netconf.communicator.util.RemoteDeviceId;
import org.opendaylight.netconf.sal.connect.api.DeviceActionFactory;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceHandler;
import org.opendaylight.netconf.sal.connect.api.SchemaResourceManager;
import org.opendaylight.netconf.sal.connect.netconf.sal.NetconfDeviceSalFacade;
import org.opendaylight.netconf.sal.connect.netconf.schema.mapping.BaseNetconfSchemas;

public class CallHomeTopology extends BaseCallHomeTopology {

    public CallHomeTopology(final String topologyId, final NetconfClientDispatcher dispatcher,
            final NetconfDeviceCommunicatorInitializerFactory communicator, final EventExecutor eventExecutor,
            final ScheduledThreadPool keepaliveExecutor, final ThreadPool processingExecutor,
            final SchemaResourceManager schemaRepositoryProvider, final DataBroker dataBroker,
            final DOMMountPointService mountPointService, final BaseNetconfSchemas baseSchemas) {
        this(topologyId, dispatcher, communicator, eventExecutor, keepaliveExecutor, processingExecutor,
                schemaRepositoryProvider, dataBroker, mountPointService, baseSchemas, null);
    }

    public CallHomeTopology(final String topologyId, final NetconfClientDispatcher dispatcher,
            final NetconfDeviceCommunicatorInitializerFactory communicator, final EventExecutor eventExecutor,
            final ScheduledThreadPool keepaliveExecutor, final ThreadPool processingExecutor,
            final SchemaResourceManager schemaRepositoryProvider, final DataBroker dataBroker,
            final DOMMountPointService mountPointService, final BaseNetconfSchemas baseSchemas,
            final DeviceActionFactory deviceActionFactory) {
        super(topologyId, dispatcher, communicator, eventExecutor, keepaliveExecutor, processingExecutor,
                schemaRepositoryProvider, dataBroker, mountPointService, deviceActionFactory, baseSchemas);
    }

    @Override
    protected RemoteDeviceHandler<NetconfSessionPreferences> createSalFacade(final RemoteDeviceId id) {
        return new NetconfDeviceSalFacade(id, mountPointService, dataBroker, topologyId);
    }
}
