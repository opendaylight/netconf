/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.sal;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.List;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMActionService;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceHandler;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfDeviceCapabilities;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNodeConnectionStatus.ConnectionStatus;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NetconfDeviceSalFacade implements AutoCloseable, RemoteDeviceHandler<NetconfSessionPreferences> {

    private static final Logger LOG = LoggerFactory.getLogger(NetconfDeviceSalFacade.class);

    private final RemoteDeviceId id;
    private final NetconfDeviceSalProvider salProvider;
    private final List<AutoCloseable> salRegistrations = new ArrayList<>();

    public NetconfDeviceSalFacade(final RemoteDeviceId id, final DOMMountPointService mountPointService,
            final DataBroker dataBroker) {
        this.id = id;
        this.salProvider = new NetconfDeviceSalProvider(id, mountPointService, dataBroker);
    }

    @VisibleForTesting
    NetconfDeviceSalFacade(final RemoteDeviceId id, final NetconfDeviceSalProvider salProvider) {
        this.id = id;
        this.salProvider = salProvider;
    }

    @Override
    public synchronized void onNotification(final DOMNotification domNotification) {
        salProvider.getMountInstance().publish(domNotification);
    }

    @Override
    public synchronized void onDeviceConnected(final SchemaContext schemaContext,
                                               final NetconfSessionPreferences netconfSessionPreferences,
                                               final DOMRpcService deviceRpc, final DOMActionService deviceAction) {

        final DOMDataBroker domBroker =
                new NetconfDeviceDataBroker(id, schemaContext, deviceRpc, netconfSessionPreferences);

        final NetconfDeviceNotificationService notificationService = new NetconfDeviceNotificationService();

        salProvider.getMountInstance()
                .onTopologyDeviceConnected(schemaContext, domBroker, deviceRpc, notificationService, deviceAction);
        salProvider.getTopologyDatastoreAdapter()
                .updateDeviceData(true, netconfSessionPreferences.getNetconfDeviceCapabilities());
    }

    @Override
    public synchronized void onDeviceReconnected(NetconfSessionPreferences netconfSessionPreferences,
            NetconfNode node) {
        salProvider.getTopologyDatastoreAdapter().updateDeviceData(ConnectionStatus.Connecting,
                netconfSessionPreferences.getNetconfDeviceCapabilities(), LogicalDatastoreType.CONFIGURATION, node);
    }

    @Override
    public synchronized void onDeviceDisconnected() {
        salProvider.getTopologyDatastoreAdapter().updateDeviceData(false, new NetconfDeviceCapabilities());
        salProvider.getMountInstance().onTopologyDeviceDisconnected();
    }

    @Override
    public synchronized void onDeviceFailed(final Throwable throwable) {
        salProvider.getTopologyDatastoreAdapter().setDeviceAsFailed(throwable);
        salProvider.getMountInstance().onTopologyDeviceDisconnected();
    }

    @Override
    public synchronized void close() {
        for (final AutoCloseable reg : Lists.reverse(salRegistrations)) {
            closeGracefully(reg);
        }
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
