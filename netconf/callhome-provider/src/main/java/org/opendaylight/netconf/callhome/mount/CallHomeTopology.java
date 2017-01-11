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
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPointService;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker;
import org.opendaylight.controller.sal.core.api.Broker;
import org.opendaylight.netconf.client.NetconfClientDispatcher;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceHandler;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;
import org.opendaylight.netconf.sal.connect.netconf.sal.NetconfDeviceSalFacade;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.netconf.topology.api.SchemaRepositoryProvider;


public class CallHomeTopology extends BaseCallHomeTopology {

    public CallHomeTopology(String topologyId, NetconfClientDispatcher clientDispatcher,
            BindingAwareBroker bindingAwareBroker, Broker domBroker, EventExecutor eventExecutor,
            ScheduledThreadPool keepaliveExecutor, ThreadPool processingExecutor,
            SchemaRepositoryProvider schemaRepositoryProvider,final DataBroker dataBroker, final DOMMountPointService mountPointService) {
        super(topologyId, clientDispatcher, bindingAwareBroker, domBroker, eventExecutor, keepaliveExecutor, processingExecutor,
                schemaRepositoryProvider, dataBroker, mountPointService);
    }

    @Override
    protected RemoteDeviceHandler<NetconfSessionPreferences> createSalFacade(RemoteDeviceId id, Broker domBroker,
            BindingAwareBroker bindingBroker) {
        return new NetconfDeviceSalFacade(id, domBroker, bindingAwareBroker);
    }
}
