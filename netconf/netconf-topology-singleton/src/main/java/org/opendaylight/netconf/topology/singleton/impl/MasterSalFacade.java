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
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import java.util.List;
import java.util.stream.Collectors;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMNotification;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker;
import org.opendaylight.controller.sal.core.api.Broker;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceHandler;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfDeviceCapabilities;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;
import org.opendaylight.netconf.sal.connect.netconf.sal.NetconfDeviceNotificationService;
import org.opendaylight.netconf.sal.connect.netconf.sal.NetconfDeviceSalProvider;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.netconf.topology.singleton.api.NetconfDOMTransaction;
import org.opendaylight.netconf.topology.singleton.impl.tx.NetconfMasterDOMTransaction;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologyUtils;
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

    private SchemaContext remoteSchemaContext = null;
    private NetconfSessionPreferences netconfSessionPreferences = null;
    private DOMRpcService deviceRpc = null;
    private final NetconfDeviceSalProvider salProvider;

    private final ActorRef masterActorRef;
    private final ActorSystem actorSystem;
    private DOMDataBroker deviceDataBroker = null;

    MasterSalFacade(final RemoteDeviceId id,
                           final Broker domBroker,
                           final BindingAwareBroker bindingBroker,
                           final ActorSystem actorSystem,
                           final ActorRef masterActorRef) {
        this.id = id;
        this.salProvider = new NetconfDeviceSalProvider(id);
        this.actorSystem = actorSystem;
        this.masterActorRef = masterActorRef;

        registerToSal(domBroker, bindingBroker);
    }

    private void registerToSal(final Broker domRegistryDependency, final BindingAwareBroker bindingBroker) {
        // TODO: remove use of provider, there is possible directly create mount instance and
        // TODO: NetconfDeviceTopologyAdapter in constructor = less complexity

        domRegistryDependency.registerProvider(salProvider);
        bindingBroker.registerProvider(salProvider);
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
        salProvider.getTopologyDatastoreAdapter().updateDeviceData(false, new NetconfDeviceCapabilities());
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

        LOG.info("Creating master data broker for device {}", id);

        final NetconfDOMTransaction masterDOMTransactions =
                new NetconfMasterDOMTransaction(id, remoteSchemaContext, deviceRpc, netconfSessionPreferences);
        deviceDataBroker =
                new NetconfDOMDataBroker(actorSystem, id, masterDOMTransactions);
        salProvider.getMountInstance()
                .onTopologyDeviceConnected(remoteSchemaContext, deviceDataBroker, deviceRpc, notificationService);
    }

    private Future<Object> sendInitialDataToActor() {
        final List<SourceIdentifier> sourceIdentifiers =
                remoteSchemaContext.getAllModuleIdentifiers().stream().map(mi ->
                        RevisionSourceIdentifier.create(mi.getName(),
                            (SimpleDateFormatUtil.DEFAULT_DATE_REV == mi.getRevision() ? Optional.<String>absent() :
                                    Optional.of(SimpleDateFormatUtil.getRevisionFormat().format(mi.getRevision())))))
                        .collect(Collectors.toList());

        // send initial data to master actor and create actor for providing it
        return Patterns.ask(masterActorRef, new CreateInitialMasterActorData(deviceDataBroker, sourceIdentifiers),
                NetconfTopologyUtils.TIMEOUT);
    }

    private void updateDeviceData() {
        Cluster cluster = Cluster.get(actorSystem);
        salProvider.getTopologyDatastoreAdapter().updateClusteredDeviceData(true, cluster.selfAddress().toString(),
                netconfSessionPreferences.getNetconfDeviceCapabilities());
    }

    private void unregisterMasterMountPoint() {
        salProvider.getMountInstance().onTopologyDeviceDisconnected();
    }

    private void closeGracefully(final AutoCloseable resource) {
        if (resource != null) {
            try {
                resource.close();
            } catch (final Exception e) {
                LOG.warn("{}: Ignoring exception while closing {}", id, resource, e);
            }
        }
    }

}
