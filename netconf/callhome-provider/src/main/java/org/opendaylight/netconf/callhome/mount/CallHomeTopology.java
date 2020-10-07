/*
 * Copyright (c) 2016 Brocade Communication Systems and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.mount;

import io.netty.util.concurrent.EventExecutor;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.controller.config.threadpool.ScheduledThreadPool;
import org.opendaylight.controller.config.threadpool.ThreadPool;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.netconf.client.NetconfClientDispatcher;
import org.opendaylight.netconf.sal.connect.api.DeviceActionFactory;
import org.opendaylight.netconf.sal.connect.api.MountPointManagerService;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceHandler;
import org.opendaylight.netconf.sal.connect.api.SchemaResourceManager;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;
import org.opendaylight.netconf.sal.connect.netconf.schema.mapping.BaseNetconfSchemas;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfSalFacadeType;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;

public class CallHomeTopology extends BaseCallHomeTopology {


    public CallHomeTopology(final String topologyId, final NetconfClientDispatcher clientDispatcher,
            final EventExecutor eventExecutor,
            final ScheduledThreadPool keepaliveExecutor, final ThreadPool processingExecutor,
            final SchemaResourceManager schemaRepositoryProvider,
            final DataBroker dataBroker, final DOMMountPointService mountPointService,
            final AAAEncryptionService encryptionService, final BaseNetconfSchemas baseSchemas,
            final MountPointManagerService netconfMountPointManager) {
        this(topologyId, clientDispatcher, eventExecutor,
                keepaliveExecutor, processingExecutor, schemaRepositoryProvider,
                dataBroker, mountPointService, encryptionService, baseSchemas, null, netconfMountPointManager);
    }

    public CallHomeTopology(final String topologyId, final NetconfClientDispatcher clientDispatcher,
                            final EventExecutor eventExecutor,
                            final ScheduledThreadPool keepaliveExecutor, final ThreadPool processingExecutor,
                            final SchemaResourceManager schemaRepositoryProvider,
                            final DataBroker dataBroker, final DOMMountPointService mountPointService,
                            final AAAEncryptionService encryptionService, final BaseNetconfSchemas baseSchemas,
                            final DeviceActionFactory deviceActionFactory,
                            final MountPointManagerService mountPointManagerService) {
        super(topologyId, clientDispatcher, eventExecutor,
                keepaliveExecutor, processingExecutor, schemaRepositoryProvider,
                dataBroker, mountPointService, encryptionService, deviceActionFactory,
                baseSchemas, mountPointManagerService);
    }

    @Override
    protected RemoteDeviceHandler<NetconfSessionPreferences> createSalFacade(final RemoteDeviceId id) {
        return netconfMountPointManager.getInstance(id, mountPointService, dataBroker, topologyId,
                NetconfSalFacadeType.NETCONFDEVICESALFACADE, null, null, null);
    }
}
