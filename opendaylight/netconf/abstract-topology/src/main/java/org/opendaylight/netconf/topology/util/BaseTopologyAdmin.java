/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.util;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import javax.annotation.Nonnull;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataChangeEvent;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netconf.topology.ConnectionAggregator;
import org.opendaylight.netconf.topology.NodeAdministrator;
import org.opendaylight.netconf.topology.TopologyAdministrator;
import org.opendaylight.netconf.topology.TopologyAdministratorCallback;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.Identifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public final class BaseTopologyAdmin<M>
    implements TopologyAdministrator, DataChangeListener {

    private final DataBroker dataBroker;
    private final boolean isMaster;

    private final TopologyAdministratorCallback<M> delegateTopologyHandler;

    private final Map<NodeId, NodeAdministrator> nodes = new HashMap<>();
    private final ConnectionAggregator aggregator;
    private final NodeWriter naSalNodeWriter;

    public BaseTopologyAdmin(final DataBroker dataBroker, final String topologyId,
        final TopologyAdministratorCallback<M> delegateTopologyHandler, final ConnectionAggregator aggregator) {

        this(dataBroker, topologyId, delegateTopologyHandler, aggregator, new SalNodeWriter(dataBroker));
    }

    public BaseTopologyAdmin(final DataBroker dataBroker, final String topologyId,
        final TopologyAdministratorCallback<M> delegateTopologyHandler, final ConnectionAggregator aggregator,
        final NodeWriter naSalNodeWriter) {

        this.dataBroker = dataBroker;
        this.delegateTopologyHandler = delegateTopologyHandler;
        this.aggregator = aggregator;
        this.naSalNodeWriter = naSalNodeWriter;

        isMaster = elect();
        if(isMaster()) {
            registerTopologyListener(createTopologyId(topologyId));
        }
    }

    private boolean elect() {
        // FIXME implement this with EntityElectionService
        return true;
    }

    private static InstanceIdentifier<Topology> createTopologyId(final String topologyId) {
        final InstanceIdentifier<NetworkTopology> networkTopology = InstanceIdentifier.create(NetworkTopology.class);
        return networkTopology.child(Topology.class, new TopologyKey(new TopologyId(topologyId)));
    }

    private void registerTopologyListener(final InstanceIdentifier<Topology> topologyId) {
        dataBroker.registerDataChangeListener(LogicalDatastoreType.CONFIGURATION, topologyId, this, DataChangeScope.SUBTREE);
    }

    @Override public Iterable<TopologyAdministrator> getPeers() {
        // FIXME return remote proxies for all peers
        return Collections.emptySet();
    }

    public void onNodeConfigured(final NodeId nodeId, final Node node) {
        Preconditions.checkState(isMaster(), "Only master administrator can listen to configuration changes");

        Futures.addCallback(connect(nodeId, node), new FutureCallback<Node>() {
            @Override public void onSuccess(final Node result) {
                naSalNodeWriter.update(nodeId, result);
            }

            @Override public void onFailure(final Throwable t) {
                // If the combined connection attempt failed, set the node to connection failed
                naSalNodeWriter.update(nodeId, nodes.get(nodeId).getFailedState(nodeId, node));
                // FIXME disconnect those which succeeded
                // just issue a delete on delegateTopologyHandler that gets handled on lower level
            }
        });
    }

    @Override
    public ListenableFuture<Node> connect(final NodeId nodeId, final Node node) {
        // TODO how to monitor the connection for failures and how to react ? what about reconnecting connections like in netconf
        return delegateTopologyHandler.connect(nodeId, node);
    }

    public void onNodeUpdated(@Nonnull final NodeId nodeId, final Node node) {
        Preconditions.checkState(isMaster(), "Only master administrator can listen to configuration changes");

        Futures.addCallback(update(nodeId, node), new FutureCallback<Node>() {
            @Override public void onSuccess(final Node result) {
                naSalNodeWriter.update(nodeId, result);
            }

            @Override public void onFailure(final Throwable t) {
                // If the combined connection attempt failed, set the node to connection failed
                naSalNodeWriter.update(nodeId, nodes.get(nodeId).getFailedState(nodeId, node));
                // FIXME disconnect those which succeeded
            }
        });
    }

    @Override
    public ListenableFuture<Node> update(final NodeId nodeId, final Node node) {
        // Set initial state
        naSalNodeWriter.init(nodeId, nodes.get(nodeId).getInitialState(nodeId, node));
        // Trigger update
        return delegateTopologyHandler.update(nodeId, node);
    }

    public void onNodeDeleted(@Nonnull final NodeId nodeId) {
        Preconditions.checkState(isMaster(), "Only master administrator can listen to configuration changes");

        Futures.addCallback(delete(nodeId), new FutureCallback<Void>() {
            @Override public void onSuccess(final Void result) {
                naSalNodeWriter.delete(nodeId);
            }

            @Override public void onFailure(final Throwable t) {
                // FIXME unable to disconnect all the connections, what do we do now ?
            }
        });
    }

    @Override
    public ListenableFuture<Void> delete(final NodeId nodeId) {
        // Trigger delete
        return delegateTopologyHandler.delete(nodeId);
    }

    @Override public boolean isMaster() {
        return isMaster;
    }

    @Override
    public void onDataChanged(AsyncDataChangeEvent<InstanceIdentifier<?>, DataObject> change) {
        // TODO parse the event and call onNodeConfigured, Updated and Deleted Reuse code from ncmount coretutorial
        for (Entry<InstanceIdentifier<?>, DataObject> entry : change.getCreatedData().entrySet()) {
            if (entry.getValue() instanceof Node) {
                NodeId nodeId = getNodeId(entry.getKey());
                onNodeConfigured(nodeId, (Node) entry.getValue());
            }
        }

        for (Entry<InstanceIdentifier<?>, DataObject> entry : change.getUpdatedData().entrySet()) {
            if (entry.getValue() instanceof Node) {
                NodeId nodeId = getNodeId(entry.getKey());
                onNodeUpdated(nodeId, (Node) entry.getValue());
            }
        }

        for (InstanceIdentifier<?> path : change.getRemovedPaths()) {
            onNodeDeleted(getNodeId(path));
        }
    }

    /**
     * Determines the Netconf Node Node ID, given the node's instance
     * identifier.
     *
     * @param path Node's instance identifier
     * @return     NodeId for the node
     */
    private NodeId getNodeId(final InstanceIdentifier<?> path) {
        for (InstanceIdentifier.PathArgument pathArgument : path.getPathArguments()) {
            if (pathArgument instanceof InstanceIdentifier.IdentifiableItem<?, ?>) {

                final Identifier key = ((InstanceIdentifier.IdentifiableItem) pathArgument).getKey();
                if(key instanceof NodeKey) {
                    return ((NodeKey) key).getNodeId();
                }
            }
        }
        return null;
    }
}
