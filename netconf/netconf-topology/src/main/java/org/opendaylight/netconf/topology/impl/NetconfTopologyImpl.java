/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.impl;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
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
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPointService;
import org.opendaylight.netconf.client.NetconfClientDispatcher;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceHandler;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;
import org.opendaylight.netconf.sal.connect.netconf.sal.NetconfDeviceSalFacade;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.netconf.topology.AbstractNetconfTopology;
import org.opendaylight.netconf.topology.api.SchemaRepositoryProvider;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopologyBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetconfTopologyImpl extends AbstractNetconfTopology
        implements DataTreeChangeListener<Node>, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(NetconfTopologyImpl.class);

    private ListenerRegistration<NetconfTopologyImpl> datastoreListenerRegistration = null;

    public NetconfTopologyImpl(final String topologyId, final NetconfClientDispatcher clientDispatcher,
                               final EventExecutor eventExecutor, final ScheduledThreadPool keepaliveExecutor,
                               final ThreadPool processingExecutor, final SchemaRepositoryProvider schemaRepositoryProvider,
                               final DataBroker dataBroker, final DOMMountPointService mountPointService) {
        super(topologyId, clientDispatcher, eventExecutor, keepaliveExecutor, processingExecutor,
                schemaRepositoryProvider, dataBroker, mountPointService);
    }

    @Override
    public void close() throws Exception {
        // close all existing connectors, delete whole topology in datastore?
        for (final NetconfConnectorDTO connectorDTO : activeConnectors.values()) {
            connectorDTO.close();
        }
        activeConnectors.clear();

        if (datastoreListenerRegistration != null) {
            datastoreListenerRegistration.close();
            datastoreListenerRegistration = null;
        }
    }

    @Override
    protected RemoteDeviceHandler<NetconfSessionPreferences> createSalFacade(final RemoteDeviceId id) {
        return new NetconfDeviceSalFacade(id, mountPointService, dataBroker);
    }

    /**
     * Invoked by blueprint.
     */
    public void init() {
        final WriteTransaction wtx = dataBroker.newWriteOnlyTransaction();
        initTopology(wtx, LogicalDatastoreType.CONFIGURATION);
        initTopology(wtx, LogicalDatastoreType.OPERATIONAL);
        Futures.addCallback(wtx.submit(), new FutureCallback<Void>() {
            @Override
            public void onSuccess(final Void result) {
                LOG.debug("topology initialization successful");
            }

            @Override
            public void onFailure(final Throwable throwable) {
                LOG.error("Unable to initialize netconf-topology, {}", throwable);
            }
        });

        LOG.debug("Registering datastore listener");
        datastoreListenerRegistration =
                dataBroker.registerDataTreeChangeListener(
                        new DataTreeIdentifier<>(LogicalDatastoreType.CONFIGURATION,
                                TopologyUtil.createTopologyListPath(topologyId).child(Node.class)), this);


    }

    @Override
    public void onDataTreeChanged(@Nonnull final Collection<DataTreeModification<Node>> collection) {
        for (final DataTreeModification<Node> change : collection) {
            final DataObjectModification<Node> rootNode = change.getRootNode();
            switch (rootNode.getModificationType()) {
                case SUBTREE_MODIFIED:
                    LOG.debug("Config for node {} updated", TopologyUtil.getNodeId(rootNode.getIdentifier()));
                    disconnectNode(TopologyUtil.getNodeId(rootNode.getIdentifier()));
                    connectNode(TopologyUtil.getNodeId(rootNode.getIdentifier()), rootNode.getDataAfter());
                    break;
                case WRITE:
                    LOG.debug("Config for node {} created", TopologyUtil.getNodeId(rootNode.getIdentifier()));
                    if (activeConnectors.containsKey(TopologyUtil.getNodeId(rootNode.getIdentifier()))) {
                        LOG.warn("RemoteDevice{{}} was already configured, reconfiguring..",
                                TopologyUtil.getNodeId(rootNode.getIdentifier()));
                        disconnectNode(TopologyUtil.getNodeId(rootNode.getIdentifier()));
                    }
                    connectNode(TopologyUtil.getNodeId(rootNode.getIdentifier()), rootNode.getDataAfter());
                    break;
                case DELETE:
                    LOG.debug("Config for node {} deleted", TopologyUtil.getNodeId(rootNode.getIdentifier()));
                    disconnectNode(TopologyUtil.getNodeId(rootNode.getIdentifier()));
                    break;
                default:
                    LOG.debug("Unsupported modification type: {}.", rootNode.getModificationType());
            }
        }
    }

    private void initTopology(final WriteTransaction wtx, final LogicalDatastoreType datastoreType) {
        final NetworkTopology networkTopology = new NetworkTopologyBuilder().build();
        final InstanceIdentifier<NetworkTopology> networkTopologyId =
                InstanceIdentifier.builder(NetworkTopology.class).build();
        wtx.merge(datastoreType, networkTopologyId, networkTopology);
        final Topology topology = new TopologyBuilder().setTopologyId(new TopologyId(topologyId)).build();
        wtx.merge(datastoreType,
                networkTopologyId.child(Topology.class, new TopologyKey(new TopologyId(topologyId))), topology);
    }

}
