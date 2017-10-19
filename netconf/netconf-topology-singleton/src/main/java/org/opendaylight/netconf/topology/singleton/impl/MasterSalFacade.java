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
import akka.dispatch.OnComplete;
import akka.pattern.Patterns;
import akka.util.Timeout;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import java.util.List;
import java.util.stream.Collectors;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPointService;
import org.opendaylight.controller.md.sal.dom.api.DOMNotification;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceHandler;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfDeviceCapabilities;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;
import org.opendaylight.netconf.sal.connect.netconf.sal.NetconfDeviceDataBroker;
import org.opendaylight.netconf.sal.connect.netconf.sal.NetconfDeviceNotificationService;
import org.opendaylight.netconf.sal.connect.netconf.sal.NetconfDeviceSalProvider;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.netconf.topology.singleton.messages.CreateInitialMasterActorData;
import org.opendaylight.yangtools.yang.common.SimpleDateFormatUtil;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.repo.api.RevisionSourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.concurrent.Future;

class MasterSalFacade implements AutoCloseable, RemoteDeviceHandler<NetconfSessionPreferences> {

    private static final Logger LOG = LoggerFactory.getLogger(MasterSalFacade.class);

    private final RemoteDeviceId id;
    private final Timeout actorResponseWaitTime;
    private final NetconfDeviceSalProvider salProvider;
    private final ActorRef masterActorRef;
    private final ActorSystem actorSystem;

    private SchemaContext remoteSchemaContext = null;
    private NetconfSessionPreferences netconfSessionPreferences = null;
    private DOMRpcService deviceRpc = null;
    private DOMDataBroker deviceDataBroker = null;

    MasterSalFacade(final RemoteDeviceId id,
                    final ActorSystem actorSystem,
                    final ActorRef masterActorRef,
                    final Timeout actorResponseWaitTime,
                    final DOMMountPointService mountService,
                    final DataBroker dataBroker) {
        this.id = id;
        this.salProvider = new NetconfDeviceSalProvider(id, mountService, dataBroker);
        this.actorSystem = actorSystem;
        this.masterActorRef = masterActorRef;
        this.actorResponseWaitTime = actorResponseWaitTime;
    }

    @Override
    public void onDeviceConnected(final SchemaContext remoteSchemaContext,
                                  final NetconfSessionPreferences netconfSessionPreferences,
                                  final DOMRpcService deviceRpc) {
        this.remoteSchemaContext = remoteSchemaContext;
        this.netconfSessionPreferences = netconfSessionPreferences;
        this.deviceRpc = deviceRpc;

        registerMasterMountPoint();

        sendInitialDataToActor().onComplete(new OnComplete<Object>() {
            @Override
            public void onComplete(final Throwable failure, final Object success) throws Throwable {
                if (failure == null) {
                    updateDeviceData();
                    return;
                }
                throw failure;
            }
        }, actorSystem.dispatcher());

    }

    @Override
    public void onDeviceDisconnected() {
        salProvider.getTopologyDatastoreAdapter().updateOwnDeviceData(false, new NetconfDeviceCapabilities());
        unregisterMasterMountPoint();
    }

    @Override
    public void onDeviceFailed(final Throwable throwable) {
        salProvider.getTopologyDatastoreAdapter().setDeviceAsFailed(throwable);
        unregisterMasterMountPoint();
    }

    @Override
    public void onNotification(final DOMNotification domNotification) {
        salProvider.getMountInstance().publish(domNotification);
    }

    @Override
    public void close() {
        unregisterMasterMountPoint();
        closeGracefully(salProvider);
    }

    private void registerMasterMountPoint() {
        Preconditions.checkNotNull(id);
        Preconditions.checkNotNull(remoteSchemaContext,
                "Device has no remote schema context yet. Probably not fully connected.");
        Preconditions.checkNotNull(netconfSessionPreferences,
                "Device has no capabilities yet. Probably not fully connected.");

        final NetconfDeviceNotificationService notificationService = new NetconfDeviceNotificationService();

        LOG.info("{}: Creating master data broker for device", id);

        deviceDataBroker = new NetconfDeviceDataBroker(id, remoteSchemaContext, deviceRpc, netconfSessionPreferences);
        // We need to create ProxyDOMDataBroker so accessing mountpoint
        // on leader node would be same as on follower node
        final ProxyDOMDataBroker proxyDataBroker =
                new ProxyDOMDataBroker(actorSystem, id, masterActorRef, actorResponseWaitTime);
        salProvider.getMountInstance()
                .onTopologyDeviceConnected(remoteSchemaContext, proxyDataBroker, deviceRpc, notificationService);
    }

    private Future<Object> sendInitialDataToActor() {
        final List<SourceIdentifier> sourceIdentifiers =
                remoteSchemaContext.getAllModuleIdentifiers().stream().map(mi ->
                        RevisionSourceIdentifier.create(mi.getName(),
                                (SimpleDateFormatUtil.DEFAULT_DATE_REV == mi.getRevision() ? Optional.<String>absent() :
                                    Optional.of(mi.getQNameModule().getFormattedRevision()))))
                        .collect(Collectors.toList());

        // send initial data to master actor and create actor for providing it
        return Patterns.ask(masterActorRef, new CreateInitialMasterActorData(deviceDataBroker, sourceIdentifiers,
                deviceRpc), actorResponseWaitTime);
    }

    private void updateDeviceData() {
        final Cluster cluster = Cluster.get(actorSystem);
        salProvider.getTopologyDatastoreAdapter().overwriteClusteredDeviceData(true, cluster.selfAddress().toString(),
                netconfSessionPreferences.getNetconfDeviceCapabilities());
    }

    private void unregisterMasterMountPoint() {
        salProvider.getMountInstance().onTopologyDeviceDisconnected();
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    private void closeGracefully(final AutoCloseable resource) {
        if (resource != null) {
            try {
                resource.close();
            } catch (final Exception e) {
                LOG.error("{}: Ignoring exception while closing {}", id, resource, e);
            }
        }
    }

}
