/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.sal;

import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceHandler;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceServices;
import org.opendaylight.netconf.sal.connect.netconf.NetconfDeviceSchema;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetconfDeviceSalFacade implements RemoteDeviceHandler, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfDeviceSalFacade.class);

    private final RemoteDeviceId id;
    private final NetconfDeviceSalProvider salProvider;
    private final boolean lockDatastore;

    public NetconfDeviceSalFacade(final RemoteDeviceId id, final NetconfDeviceSalProvider salProvider,
            final boolean lockDatastore) {
        this.id = id;
        this.salProvider = salProvider;
        this.lockDatastore = lockDatastore;
    }

    @Override
    public synchronized void onNotification(final DOMNotification domNotification) {
        salProvider.getMountInstance().publish(domNotification);
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

        salProvider.getMountInstance().onTopologyDeviceConnected(modelContext, services, netconfDataBroker,
            netconfDataTree);
    }

    @Override
    public synchronized void onDeviceDisconnected() {
        salProvider.getMountInstance().onTopologyDeviceDisconnected();
    }

    @Override
    public synchronized void onDeviceFailed(final Throwable throwable) {
        salProvider.getMountInstance().onTopologyDeviceDisconnected();
    }

    @Override
    public synchronized void close() {
        closeGracefully(salProvider);
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
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
