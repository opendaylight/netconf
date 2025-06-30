/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl;

import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.concurrent.ExecutionException;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.cluster.Cluster;
import org.apache.pekko.dispatch.OnComplete;
import org.apache.pekko.pattern.Patterns;
import org.apache.pekko.util.Timeout;
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
import org.opendaylight.netconf.client.mdsal.spi.DataOperationImpl;
import org.opendaylight.netconf.client.mdsal.spi.NetconfDataOperations;
import org.opendaylight.netconf.client.mdsal.spi.NetconfDeviceDataBroker;
import org.opendaylight.netconf.client.mdsal.spi.NetconfDeviceMount;
import org.opendaylight.netconf.databind.DatabindContext;
import org.opendaylight.netconf.dom.api.NetconfDataTreeService;
import org.opendaylight.netconf.topology.singleton.messages.CreateInitialMasterActorData;
import org.opendaylight.netconf.topology.spi.NetconfDeviceTopologyAdapter;
import org.opendaylight.netconf.topology.spi.NetconfNodeUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev241009.credentials.Credentials;
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

    /**
     * MasterSalFacade is responsible for handling the connection and disconnection
     * of NETCONF devices. It manages the master mount point and coordinates with
     * the master actor to update device data and manage session preferences.
     *
     * @param id                    the unique identifier for the remote device
     * @param credentials           the credentials used to authenticate the remote device
     * @param actorSystem           the Actor system for managing actors
     * @param masterActorRef        the reference to the master actor responsible for this device
     * @param actorResponseWaitTime the timeout duration to wait for responses from the actor
     * @param mountService          the mount point service for managing mount points
     * @param dataBroker            the data broker for accessing and modifying data in the data store
     * @param lockDatastore         a flag indicating whether the datastore should be locked
     */
    MasterSalFacade(final RemoteDeviceId id,
                    final Credentials credentials,
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

        datastoreAdapter = new NetconfDeviceTopologyAdapter(dataBroker, NetconfNodeUtils.DEFAULT_TOPOLOGY_OID, id,
            credentials);
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
        final var future = datastoreAdapter.shutdown();
        mount.close();

        try {
            future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for datastore adapter shutdown", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Datastore adapter shutdown failed", e);
        }
    }

    private void registerMasterMountPoint() {
        requireNonNull(id);

        final var databind = requireNonNull(currentSchema,
            "Device has no remote schema context yet. Probably not fully connected.")
            .databind();
        final var preferences = requireNonNull(netconfSessionPreferences,
            "Device has no capabilities yet. Probably not fully connected.");

        deviceDataBroker = newDeviceDataBroker(databind, preferences);
        netconfService = newNetconfDataTreeService(databind, preferences);

        final var proxyNetconfService = new ProxyNetconfDataTreeService(id, masterActorRef, actorSystem.dispatcher(),
            actorResponseWaitTime);
        mount.onDeviceConnected(databind.modelContext(),
            new NetconfDataOperations(new DataOperationImpl(proxyNetconfService)),
            deviceServices,
            // We need to create ProxyDOMDataBroker so accessing mountpoint
            // on leader node would be same as on follower node
            new ProxyDOMDataBroker(id, masterActorRef, actorSystem.dispatcher(), actorResponseWaitTime));
    }

    protected DOMDataBroker newDeviceDataBroker(final DatabindContext databind,
            final NetconfSessionPreferences preferences) {
        return new NetconfDeviceDataBroker(id, databind, deviceServices.rpcs(), preferences, lockDatastore);
    }

    protected NetconfDataTreeService newNetconfDataTreeService(final DatabindContext databind,
            final NetconfSessionPreferences preferences) {
        return AbstractNetconfDataTreeService.of(id, databind, deviceServices.rpcs(), preferences, lockDatastore);
    }

    private Future<Object> sendInitialDataToActor() {
        final var sourceIdentifiers = List.copyOf(SchemaContextUtil.getConstituentModuleIdentifiers(
            currentSchema.databind().modelContext()));

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
