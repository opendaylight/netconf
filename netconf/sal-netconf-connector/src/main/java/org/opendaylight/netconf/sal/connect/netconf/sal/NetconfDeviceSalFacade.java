/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.sal;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceHandler;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceId;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceServices;
import org.opendaylight.netconf.sal.connect.netconf.NetconfDeviceSchema;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;

public class NetconfDeviceSalFacade implements RemoteDeviceHandler, AutoCloseable {
    private final RemoteDeviceId id;
    private final NetconfDeviceMount mount;
    private final boolean lockDatastore;

    public NetconfDeviceSalFacade(final RemoteDeviceId id, final DOMMountPointService mountPointService,
            final boolean lockDatastore) {
        this(id, new NetconfDeviceMount(id, mountPointService, NetconfDeviceMount.defaultTopologyMountPath(id)),
            lockDatastore);
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
        final var mountContext = deviceSchema.mountContext();
        final var modelContext = mountContext.getEffectiveModelContext();

        final var deviceRpc = services.rpcs();

        final var netconfDataTree = AbstractNetconfDataTreeService.of(id, mountContext, deviceRpc, sessionPreferences,
            lockDatastore);
        final var netconfDataBroker = new NetconfDeviceDataBroker(id, mountContext, deviceRpc, sessionPreferences,
            lockDatastore);

        mount.onDeviceConnected(modelContext, services, netconfDataBroker, netconfDataTree);
    }

    @Override
    public synchronized void onDeviceDisconnected() {
        mount.onDeviceDisconnected();
    }

    @Override
    public synchronized void onDeviceFailed(final Throwable throwable) {
        mount.onDeviceDisconnected();
    }

    @Override
    public synchronized void close() {
        mount.close();
    }
}
