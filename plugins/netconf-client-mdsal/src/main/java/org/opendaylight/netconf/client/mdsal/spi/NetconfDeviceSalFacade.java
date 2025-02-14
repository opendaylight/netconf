/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.spi;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import java.util.concurrent.atomic.AtomicBoolean;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.netconf.client.mdsal.NetconfDeviceSchema;
import org.opendaylight.netconf.client.mdsal.api.NetconfSessionPreferences;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceHandler;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceServices;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetconfDeviceSalFacade implements RemoteDeviceHandler, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfDeviceSalFacade.class);

    private final NetconfDeviceMount mount;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final boolean lockDatastore;

    protected final RemoteDeviceId id;

    public NetconfDeviceSalFacade(final RemoteDeviceId id, final DOMMountPointService mountPointService,
            final YangInstanceIdentifier mountPath, final boolean lockDatastore) {
        this(id, new NetconfDeviceMount(id, mountPointService, mountPath), lockDatastore);
    }

    @VisibleForTesting
    NetconfDeviceSalFacade(final RemoteDeviceId id, final NetconfDeviceMount mount, final boolean lockDatastore) {
        this.id = requireNonNull(id);
        this.mount = requireNonNull(mount);
        this.lockDatastore = lockDatastore;
    }

    @Override
    public synchronized void onNotification(final DOMNotification domNotification) {
        mount.publish(domNotification);
    }

    @Override
    public synchronized void onDeviceConnected(final NetconfDeviceSchema deviceSchema,
            final NetconfSessionPreferences sessionPreferences, final RemoteDeviceServices services) {
        if (closed()) {
            LOG.warn("{}: Device mount was closed before device connected setup finished.", id);
            return;
        }

        final var databind = deviceSchema.databind();
        final var deviceRpc = services.rpcs();

        final var netconfDataTree = AbstractNetconfDataTreeService.of(id, databind, deviceRpc, sessionPreferences,
            lockDatastore);
        final var netconfDataBroker = new NetconfDeviceDataBroker(id, databind, deviceRpc, sessionPreferences,
            lockDatastore);

        mount.onDeviceConnected(databind.modelContext(), new NetconfRestconfStrategy(databind, netconfDataTree),
            services, netconfDataBroker, netconfDataTree);
    }

    @Override
    public synchronized void onDeviceDisconnected() {
        if (closed()) {
            LOG.warn("{}: Device mount was closed before device disconnected setup finished.", id);
            return;
        }
        mount.onDeviceDisconnected();
    }

    @Override
    public synchronized void onDeviceFailed(final Throwable throwable) {
        if (closed()) {
            LOG.warn("{}: Device mount was closed before device failed setup finished.", id, throwable);
            return;
        }
        mount.onDeviceDisconnected();
    }

    @Override
    public synchronized void close() {
        closed.set(true);
        mount.close();
    }

    public boolean closed() {
        return closed.get();
    }
}
