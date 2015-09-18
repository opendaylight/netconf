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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nonnull;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataObjectModification;
import org.opendaylight.controller.md.sal.binding.api.DataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataTreeIdentifier;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netconf.topology.NodeManager;
import org.opendaylight.netconf.topology.StateAggregator;
import org.opendaylight.netconf.topology.TopologyManager;
import org.opendaylight.netconf.topology.TopologyManagerCallback;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yangtools.yang.binding.Identifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier.PathArgument;

public final class BaseTopologyManager<M>
    implements TopologyManager, DataTreeChangeListener<Node> {

    private final DataBroker dataBroker;
    private final boolean isMaster;

    private final TopologyManagerCallback<M> delegateTopologyHandler;

    private final Map<NodeId, NodeManager> nodes = new HashMap<>();
    private final StateAggregator aggregator;
    private final NodeWriter naSalNodeWriter;

    public BaseTopologyManager(final DataBroker dataBroker, final String topologyId,
                               final TopologyManagerCallback<M> delegateTopologyHandler, final StateAggregator aggregator) {

        this(dataBroker, topologyId, delegateTopologyHandler, aggregator, new SalNodeWriter(dataBroker, topologyId));
    }

    public BaseTopologyManager(final DataBroker dataBroker, final String topologyId,
                               final TopologyManagerCallback<M> delegateTopologyHandler, final StateAggregator aggregator,
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
        dataBroker.registerDataTreeChangeListener(new DataTreeIdentifier<>(LogicalDatastoreType.CONFIGURATION, topologyId.child(Node.class)), this);
    }

    @Override public Iterable<TopologyManager> getPeers() {
        // FIXME return remote proxies for all peers
        return Collections.emptySet();
    }

    public void onNodeConfigured(final NodeId nodeId, final Node node) {
        Preconditions.checkState(isMaster(), "Only master administrator can listen to configuration changes");

        Futures.addCallback(nodeCreated(nodeId, node), new FutureCallback<Node>() {
            @Override public void onSuccess(final Node result) {
                // FIXME make this (writing state data for nodes) optional and customizable
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
    public ListenableFuture<Node> nodeCreated(final NodeId nodeId, final Node node) {
        // TODO how to monitor the connection for failures and how to react ? what about reconnecting connections like in netconf
        ArrayList<ListenableFuture<Node>> futures = new ArrayList<>();

        if (isMaster) {
            futures.add(delegateTopologyHandler.nodeCreated(nodeId, node));
            // only master should call connect on peers and aggregate futures
            for (TopologyManager topologyManager : getPeers()) {
                futures.add(topologyManager.nodeCreated(nodeId, node));
            }

            // TODO handle resyncs

            //combine peer futures
            return aggregator.combineCreateAttempts(futures);
        }

        // trigger create on this slave
        return delegateTopologyHandler.nodeCreated(nodeId, node);
    }

    public void onNodeUpdated(@Nonnull final NodeId nodeId, final Node node) {
        Preconditions.checkState(isMaster(), "Only master administrator can listen to configuration changes");

        Futures.addCallback(nodeUpdated(nodeId, node), new FutureCallback<Node>() {
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
    public ListenableFuture<Node> nodeUpdated(final NodeId nodeId, final Node node) {

        ArrayList<ListenableFuture<Node>> futures = new ArrayList<>();

        // Master needs to trigger nodeUpdated on peers and combine results
        if (isMaster) {
            futures.add(nodes.get(nodeId).nodeUpdated(nodeId, node));
            for (TopologyManager topology : getPeers()) {
                futures.add(topology.nodeUpdated(nodeId, node));
            }

            return aggregator.combineUpdateAttempts(futures);
        }

        // Trigger update on this slave
        return delegateTopologyHandler.nodeUpdated(nodeId, node);
    }

    public void onNodeDeleted(@Nonnull final NodeId nodeId) {
        Preconditions.checkState(isMaster(), "Only master administrator can listen to configuration changes");

        Futures.addCallback(nodeDeleted(nodeId), new FutureCallback<Void>() {
            @Override public void onSuccess(final Void result) {
                naSalNodeWriter.delete(nodeId);
            }

            @Override public void onFailure(final Throwable t) {
                // FIXME unable to disconnect all the connections, what do we do now ?
            }
        });
    }

    @Override
    public ListenableFuture<Void> nodeDeleted(final NodeId nodeId) {
        ArrayList<ListenableFuture<Void>> futures = new ArrayList<>();

        // Master needs to trigger delete on peers and combine results
        if (isMaster) {
            futures.add(nodes.get(nodeId).nodeDeleted(nodeId));
            for (TopologyManager topology : getPeers()) {
                futures.add(topology.nodeDeleted(nodeId));
            }

            return aggregator.combineDeleteAttempts(futures);
        }

        // Trigger delete
        return delegateTopologyHandler.nodeDeleted(nodeId);
    }

    @Override public boolean isMaster() {
        return isMaster;
    }

    // FIXME extract data change listener as a special source of events + provide a way to customize
    @Override
    public void onDataTreeChanged(@Nonnull Collection<DataTreeModification<Node>> changes) {
        for (DataTreeModification<Node> change : changes) {
            final DataObjectModification<Node> rootNode = change.getRootNode();
            switch (rootNode.getModificationType()) {
                case WRITE:
                    onNodeConfigured(getNodeId(rootNode.getIdentifier()), rootNode.getDataAfter());
                case SUBTREE_MODIFIED:
                    onNodeUpdated(getNodeId(rootNode.getIdentifier()), rootNode.getDataAfter());
                case DELETE:
                    onNodeDeleted(getNodeId(rootNode.getIdentifier()));
            }
        }
    }

    /**
     * Determines the Netconf Node Node ID, given the node's instance
     * identifier.
     *
     * @param pathArgument Node's path arument
     * @return     NodeId for the node
     */
    private NodeId getNodeId(final PathArgument pathArgument) {
        if (pathArgument instanceof InstanceIdentifier.IdentifiableItem<?, ?>) {

            final Identifier key = ((InstanceIdentifier.IdentifiableItem) pathArgument).getKey();
            if(key instanceof NodeKey) {
                return ((NodeKey) key).getNodeId();
            }
        }
        throw new IllegalStateException("Unable to create NodeId from: " + pathArgument);
    }
}
