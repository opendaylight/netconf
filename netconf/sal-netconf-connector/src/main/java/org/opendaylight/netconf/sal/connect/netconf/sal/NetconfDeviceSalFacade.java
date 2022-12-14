/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.sal;

import com.google.common.annotations.VisibleForTesting;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.DataTreeIdentifier;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.netconf.dom.api.NetconfDataTreeService;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceHandler;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceServices;
import org.opendaylight.netconf.sal.connect.netconf.NetconfDeviceSchema;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfDeviceCapabilities;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.optional.rev190614.NetconfNodeFieldsOptional;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.optional.rev190614.netconf.node.fields.optional.Topology;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.optional.rev190614.netconf.node.fields.optional.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.optional.rev190614.netconf.node.fields.optional.topology.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.optional.rev190614.netconf.node.fields.optional.topology.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.optional.rev190614.netconf.node.fields.optional.topology.node.DatastoreLock;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NetconfDeviceSalFacade implements RemoteDeviceHandler, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfDeviceSalFacade.class);

    private final RemoteDeviceId id;
    private final NetconfDeviceSalProvider salProvider;
    private final DataBroker dataBroker;
    private final String topologyId;

    private ListenerRegistration<LockChangeListener> listenerRegistration = null;

    public NetconfDeviceSalFacade(final RemoteDeviceId id, final DOMMountPointService mountPointService,
            final DataBroker dataBroker, final String topologyId) {
        this(id, new NetconfDeviceSalProvider(id, mountPointService, dataBroker), dataBroker, topologyId);
    }

    @VisibleForTesting
    NetconfDeviceSalFacade(final RemoteDeviceId id, final NetconfDeviceSalProvider salProvider,
            final DataBroker dataBroker, final String topologyId) {
        this.id = id;
        this.salProvider = salProvider;
        this.dataBroker = dataBroker;
        this.topologyId = topologyId;
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
        // FIXME: instanceof DOMRpcService, as it might be others
        final var netconfDataBroker = new NetconfDeviceDataBroker(id, mountContext, deviceRpc, sessionPreferences);
        final var netconfDataTree = AbstractNetconfDataTreeService.of(id, mountContext, deviceRpc, sessionPreferences);
        registerLockListener(netconfDataBroker, netconfDataTree);

        salProvider.getMountInstance().onTopologyDeviceConnected(modelContext, services, netconfDataBroker,
            netconfDataTree);
        salProvider.getTopologyDatastoreAdapter().updateDeviceData(true, deviceSchema.capabilities());
    }

    @Override
    public synchronized void onDeviceDisconnected() {
        salProvider.getTopologyDatastoreAdapter().updateDeviceData(false, NetconfDeviceCapabilities.empty());
        salProvider.getMountInstance().onTopologyDeviceDisconnected();
        closeLockChangeListener();
    }

    @Override
    public synchronized void onDeviceFailed(final Throwable throwable) {
        salProvider.getTopologyDatastoreAdapter().setDeviceAsFailed(throwable);
        salProvider.getMountInstance().onTopologyDeviceDisconnected();
        closeLockChangeListener();
    }

    @Override
    public synchronized void close() {
        closeGracefully(salProvider);
        closeLockChangeListener();
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

    private void closeLockChangeListener() {
        if (listenerRegistration != null) {
            listenerRegistration.close();
        }
    }

    private void registerLockListener(final NetconfDeviceDataBroker netconfDeviceDataBroker,
                                      final NetconfDataTreeService netconfDataTreeService) {
        listenerRegistration = dataBroker.registerDataTreeChangeListener(
                DataTreeIdentifier.create(LogicalDatastoreType.CONFIGURATION, createTopologyListPath()),
                new LockChangeListener(netconfDeviceDataBroker, netconfDataTreeService));
    }

    private InstanceIdentifier<DatastoreLock> createTopologyListPath() {
        return InstanceIdentifier.create(NetconfNodeFieldsOptional.class)
                .child(Topology.class, new TopologyKey(new TopologyId(topologyId)))
                .child(Node.class, new NodeKey(new NodeId(id.getName())))
                .child(DatastoreLock.class);

    }
}
