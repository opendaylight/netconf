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
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPointService;
import org.opendaylight.netconf.client.NetconfClientDispatcher;
import org.opendaylight.netconf.topology.AbstractNetconfTopology;
import org.opendaylight.netconf.topology.api.SchemaRepositoryProvider;

abstract class BaseCallHomeTopology extends AbstractNetconfTopology {

    protected DOMMountPointService mountPointService = null;

    BaseCallHomeTopology(final String topologyId, final NetconfClientDispatcher clientDispatcher,
                         final EventExecutor eventExecutor,
                         final ScheduledThreadPool keepaliveExecutor,
                         final ThreadPool processingExecutor,
                         final SchemaRepositoryProvider schemaRepositoryProvider,
                         final DataBroker dataBroker,
                         final DOMMountPointService mountPointService,
                         final AAAEncryptionService encryptionService) {
        super(topologyId, clientDispatcher, eventExecutor, keepaliveExecutor,
              processingExecutor, schemaRepositoryProvider, dataBroker, mountPointService,
              encryptionService);
        this.mountPointService = mountPointService;
    }
}
