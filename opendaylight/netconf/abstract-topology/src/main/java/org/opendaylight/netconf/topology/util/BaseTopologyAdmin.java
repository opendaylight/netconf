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
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataTreeIdentifier;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netconf.topology.ConnectionAggregator;
import org.opendaylight.netconf.topology.NodeAdministrator;
import org.opendaylight.netconf.topology.NodeAdministratorCallback;
import org.opendaylight.netconf.topology.TopologyAdministrator;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public final class BaseTopologyAdmin<N extends Node, M>
    implements TopologyAdministrator<N>, DataTreeChangeListener<Topology> {

    private final DataBroker dataBroker;
    private final boolean isMaster;

    private final Map<NodeId, NodeAdministrator<N>> nodes = new HashMap<>();
    private final NodeAdministratorCallback<N, M> delegateNodeHandler;
    private final ConnectionAggregator<N> aggregator;
    private final NodeWriter<N> naSalNodeWriter;

    public BaseTopologyAdmin(final DataBroker dataBroker, final String topologyId,
        final NodeAdministratorCallback<N, M> delegateNodeHandler, final ConnectionAggregator<N> aggregator) {
        this(dataBroker, topologyId, delegateNodeHandler, aggregator, new SalNodeWriter<N>(dataBroker));
    }

    public BaseTopologyAdmin(final DataBroker dataBroker, final String topologyId,
        final NodeAdministratorCallback<N, M> delegateNodeHandler, final ConnectionAggregator<N> aggregator,
        final NodeWriter<N> naSalNodeWriter) {
        this.dataBroker = dataBroker;
        this.delegateNodeHandler = delegateNodeHandler;
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
        final DataTreeIdentifier<Topology> topologyTreeId =
            new DataTreeIdentifier<>(LogicalDatastoreType.CONFIGURATION, topologyId);
        dataBroker.registerDataTreeChangeListener(topologyTreeId, this);
    }

    @Override public Iterable<TopologyAdministrator<N>> getPeers() {
        // FIXME return remote proxies for all peers
        return Collections.emptySet();
    }

    public void onNodeConfigured(final NodeId nodeId, final N node) {
        Preconditions.checkState(isMaster(), "Only master administrator can listen to configuration changes");

        List<ListenableFuture<N>> connectionFutures = new ArrayList<>();
        connectionFutures.add(connect(nodeId, node));

        // create new NodeAdmin and notify everybody else
        for (TopologyAdministrator<N> getPeer : getPeers()) {
            // FIXME add strategy here to determine who to connect
            connectionFutures.add(getPeer.connect(nodeId, node));
        }

        ListenableFuture<N> combinedConnectResult = aggregator.combineConnectAttempts(connectionFutures);
        Futures.addCallback(combinedConnectResult, new FutureCallback<N>() {
            @Override public void onSuccess(final N result) {
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
    public ListenableFuture<N> connect(final NodeId nodeId, final N node) {
        // Init node admin and a writer for it
        final SalNodeWriter<N> naSalNodeWriter = new SalNodeWriter<>(dataBroker);
        final BaseNodeAdmin<N, M> naBaseNodeAdmin = new BaseNodeAdmin<>(delegateNodeHandler);
        nodes.put(nodeId, naBaseNodeAdmin);

        // Set initial state ? in every peer or just master ? TODO
        naSalNodeWriter.init(nodeId, naBaseNodeAdmin.getInitialState(nodeId, node));
        // Trigger connect
        return naBaseNodeAdmin.connect(nodeId, node);
        // TODO how to monitor the connection for failures and how to react ? what about reconnecting connections like in netconf
    }

    public void onNodeUpdated(@Nonnull final NodeId nodeId, final N node) {
        Preconditions.checkState(isMaster(), "Only master administrator can listen to configuration changes");

        List<ListenableFuture<N>> connectionFutures = new ArrayList<>();
        connectionFutures.add(update(nodeId, node));

        // create new NodeAdmin and notify everybody else
        for (TopologyAdministrator<N> getPeer : getPeers()) {
            connectionFutures.add(getPeer.update(nodeId, node));
        }

        ListenableFuture<N> combinedConnectResult = aggregator.combineUpdateAttempts(connectionFutures);
        Futures.addCallback(combinedConnectResult, new FutureCallback<N>() {
            @Override public void onSuccess(final N result) {
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
    public ListenableFuture<N> update(final NodeId nodeId, final N node) {
        // Set initial state
        naSalNodeWriter.init(nodeId, nodes.get(nodeId).getInitialState(nodeId, node));
        // Trigger update
        return nodes.get(nodeId).update(nodeId, node);
    }

    public void onNodeDeleted(@Nonnull final NodeId nodeId) {
        Preconditions.checkState(isMaster(), "Only master administrator can listen to configuration changes");

        List<ListenableFuture<Void>> connectionFutures = new ArrayList<>();
        connectionFutures.add(delete(nodeId));

        // create new NodeAdmin and notify everybody else
        for (TopologyAdministrator<N> getPeer : getPeers()) {
            connectionFutures.add(getPeer.delete(nodeId));
        }

        ListenableFuture<Void> combinedConnectResult = aggregator.combineDisconnectAttempts(connectionFutures);
        Futures.addCallback(combinedConnectResult, new FutureCallback<Void>() {
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
        return nodes.get(nodeId).delete(nodeId);
    }

    @Override public void onDataTreeChanged(@Nonnull final Collection<DataTreeModification<Topology>> collection) {
        // TODO parse the event and call onNodeConfigured, Updated and Deleted Reuse code from ncmount coretutorial
    }

    @Override public boolean isMaster() {
        return isMaster;
    }
}
