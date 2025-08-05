/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.spi;

import java.util.concurrent.ExecutionException;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.netconf.client.mdsal.NetconfDeviceCapabilities;
import org.opendaylight.netconf.client.mdsal.NetconfDeviceSchema;
import org.opendaylight.netconf.client.mdsal.api.NetconfSessionPreferences;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices;
import org.opendaylight.netconf.client.mdsal.spi.NetconfDeviceSalFacade;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.device.rev250805.credentials.Credentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link NetconfDeviceSalFacade} specialization for netconf topology.
 */
public class NetconfTopologyDeviceSalFacade extends NetconfDeviceSalFacade {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfTopologyDeviceSalFacade.class);

    private final NetconfDeviceTopologyAdapter datastoreAdapter;

    /**
     * NetconfTopologyDeviceSalFacade is a specialization of NetconfDeviceSalFacade
     * for the netconf topology. It handles the lifecycle and data updates for
     * NETCONF devices within the topology.
     *
     * @param id                the unique identifier for the remote device
     * @param credentials       the credentials used to authenticate the remote device
     * @param mountPointService the mount point service for managing mount points
     * @param lockDatastore     a flag indicating whether the datastore should be locked
     * @param dataBroker        the data broker for accessing and modifying data in the data store
     */
    public NetconfTopologyDeviceSalFacade(final RemoteDeviceId id, final Credentials credentials,
            final DOMMountPointService mountPointService, final boolean lockDatastore, final DataBroker dataBroker) {
        super(id, mountPointService, NetconfNodeUtils.defaultTopologyMountPath(id), lockDatastore);
        datastoreAdapter = new NetconfDeviceTopologyAdapter(dataBroker, NetconfNodeUtils.DEFAULT_TOPOLOGY_IID, id,
            credentials);
    }

    @Override
    public synchronized void onDeviceConnected(final NetconfDeviceSchema deviceSchema,
            final NetconfSessionPreferences sessionPreferences, final RemoteDeviceServices services) {
        if (closed()) {
            LOG.warn("{}: Device adapter was closed before device connected setup finished.", id);
            return;
        }
        super.onDeviceConnected(deviceSchema, sessionPreferences, services);
        datastoreAdapter.updateDeviceData(true, deviceSchema.capabilities(), sessionPreferences.sessionId());
    }

    @Override
    public synchronized void onDeviceDisconnected() {
        if (closed()) {
            LOG.warn("{}: Device adapter was closed before device disconnected setup finished.", id);
            return;
        }
        datastoreAdapter.updateDeviceData(false, NetconfDeviceCapabilities.empty(), null);
        super.onDeviceDisconnected();
    }

    @Override
    public synchronized void onDeviceFailed(final Throwable throwable) {
        if (closed()) {
            LOG.warn("{}: Device adapter was closed before device failure setup finished.", id, throwable);
            return;
        }
        datastoreAdapter.setDeviceAsFailed(throwable);
        super.onDeviceFailed(throwable);
    }

    @Override
    public synchronized void close() {
        final var future = datastoreAdapter.shutdown();
        super.close();

        try {
            future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for datastore adapter shutdown", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Datastore adapter shutdown failed", e);
        }
    }
}
