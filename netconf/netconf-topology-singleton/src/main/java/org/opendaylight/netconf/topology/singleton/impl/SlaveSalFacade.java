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
import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;
import org.opendaylight.controller.sal.core.api.Broker;
import org.opendaylight.netconf.sal.connect.netconf.sal.NetconfDeviceNotificationService;
import org.opendaylight.netconf.sal.connect.netconf.sal.NetconfDeviceSalProvider;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.netconf.topology.singleton.api.NetconfDOMTransaction;
import org.opendaylight.netconf.topology.singleton.impl.tx.NetconfProxyDOMTransaction;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SlaveSalFacade {

    private static final Logger LOG = LoggerFactory.getLogger(SlaveSalFacade.class);

    private final RemoteDeviceId id;
    private final NetconfDeviceSalProvider salProvider;

    private final ActorSystem actorSystem;

    public SlaveSalFacade(final RemoteDeviceId id,
                          final Broker domBroker,
                          final ActorSystem actorSystem) {
        this.id = id;
        this.salProvider = new NetconfDeviceSalProvider(id);
        this.actorSystem = actorSystem;

        registerToSal(domBroker);
    }

    private void registerToSal(final Broker domRegistryDependency) {
        domRegistryDependency.registerProvider(salProvider);

    }

    public void registerSlaveMountPoint(final SchemaContext remoteSchemaContext, final DOMRpcService deviceRpc,
                                        final ActorRef masterActorRef) {
        final NetconfDeviceNotificationService notificationService = new NetconfDeviceNotificationService();

        final NetconfDOMTransaction proxyDOMTransactions =
                new NetconfProxyDOMTransaction(actorSystem, masterActorRef);

        final NetconfDOMDataBroker netconfDeviceDataBroker =
                new NetconfDOMDataBroker(actorSystem, id, proxyDOMTransactions);

        salProvider.getMountInstance().onTopologyDeviceConnected(remoteSchemaContext, netconfDeviceDataBroker,
                deviceRpc, notificationService);

        LOG.info("Slave mount point registered.");
    }

    public void unregisterSlaveMountPoint() {
        salProvider.getMountInstance().onTopologyDeviceDisconnected();
    }

    public void close() {
        unregisterSlaveMountPoint();
        try {
            salProvider.getMountInstance().close();
        } catch (Exception exception) {
            LOG.warn("Exception in closing slave sal facade: {}", exception);
        }

    }


}
