/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.util;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.TypedActor;
import akka.actor.TypedActorExtension;
import akka.actor.TypedProps;
import akka.dispatch.OnComplete;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;
import scala.concurrent.impl.Promise.DefaultPromise;

public final class BaseTopologyManager<M>
    implements TopologyManager<M> {

    private static final Logger LOG = LoggerFactory.getLogger(BaseTopologyManager.class);

    private final ActorSystem system;
    private final List<String> remotePaths;

    private final DataBroker dataBroker;
    private final RoleChangeStrategy roleChangeStrategy;
    private final StateAggregator aggregator;

    private final NodeWriter naSalNodeWriter;
    private final String topologyId;
    private final TopologyManagerCallback<M> delegateTopologyHandler;

    private final Map<NodeId, NodeManager> nodes = new HashMap<>();
    private final List<TopologyManager> peers = new ArrayList<>();
    private final int id = new Random().nextInt();

    private boolean isMaster;

    public BaseTopologyManager(final ActorSystem system,
                               final List<String> remotePaths,
                               final DataBroker dataBroker,
                               final String topologyId,
                               final TopologyManagerCallbackFactory<M> topologyManagerCallbackFactory,
                               final StateAggregator aggregator,
                               final NodeWriter naSalNodeWriter,
                               final RoleChangeStrategy roleChangeStrategy) {
        this(system, remotePaths, dataBroker, topologyId, topologyManagerCallbackFactory, aggregator, naSalNodeWriter, roleChangeStrategy, false);
    }

    public BaseTopologyManager(final ActorSystem system,
                               final List<String> remotePaths,
                               final DataBroker dataBroker,
                               final String topologyId,
                               final TopologyManagerCallbackFactory<M> topologyManagerCallbackFactory,
                               final StateAggregator aggregator,
                               final NodeWriter naSalNodeWriter,
                               final RoleChangeStrategy roleChangeStrategy,
                               final boolean isMaster) {

        this.system = system;
        this.remotePaths = remotePaths;
        this.dataBroker = dataBroker;
        this.topologyId = topologyId;
        this.delegateTopologyHandler = topologyManagerCallbackFactory.create(system, dataBroker, topologyId, remotePaths);
        this.aggregator = aggregator;
        this.naSalNodeWriter = naSalNodeWriter;
        this.roleChangeStrategy = roleChangeStrategy;

        // election has not yet happened
        this.isMaster = isMaster;

        // TODO change to enum, master/slave active/standby
//        roleChangeStrategy.registerRoleCandidate(this);
        LOG.warn("Base manager started ", + id);
    }

    @Override public Iterable<TopologyManager<M>> getPeers() {
        // FIXME return remote proxies for all peers
        return Collections.emptySet();
    }


    @Override
    public ListenableFuture<Node> nodeCreated(final NodeId nodeId, final Node node) {
        LOG.warn("TopologyManager({}) nodeCreated received, nodeid: {}", id, nodeId.getValue());

        // TODO how to monitor the connection for failures and how to react ? what about reconnecting connections like in netconf
        ArrayList<ListenableFuture<Node>> futures = new ArrayList<>();

        if (isMaster) {
            if (peers.isEmpty()) {
                peers.addAll(createPeers(system, remotePaths));
            }
            futures.add(delegateTopologyHandler.nodeCreated(nodeId, node));
            // only master should call connect on peers and aggregate futures
            for (TopologyManager topologyManager : peers) {
                //futures.add(topologyManager.nodeCreated(nodeId, node));

                // add a future into our futures that gets its completion status from the converted scala future
                final SettableFuture<Node> settableFuture = SettableFuture.create();
                futures.add(settableFuture);
                Future<Node> scalaFuture = topologyManager.remoteNodeCreated(nodeId, node);
                scalaFuture.onComplete(new OnComplete<Node>() {
                    @Override
                    public void onComplete(Throwable failure, Node success) throws Throwable {
                        if (failure != null) {
                            settableFuture.setException(failure);
                            return;
                        }

                        settableFuture.set(success);
                    }
                }, TypedActor.context().dispatcher());
            }

            // TODO handle resyncs

            final ListenableFuture<Node> aggregatedFuture = aggregator.combineCreateAttempts(futures);
            Futures.addCallback(aggregatedFuture, new FutureCallback<Node>() {
                @Override
                public void onSuccess(final Node result) {
                    // FIXME make this (writing state data for nodes) optional and customizable
                    // this should be possible with providing your own NodeWriter implementation, maybe rename this interface?
                    LOG.debug("Futures aggregated succesfully");
                    naSalNodeWriter.update(nodeId, result);
                }

                @Override
                public void onFailure(final Throwable t) {
                    // If the combined connection attempt failed, set the node to connection failed
                    LOG.debug("Futures aggregation failed");
                    naSalNodeWriter.update(nodeId, nodes.get(nodeId).getFailedState(nodeId, node));
                    // FIXME disconnect those which succeeded
                    // just issue a delete on delegateTopologyHandler that gets handled on lower level
                }
            });

            //combine peer futures
            return aggregatedFuture;
        }

        // trigger create on this slave
        return delegateTopologyHandler.nodeCreated(nodeId, node);
    }

    @Override
    public ListenableFuture<Node> nodeUpdated(final NodeId nodeId, final Node node) {

        ArrayList<ListenableFuture<Node>> futures = new ArrayList<>();

        // Master needs to trigger nodeUpdated on peers and combine results
        if (isMaster) {
            futures.add(nodes.get(nodeId).nodeUpdated(nodeId, node));
            for (TopologyManager topology : peers) {
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
            for (TopologyManager topology : peers) {
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
        // TODO if we are master start watching peers and react to failure, implement akka Reciever
        isMaster = roleChangeDTO.isOwner();
        delegateTopologyHandler.onRoleChanged(roleChangeDTO);
    }

    @Nonnull
    @Override
    public String getTopologyId() {
        if (peers.isEmpty()) {
            peers.addAll(createPeers(system, remotePaths));
        }
        LOG.warn("My id: " + id);
        for (final TopologyManager manager : peers) {
            LOG.warn("asking peer for id");
            LOG.warn("Peer id: " + manager.getId());
        }
        return topologyId;
    }

    @Override
    public int getId() {
        return id;
    }

    @Override
    public Future<Node> remoteNodeCreated(NodeId nodeId, Node node) {
        final ListenableFuture<Node> nodeListenableFuture = nodeCreated(nodeId, node);
        final DefaultPromise<Node> promise = new DefaultPromise<>();
        Futures.addCallback(nodeListenableFuture, new FutureCallback<Node>() {
            @Override
            public void onSuccess(Node result) {
                promise.success(result);
            }

            @Override
            public void onFailure(Throwable t) {
                promise.failure(t);
            }
        });

        return promise.future();
    }

    @Override
    public Future<Node> remoteNodeUpdated(NodeId nodeId, Node node) {
        final ListenableFuture<Node> nodeListenableFuture = nodeUpdated(nodeId, node);
        final DefaultPromise<Node> promise = new DefaultPromise<>();
        Futures.addCallback(nodeListenableFuture, new FutureCallback<Node>() {
            @Override
            public void onSuccess(Node result) {
                promise.success(result);
            }

            @Override
            public void onFailure(Throwable t) {
                promise.failure(t);
            }
        });
        return promise.future();
    }

    @Override
    public Future<Void> remoteNodeDeleted(NodeId nodeId) {
        final ListenableFuture<Void> listenableFuture = nodeDeleted(nodeId);
        final DefaultPromise<Void> promise = new DefaultPromise<>();
        Futures.addCallback(listenableFuture, new FutureCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                promise.success(null);
            }

            @Override
            public void onFailure(Throwable t) {
                promise.failure(t);
            }
        });

        return promise.future();
    }

    private List<TopologyManager> createPeers(final ActorSystem system, final List<String> remotePaths) {
        final TypedActorExtension extension = TypedActor.get(system);
        final List<TopologyManager> peers = new ArrayList<>();

        for (final String path : remotePaths) {
            try {
                //TODO this needs to be async, otherwise it introduces deadlock when multiple managers want to identify peers at the same time
                LOG.warn("getting remote actor for path: " + path);
                final ActorRef actorRef = Await.result(system.actorSelection(path).
                        resolveOne(FiniteDuration.create(5l, TimeUnit.SECONDS)), Duration.create(5l, TimeUnit.SECONDS));
                //async impl
//                final Future<ActorRef> scalaFuture = system.actorSelection(path).resolveOne(FiniteDuration.create(5l, TimeUnit.SECONDS));
//                scalaFuture.onComplete(new OnComplete<ActorRef>() {
//                    @Override
//                    public void onComplete(Throwable failure, ActorRef success) throws Throwable {
//
//                    }
//                }, TypedActor.context().dispatcher());
                final TopologyManager peer = extension.typedActorOf(new TypedProps<>(TopologyManager.class, BaseTopologyManager.class), actorRef);
                peers.add(peer);
            } catch (Exception e) {
                LOG.error("Unable to get remote actor ref on path: " + path, e);
            }
        }
        return peers;
    }
}
