/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl;

import static java.util.Objects.requireNonNull;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.cluster.Cluster;
import akka.dispatch.OnComplete;
import akka.pattern.Patterns;
import akka.util.Timeout;
import java.util.List;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.netconf.client.mdsal.NetconfDeviceCapabilities;
import org.opendaylight.netconf.client.mdsal.NetconfDeviceSchema;
import org.opendaylight.netconf.client.mdsal.api.NetconfSessionPreferences;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceHandler;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices;
import org.opendaylight.netconf.client.mdsal.spi.AbstractNetconfDataTreeService;
import org.opendaylight.netconf.client.mdsal.spi.NetconfDeviceDataBroker;
import org.opendaylight.netconf.client.mdsal.spi.NetconfDeviceMount;
import org.opendaylight.netconf.dom.api.NetconfDataTreeService;
import org.opendaylight.netconf.topology.singleton.messages.CreateInitialMasterActorData;
import org.opendaylight.netconf.topology.spi.NetconfDeviceTopologyAdapter;
import org.opendaylight.netconf.topology.spi.NetconfNodeUtils;
import org.opendaylight.yangtools.yang.data.api.schema.MountPointContext;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.util.SchemaContextUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.concurrent.Future;

class MasterSalFacade implements RemoteDeviceHandler, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(MasterSalFacade.class);

    private final RemoteDeviceId id;
    private final Timeout actorResponseWaitTime;
    private final ActorRef masterActorRef;
    private final ActorSystem actorSystem;
    private final NetconfDeviceTopologyAdapter datastoreAdapter;
    private final NetconfDeviceMount mount;
    private final boolean lockDatastore;

    private NetconfDeviceSchema currentSchema = null;
    private NetconfSessionPreferences netconfSessionPreferences = null;
    private RemoteDeviceServices deviceServices = null;
    private DOMDataBroker deviceDataBroker = null;
    private NetconfDataTreeService netconfService = null;

    MasterSalFacade(final RemoteDeviceId id,
                    final ActorSystem actorSystem,
                    final ActorRef masterActorRef,
                    final Timeout actorResponseWaitTime,
                    final DOMMountPointService mountService,
                    final DataBroker dataBroker,
                    final boolean lockDatastore) {
        this.id = id;
        mount = new NetconfDeviceMount(id, mountService, NetconfNodeUtils.defaultTopologyMountPath(id));
        this.actorSystem = actorSystem;
        this.masterActorRef = masterActorRef;
        this.actorResponseWaitTime = actorResponseWaitTime;
        this.lockDatastore = lockDatastore;

        datastoreAdapter = new NetconfDeviceTopologyAdapter(dataBroker, NetconfNodeUtils.DEFAULT_TOPOLOGY_IID, id);
    }

    @Override
    public void onDeviceConnected(final NetconfDeviceSchema deviceSchema,
            final NetconfSessionPreferences sessionPreferences, final RemoteDeviceServices services) {
        currentSchema = requireNonNull(deviceSchema);
        netconfSessionPreferences = requireNonNull(sessionPreferences);
        deviceServices = requireNonNull(services);
        if (services.actions() != null) {
            LOG.debug("{}: YANG 1.1 actions are supported in clustered netconf topology, DOMActionService exposed for "
                + "the device", id);
        }

        LOG.info("Device {} connected - registering master mount point", id);

        registerMasterMountPoint();

        sendInitialDataToActor().onComplete(new OnComplete<>() {
            @Override
            public void onComplete(final Throwable failure, final Object success) {
                if (failure == null) {
                    updateDeviceData();
                    return;
                }

                LOG.error("{}: CreateInitialMasterActorData to {} failed", id, masterActorRef, failure);
            }
        }, actorSystem.dispatcher());
    }

    @Override
    public void onDeviceDisconnected() {
        LOG.info("Device {} disconnected - unregistering master mount point", id);
        datastoreAdapter.updateDeviceData(false, NetconfDeviceCapabilities.empty(), null);
        mount.onDeviceDisconnected();
    }

    @Override
    public void onDeviceFailed(final Throwable throwable) {
        datastoreAdapter.setDeviceAsFailed(throwable);
        mount.onDeviceDisconnected();
    }

    @Override
    public void onNotification(final DOMNotification domNotification) {
        mount.publish(domNotification);
    }

    @Override
    public void close() {
        datastoreAdapter.close();
        mount.close();
    }

    private void registerMasterMountPoint() {
        requireNonNull(id);

        final var mountContext = requireNonNull(currentSchema,
            "Device has no remote schema context yet. Probably not fully connected.")
            .mountContext();
        final var preferences = requireNonNull(netconfSessionPreferences,
            "Device has no capabilities yet. Probably not fully connected.");

        deviceDataBroker = newDeviceDataBroker(mountContext, preferences);
        netconfService = newNetconfDataTreeService(mountContext, preferences);

        // We need to create ProxyDOMDataBroker so accessing mountpoint
        // on leader node would be same as on follower node
        final ProxyDOMDataBroker proxyDataBroker = new ProxyDOMDataBroker(id, masterActorRef, actorSystem.dispatcher(),
            actorResponseWaitTime);
        final NetconfDataTreeService proxyNetconfService = new ProxyNetconfDataTreeService(id, masterActorRef,
            actorSystem.dispatcher(), actorResponseWaitTime);
        mount.onDeviceConnected(mountContext.getEffectiveModelContext(), deviceServices,
            proxyDataBroker, proxyNetconfService);
    }

    protected DOMDataBroker newDeviceDataBroker(final MountPointContext mountContext,
            final NetconfSessionPreferences preferences) {
        return new NetconfDeviceDataBroker(id, mountContext, deviceServices.rpcs(), preferences, lockDatastore);
    }

    protected NetconfDataTreeService newNetconfDataTreeService(final MountPointContext mountContext,
            final NetconfSessionPreferences preferences) {
        return AbstractNetconfDataTreeService.of(id, mountContext, deviceServices.rpcs(), preferences, lockDatastore);
    }

    private Future<Object> sendInitialDataToActor() {
        final List<SourceIdentifier> sourceIdentifiers = List.copyOf(SchemaContextUtil.getConstituentModuleIdentifiers(
            currentSchema.mountContext().getEffectiveModelContext()));

        LOG.debug("{}: Sending CreateInitialMasterActorData with sourceIdentifiers {} to {}", id, sourceIdentifiers,
            masterActorRef);

        // send initial data to master actor
        return Patterns.ask(masterActorRef, new CreateInitialMasterActorData(deviceDataBroker, netconfService,
            sourceIdentifiers, deviceServices), actorResponseWaitTime);
    }

    private void updateDeviceData() {
        final String masterAddress = Cluster.get(actorSystem).selfAddress().toString();
        LOG.debug("{}: updateDeviceData with master address {}", id, masterAddress);
        datastoreAdapter.updateClusteredDeviceData(true, masterAddress, currentSchema.capabilities(),
                netconfSessionPreferences.sessionId());
    }
}
