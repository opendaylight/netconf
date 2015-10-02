/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.util;

import akka.actor.TypedActor;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nonnull;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.netconf.topology.NodeManager;
import org.opendaylight.netconf.topology.RoleChangeStrategy;
import org.opendaylight.netconf.topology.StateAggregator;
import org.opendaylight.netconf.topology.TopologyManager;
import org.opendaylight.netconf.topology.TopologyManagerCallback;
import org.opendaylight.netconf.topology.TopologyManagerCallback.TopologyManagerCallbackFactory;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;

public final class BaseTopologyManager<M>
    implements TopologyManager<M> {

    private final DataBroker dataBroker;

    private final RoleChangeStrategy roleChangeStrategy;
    private final StateAggregator aggregator;
    private final NodeWriter naSalNodeWriter;

    private final String topologyId;
    private final TopologyManagerCallback<M> delegateTopologyHandler;
    private final Map<NodeId, NodeManager> nodes = new HashMap<>();

    private boolean isMaster;

    public BaseTopologyManager(final DataBroker dataBroker,
                               final String topologyId,
                               final TopologyManagerCallbackFactory<M> topologyManagerCallbackFactory,
                               final StateAggregator aggregator,
                               final NodeWriter naSalNodeWriter,
                               final RoleChangeStrategy roleChangeStrategy) {

        this.dataBroker = dataBroker;
        this.topologyId = topologyId;
        this.delegateTopologyHandler = topologyManagerCallbackFactory.create(this);
        this.aggregator = aggregator;
        this.naSalNodeWriter = naSalNodeWriter;
        this.roleChangeStrategy = roleChangeStrategy;

        // election has not yet happened
        isMaster = false;

        // TODO change to enum, master/slave active/standby
        roleChangeStrategy.registerRoleCandidate(this);
    }

    @Override public Iterable<TopologyManager<M>> getPeers() {
        // FIXME return remote proxies for all peers
        return Collections.emptySet();
    }


    @Override
    public ListenableFuture<Node> nodeCreated(final NodeId nodeId, final Node node) {
        // TODO how to monitor the connection for failures and how to react ? what about reconnecting connections like in netconf
        ArrayList<ListenableFuture<Node>> futures = new ArrayList<>();

        if (isMaster) {
            futures.add(delegateTopologyHandler.nodeCreated(TypedActor.context(), nodeId, node));
            // only master should call connect on peers and aggregate futures
            for (TopologyManager topologyManager : getPeers()) {
                futures.add(topologyManager.nodeCreated(nodeId, node));
            }

            // TODO handle resyncs

            final ListenableFuture<Node> aggregatedFuture = aggregator.combineCreateAttempts(futures);
            Futures.addCallback(aggregatedFuture, new FutureCallback<Node>() {
                @Override public void onSuccess(final Node result) {
                    // FIXME make this (writing state data for nodes) optional and customizable
                    // this should be possible with providing your own NodeWriter implementation, maybe rename this interface?
                    naSalNodeWriter.update(nodeId, result);
                }

                @Override public void onFailure(final Throwable t) {
                    // If the combined connection attempt failed, set the node to connection failed
                    naSalNodeWriter.update(nodeId, nodes.get(nodeId).getFailedState(nodeId, node));
                    // FIXME disconnect those which succeeded
                    // just issue a delete on delegateTopologyHandler that gets handled on lower level
                }
            });

            //combine peer futures
            return aggregatedFuture;
        }

        // trigger create on this slave
        return delegateTopologyHandler.nodeCreated(TypedActor.context(), nodeId, node);
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

            final ListenableFuture<Node> aggregatedFuture = aggregator.combineUpdateAttempts(futures);
            Futures.addCallback(aggregatedFuture, new FutureCallback<Node>() {
                @Override public void onSuccess(final Node result) {
                    // FIXME make this (writing state data for nodes) optional and customizable
                    // this should be possible with providing your own NodeWriter implementation, maybe rename this interface?
                    naSalNodeWriter.update(nodeId, result);
                }

                @Override public void onFailure(final Throwable t) {
                    // If the combined connection attempt failed, set the node to connection failed
                    naSalNodeWriter.update(nodeId, nodes.get(nodeId).getFailedState(nodeId, node));
                    // FIXME disconnect those which succeeded
                    // just issue a delete on delegateTopologyHandler that gets handled on lower level
                }
            });

            //combine peer futures
            return aggregatedFuture;
        }

        // Trigger update on this slave
        return delegateTopologyHandler.nodeUpdated(nodeId, node);
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

            final ListenableFuture<Void> aggregatedFuture = aggregator.combineDeleteAttempts(futures);
            Futures.addCallback(aggregatedFuture, new FutureCallback<Void>() {
                @Override public void onSuccess(final Void result) {
                    naSalNodeWriter.delete(nodeId);
                }

                @Override public void onFailure(final Throwable t) {
                    // FIXME unable to disconnect all the connections, what do we do now ?
                }
            });

            return aggregatedFuture;
        }

        // Trigger delete
        return delegateTopologyHandler.nodeDeleted(nodeId);
    }

    @Override public boolean isMaster() {
        return isMaster;
    }

    @Override
    public void onRoleChanged(RoleChangeDTO roleChangeDTO) {
        isMaster = roleChangeDTO.isOwner();
        delegateTopologyHandler.onRoleChanged(roleChangeDTO);
    }

    @Nonnull
    @Override
    public String getTopologyId() {
        return topologyId;
    }
}
