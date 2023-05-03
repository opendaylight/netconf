/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.callhome.mount;

import io.netty.channel.EventLoopGroup;
import io.netty.util.concurrent.EventExecutor;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.controller.config.threadpool.ScheduledThreadPool;
import org.opendaylight.controller.config.threadpool.ThreadPool;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.netconf.callhome.mount.tls.NetconfCallHomeTlsService;
import org.opendaylight.netconf.callhome.protocol.tls.TlsAllowedDevicesMonitor;
import org.opendaylight.netconf.client.mdsal.api.DeviceActionFactory;
import org.opendaylight.netconf.client.mdsal.api.SchemaResourceManager;
import org.opendaylight.netconf.sal.connect.netconf.schema.mapping.BaseNetconfSchemas;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;

@Component(service = { })
public final class OSGiCallHomeProvider {
    private final IetfZeroTouchCallHomeServerProvider callhomeProvider;
    private final NetconfCallHomeTlsService netconfCallHomeService;

    @Activate
    public OSGiCallHomeProvider(
            @Reference(target = "(type=global-netconf-ssh-scheduled-executor)")
                final ScheduledThreadPool keepAliveExecutor,
            @Reference(target = "(type=global-netconf-processing-executor)") final ThreadPool processingExecutor,
            @Reference(target = "(type=global-event-executor)") final EventExecutor eventExecutor,
            @Reference(target = "(type=global-boss-group)") final EventLoopGroup globalBossGroup,
            @Reference(target = "(type=global-worker-group)") final EventLoopGroup globalWorkerGroup,
            @Reference final DataBroker dataBroker, @Reference final DOMMountPointService domMountPointService,
            @Reference final AAAEncryptionService encryptionService,
            @Reference final DeviceActionFactory deviceActionFactory,
            @Reference final SchemaResourceManager schemaManager, @Reference final BaseNetconfSchemas baseSchemas,
            @Reference final TlsAllowedDevicesMonitor allowedDevicesMonitor) {
        final var callhomeDispatcher = new CallHomeMountDispatcher(eventExecutor, keepAliveExecutor, processingExecutor,
            schemaManager, baseSchemas, dataBroker, domMountPointService, encryptionService, deviceActionFactory);

        callhomeProvider = new IetfZeroTouchCallHomeServerProvider(dataBroker, callhomeDispatcher);
        netconfCallHomeService =  new NetconfCallHomeTlsService(dataBroker, allowedDevicesMonitor, callhomeDispatcher,
            globalBossGroup, globalWorkerGroup);
    }

    @Deactivate
    public void close() {
        netconfCallHomeService.close();
        callhomeProvider.close();
    }
}
