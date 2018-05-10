/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.singleton.impl;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.util.Timeout;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPointService;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;
import org.opendaylight.netconf.sal.connect.netconf.sal.NetconfDeviceNotificationService;
import org.opendaylight.netconf.sal.connect.netconf.sal.NetconfDeviceSalProvider;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SlaveSalFacade {

    private static final Logger LOG = LoggerFactory.getLogger(SlaveSalFacade.class);

    private final RemoteDeviceId id;
    private final NetconfDeviceSalProvider salProvider;
    private final ActorSystem actorSystem;
    private final Timeout actorResponseWaitTime;

    public SlaveSalFacade(final RemoteDeviceId id,
                          final ActorSystem actorSystem,
                          final Timeout actorResponseWaitTime,
                          final DOMMountPointService mountPointService) {
        this.id = id;
        this.salProvider = new NetconfDeviceSalProvider(id, mountPointService);
        this.actorSystem = actorSystem;
        this.actorResponseWaitTime = actorResponseWaitTime;
    }

    public void registerSlaveMountPoint(final SchemaContext remoteSchemaContext, final DOMRpcService deviceRpc,
                                        final ActorRef masterActorRef) {
        final NetconfDeviceNotificationService notificationService = new NetconfDeviceNotificationService();

        final ProxyDOMDataBroker netconfDeviceDataBroker =
                new ProxyDOMDataBroker(actorSystem, id, masterActorRef, actorResponseWaitTime);

        salProvider.getMountInstance().onTopologyDeviceConnected(remoteSchemaContext, netconfDeviceDataBroker,
                deviceRpc, notificationService);

        LOG.info("{}: Slave mount point registered.", id);
    }

    public void unregisterSlaveMountPoint() {
        salProvider.getMountInstance().onTopologyDeviceDisconnected();
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    public void close() {
        unregisterSlaveMountPoint();
        try {
            salProvider.getMountInstance().close();
        } catch (final Exception exception) {
            LOG.warn("{}: Exception in closing slave sal facade: {}", id, exception);
        }

        LOG.info("{}: Slave mount point unregistered.", id);
    }
}
