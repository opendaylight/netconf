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
import akka.cluster.Cluster;
import akka.util.Timeout;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.dom.api.DOMActionService;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.netconf.dom.api.NetconfDataTreeService;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceHandler;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;
import org.opendaylight.netconf.sal.connect.netconf.sal.NetconfDeviceNotificationService;
import org.opendaylight.netconf.sal.connect.netconf.sal.NetconfDeviceSalFacade;
import org.opendaylight.netconf.sal.connect.netconf.sal.NetconfDeviceSalProvider;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yangtools.rfc8528.data.api.MountPointContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class MasterSalFacade implements AutoCloseable, RemoteDeviceHandler<NetconfSessionPreferences> {
    private static final Logger LOG = LoggerFactory.getLogger(MasterSalFacade.class);

    private final RemoteDeviceId id;
    private final ActorSystem actorSystem;
    private final ActorRef masterActorRef;
    private final Timeout actorResponseWaitTime;
    private final NetconfDeviceSalProvider salProvider;
    private final NetconfDeviceSalFacade salFacade;

    MasterSalFacade(final RemoteDeviceId id, final ActorSystem actorSystem, final ActorRef masterActorRef,
            final Timeout actorResponseWaitTime, final DOMMountPointService mountPointService,
            final DataBroker dataBroker, final String topologyId) {
        this.id = id;
        this.actorSystem = actorSystem;
        this.masterActorRef = masterActorRef;
        this.actorResponseWaitTime = actorResponseWaitTime;
        salProvider = new NetconfDeviceSalProvider(id, mountPointService, dataBroker);
        salFacade = new NetconfDeviceSalFacade(id, mountPointService, dataBroker, topologyId);
    }

    @Override
    public void onDeviceConnected(final MountPointContext mountContext,
            final NetconfSessionPreferences netconfSessionPreferences, final DOMRpcService deviceRpc,
            final DOMActionService deviceAction) {
        LOG.info("Device {} connected - registering master mount point", id);
        registerMasterMountPoint(mountContext, netconfSessionPreferences, deviceRpc, deviceAction);
    }

    @Override
    public void onDeviceConnected(final MountPointContext mountContext,
            final NetconfSessionPreferences netconfSessionPreferences, final DOMRpcService deviceRpc) {
        LOG.info("Device {} connected - registering master mount point", id);
        registerMasterMountPoint(mountContext, netconfSessionPreferences, deviceRpc, null);
    }

    @Override
    public void onDeviceDisconnected() {
        LOG.info("Device {} disconnected - unregistering master mount point", id);
        salFacade.onDeviceDisconnected();
    }

    @Override
    public void onDeviceFailed(final Throwable throwable) {
        LOG.info("Device {} failed - unregistering master mount point", id);
        salFacade.onDeviceFailed(throwable);
    }

    @Override
    public void onNotification(final DOMNotification domNotification) {
        salFacade.onNotification(domNotification);
    }

    @Override
    public void close() {
        LOG.info("Device {} closed - unregistering master mount point", id);
        salFacade.close();
    }

    // TODO NETCONF-629
    private void registerMasterMountPoint(final MountPointContext mountContext,
            final NetconfSessionPreferences netconfSessionPreferences, final DOMRpcService deviceRpc,
            final DOMActionService deviceAction) {
        // register proxy mount point
        final ProxyDOMDataBroker proxyDataBroker = new ProxyDOMDataBroker(id, masterActorRef, actorSystem.dispatcher(),
                actorResponseWaitTime);
        final NetconfDataTreeService proxyNetconfService = new ProxyNetconfDataTreeService(id, masterActorRef,
                actorSystem.dispatcher(), actorResponseWaitTime);
        salProvider.getMountInstance().onTopologyDeviceConnected(mountContext.getEffectiveModelContext(),
            proxyDataBroker, proxyNetconfService, deviceRpc, new NetconfDeviceNotificationService(), deviceAction);

        // update device status to connected in operational datastore with master address
        final String masterAddress = Cluster.get(actorSystem).selfAddress().toString();
        salProvider.getTopologyDatastoreAdapter().updateClusteredDeviceData(true, masterAddress,
                netconfSessionPreferences.getNetconfDeviceCapabilities());
    }
}
