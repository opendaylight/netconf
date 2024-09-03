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
import java.util.concurrent.atomic.AtomicBoolean;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices;
import org.opendaylight.netconf.client.mdsal.spi.NetconfDeviceMount;
import org.opendaylight.netconf.topology.spi.NetconfNodeUtils;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SlaveSalFacade {
    private static final Logger LOG = LoggerFactory.getLogger(SlaveSalFacade.class);

    private final AtomicBoolean registered = new AtomicBoolean(false);
    private final RemoteDeviceId id;
    private final NetconfDeviceMount mount;
    private final ActorSystem actorSystem;
    private final Timeout actorResponseWaitTime;

    public SlaveSalFacade(final RemoteDeviceId id,
                          final ActorSystem actorSystem,
                          final Timeout actorResponseWaitTime,
                          final DOMMountPointService mountPointService) {
        this.id = id;
        this.actorSystem = actorSystem;
        this.actorResponseWaitTime = actorResponseWaitTime;
        mount = new NetconfDeviceMount(id, mountPointService, NetconfNodeUtils.defaultTopologyMountPath(id));
    }

    public void registerSlaveMountPoint(final EffectiveModelContext remoteSchemaContext, final ActorRef masterActorRef,
            final RemoteDeviceServices services) {
        if (!registered.compareAndSet(false, true)) {
            LOG.info("Mount point {} already registered, skipping registation", id);
            return;
        }

        final var netconfDeviceDataBroker = new ProxyDOMDataBroker(id, masterActorRef,
            actorSystem.dispatcher(), actorResponseWaitTime);
        final var proxyNetconfService = new ProxyNetconfDataTreeService(id, masterActorRef,
            actorSystem.dispatcher(), actorResponseWaitTime);

        mount.onDeviceConnected(remoteSchemaContext, services, netconfDeviceDataBroker, proxyNetconfService);
        LOG.info("{}: Slave mount point registered.", id);
    }

    public void close() {
        if (registered.compareAndSet(true, false)) {
            mount.onDeviceDisconnected();
            LOG.info("{}: Slave mount point unregistered.", id);
        }
    }
}
