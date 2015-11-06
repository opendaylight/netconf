/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.impl;

import io.netty.util.concurrent.EventExecutor;
import java.util.Collection;
import javax.annotation.Nonnull;
import org.opendaylight.controller.config.threadpool.ScheduledThreadPool;
import org.opendaylight.controller.config.threadpool.ThreadPool;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataObjectModification;
import org.opendaylight.controller.md.sal.binding.api.DataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataTreeIdentifier;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.ProviderContext;
import org.opendaylight.controller.sal.core.api.Broker;
import org.opendaylight.netconf.client.NetconfClientDispatcher;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceHandler;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;
import org.opendaylight.netconf.sal.connect.netconf.sal.NetconfDeviceSalFacade;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.netconf.topology.AbstractNetconfTopology;
import org.opendaylight.netconf.topology.SchemaRepositoryProvider;
import org.opendaylight.netconf.topology.pipeline.TopologyMountPointFacade.ConnectionStatusListenerRegistration;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetconfTopologyImpl extends AbstractNetconfTopology implements DataTreeChangeListener<Node>, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(NetconfTopologyImpl.class);

        private ListenerRegistration<NetconfTopologyImpl> datastoreListenerRegistration = null;

    public NetconfTopologyImpl(final String topologyId, final NetconfClientDispatcher clientDispatcher,
                               final BindingAwareBroker bindingAwareBroker, final Broker domBroker,
                               final EventExecutor eventExecutor, final ScheduledThreadPool keepaliveExecutor,
                               final ThreadPool processingExecutor, final SchemaRepositoryProvider schemaRepositoryProvider) {
        super(topologyId, clientDispatcher,
                bindingAwareBroker, domBroker, eventExecutor,
                keepaliveExecutor, processingExecutor, schemaRepositoryProvider);
        registerToSal(this, this);
    }

    @Override
    public void close() throws Exception {
        // close all existing connectors, delete whole topology in datastore?
        for (NetconfConnectorDTO connectorDTO : activeConnectors.values()) {
            connectorDTO.getCommunicator().disconnect();
        }
        activeConnectors.clear();

        if (datastoreListenerRegistration != null) {
            datastoreListenerRegistration.close();
            datastoreListenerRegistration = null;
        }
    }

    @Override
    protected RemoteDeviceHandler<NetconfSessionPreferences> createSalFacade(RemoteDeviceId id, Broker domBroker, BindingAwareBroker bindingBroker, long defaultRequestTimeoutMillis) {
        return new NetconfDeviceSalFacade(id, domBroker, bindingAwareBroker, defaultRequestTimeoutMillis);
    }

    @Override
    public void registerMountPoint(NodeId nodeId) {
        throw new UnsupportedOperationException("MountPoint registration is not supported in regular topology, this happens automaticaly in the netconf pipeline");
    }

    @Override
    public void unregisterMountPoint(NodeId nodeId) {
        throw new UnsupportedOperationException("MountPoint registration is not supported in regular topology, this happens automaticaly in the netconf pipeline");
    }

    @Override
    public ConnectionStatusListenerRegistration registerConnectionStatusListener(NodeId node, RemoteDeviceHandler<NetconfSessionPreferences> listener) {
        throw new UnsupportedOperationException("Registering a listener on a regular netconf device is not supported(supported only in clustered netconf topology)");
    }

    @Override
    public void onSessionInitiated(ProviderContext session) {
        dataBroker = session.getSALService(DataBroker.class);

        LOG.warn("Registering datastore listener");
        datastoreListenerRegistration =
                dataBroker.registerDataTreeChangeListener(
                        new DataTreeIdentifier<>(LogicalDatastoreType.CONFIGURATION, createTopologyId(topologyId).child(Node.class)), this);
    }

    @Override
    public void onDataTreeChanged(@Nonnull Collection<DataTreeModification<Node>> collection) {
        for (DataTreeModification<Node> change : collection) {
            final DataObjectModification<Node> rootNode = change.getRootNode();
            switch (rootNode.getModificationType()) {
                case SUBTREE_MODIFIED:
                    LOG.debug("Config for node {} updated", getNodeId(rootNode.getIdentifier()));
                    disconnectNode(getNodeId(rootNode.getIdentifier()));
                    connectNode(getNodeId(rootNode.getIdentifier()), rootNode.getDataAfter());
                    break;
                case WRITE:
                    LOG.debug("Config for node {} created", getNodeId(rootNode.getIdentifier()));
                    if (activeConnectors.containsKey(getNodeId(rootNode.getIdentifier()))) {
                        LOG.warn("RemoteDevice{{}} was already configured, reconfiguring..", getNodeId(rootNode.getIdentifier()));
                        disconnectNode(getNodeId(rootNode.getIdentifier()));
                    }
                    connectNode(getNodeId(rootNode.getIdentifier()), rootNode.getDataAfter());
                    break;
                case DELETE:
                    LOG.debug("Config for node {} deleted", getNodeId(rootNode.getIdentifier()));
                    disconnectNode(getNodeId(rootNode.getIdentifier()));
                    break;
            }
        }
    }

}
