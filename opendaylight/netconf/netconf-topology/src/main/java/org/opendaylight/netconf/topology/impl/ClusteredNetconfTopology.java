/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.impl;

import akka.actor.ActorSystem;
import io.netty.util.concurrent.EventExecutor;
import java.util.Collection;
import java.util.Collections;
import org.opendaylight.controller.config.threadpool.ScheduledThreadPool;
import org.opendaylight.controller.config.threadpool.ThreadPool;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipService;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.ProviderContext;
import org.opendaylight.controller.sal.core.api.Broker;
import org.opendaylight.netconf.client.NetconfClientDispatcher;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceHandler;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.netconf.topology.AbstractNetconfTopology;
import org.opendaylight.netconf.topology.SchemaRepositoryProvider;
import org.opendaylight.netconf.topology.TopologyMountPointFacade;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClusteredNetconfTopology extends AbstractNetconfTopology implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(ClusteredNetconfTopology.class);

    private final ActorSystem actorSystem;
    private final EntityOwnershipService entityOwnershipService;

    public ClusteredNetconfTopology(final String topologyId, final NetconfClientDispatcher clientDispatcher,
                               final BindingAwareBroker bindingAwareBroker, final Broker domBroker,
                               final EventExecutor eventExecutor, final ScheduledThreadPool keepaliveExecutor,
                               final ThreadPool processingExecutor, final SchemaRepositoryProvider schemaRepositoryProvider,
                               final ActorSystem actorSystem, final EntityOwnershipService entityOwnershipService) {
        super(topologyId, clientDispatcher,
                bindingAwareBroker, domBroker, eventExecutor,
                keepaliveExecutor, processingExecutor, schemaRepositoryProvider);
        this.actorSystem = actorSystem;
        this.entityOwnershipService = entityOwnershipService;
        LOG.warn("Clustered netconf topo started");
    }

    @Override
    public void onSessionInitiated(final ProviderContext session) {
        dataBroker = session.getSALService(DataBroker.class);
    }

    @Override
    public void close() throws Exception {
        // close all existing connectors, delete whole topology in datastore?
        for (NetconfConnectorDTO connectorDTO : activeConnectors.values()) {
            connectorDTO.getCommunicator().disconnect();
        }
        activeConnectors.clear();
    }

    @Override
    protected RemoteDeviceHandler<NetconfSessionPreferences> createSalFacade(final RemoteDeviceId id, final Broker domBroker, final BindingAwareBroker bindingBroker, long defaultRequestTimeoutMillis) {
        return new TopologyMountPointFacade(id, domBroker, bindingBroker, defaultRequestTimeoutMillis);
    }

    @Override
    public void registerMountPoint(NodeId nodeId) {

    }

    @Override
    public void unregisterMountPoint(NodeId nodeId) {

    }

    @Override
    public void registerConnectionStatusListener(final NodeId node, final RemoteDeviceHandler<NetconfSessionPreferences> listener) {
        if (activeConnectors.get(node).getFacade() instanceof TopologyMountPointFacade) {
            ((TopologyMountPointFacade) activeConnectors.get(node).getFacade()).registerConnectionStatusListener(listener);
        } else {
            LOG.warn("Unable to register a connection status listener on a regular salFacade, reconfigure for topology mountpoint facade");
        }
    }

    @Override
    public Collection<ProviderFunctionality> getProviderFunctionality() {
        return Collections.emptySet();
    }
}
