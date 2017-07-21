/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.singleton.impl;

import akka.actor.ActorSystem;
import akka.util.Timeout;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import io.netty.util.concurrent.EventExecutor;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.controller.cluster.ActorSystemProvider;
import org.opendaylight.controller.config.threadpool.ScheduledThreadPool;
import org.opendaylight.controller.config.threadpool.ThreadPool;
import org.opendaylight.controller.md.sal.binding.api.ClusteredDataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataObjectModification;
import org.opendaylight.controller.md.sal.binding.api.DataTreeIdentifier;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPointService;
import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;
import org.opendaylight.mdsal.singleton.common.api.ClusterSingletonServiceProvider;
import org.opendaylight.mdsal.singleton.common.api.ClusterSingletonServiceRegistration;
import org.opendaylight.mdsal.singleton.common.api.ServiceGroupIdentifier;
import org.opendaylight.netconf.client.NetconfClientDispatcher;
import org.opendaylight.netconf.topology.singleton.api.NetconfTopologySingletonService;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologySetup;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologySetup.NetconfTopologySetupBuilder;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologyUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.topology.singleton.config.rev170419.Config;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopologyBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.concurrent.duration.Duration;

public class NetconfTopologyManager
        implements ClusteredDataTreeChangeListener<Node>, NetconfTopologySingletonService, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(NetconfTopologyManager.class);

    private final Map<InstanceIdentifier<Node>, NetconfTopologyContext> contexts = new HashMap<>();
    private final Map<InstanceIdentifier<Node>, ClusterSingletonServiceRegistration>
            clusterRegistrations = new HashMap<>();

    private final DataBroker dataBroker;
    private final RpcProviderRegistry rpcProviderRegistry;
    private final ClusterSingletonServiceProvider clusterSingletonServiceProvider;
    private final ScheduledThreadPool keepaliveExecutor;
    private final ThreadPool processingExecutor;
    private final ActorSystem actorSystem;
    private final EventExecutor eventExecutor;
    private final NetconfClientDispatcher clientDispatcher;
    private final String topologyId;
    private final Duration writeTxIdleTimeout;
    private final DOMMountPointService mountPointService;
    private final AAAEncryptionService encryptionService;
    private ListenerRegistration<NetconfTopologyManager> dataChangeListenerRegistration;

    public NetconfTopologyManager(final DataBroker dataBroker, final RpcProviderRegistry rpcProviderRegistry,
                                  final ClusterSingletonServiceProvider clusterSingletonServiceProvider,
                                  final ScheduledThreadPool keepaliveExecutor, final ThreadPool processingExecutor,
                                  final ActorSystemProvider actorSystemProvider,
                                  final EventExecutor eventExecutor, final NetconfClientDispatcher clientDispatcher,
                                  final String topologyId, final Config config,
                                  final DOMMountPointService mountPointService,
                                  final AAAEncryptionService encryptionService) {

        this.dataBroker = Preconditions.checkNotNull(dataBroker);
        this.rpcProviderRegistry = Preconditions.checkNotNull(rpcProviderRegistry);
        this.clusterSingletonServiceProvider = Preconditions.checkNotNull(clusterSingletonServiceProvider);
        this.keepaliveExecutor = Preconditions.checkNotNull(keepaliveExecutor);
        this.processingExecutor = Preconditions.checkNotNull(processingExecutor);
        this.actorSystem = Preconditions.checkNotNull(actorSystemProvider).getActorSystem();
        this.eventExecutor = Preconditions.checkNotNull(eventExecutor);
        this.clientDispatcher = Preconditions.checkNotNull(clientDispatcher);
        this.topologyId = Preconditions.checkNotNull(topologyId);
        this.writeTxIdleTimeout = Duration.apply(config.getWriteTransactionIdleTimeout(), TimeUnit.SECONDS);
        this.mountPointService = mountPointService;
        this.encryptionService = Preconditions.checkNotNull(encryptionService);

    }

    // Blueprint init method
    public void init() {
        dataChangeListenerRegistration = registerDataTreeChangeListener(topologyId);
    }

    @Override
    public void onDataTreeChanged(@Nonnull final Collection<DataTreeModification<Node>> changes) {
        for (final DataTreeModification<Node> change : changes) {
            final DataObjectModification<Node> rootNode = change.getRootNode();
            final InstanceIdentifier<Node> dataModifIdent = change.getRootPath().getRootIdentifier();
            final NodeId nodeId = NetconfTopologyUtils.getNodeId(rootNode.getIdentifier());
            switch (rootNode.getModificationType()) {
                case SUBTREE_MODIFIED:
                    LOG.debug("Config for node {} updated", nodeId);
                    refreshNetconfDeviceContext(dataModifIdent, rootNode.getDataAfter());
                    break;
                case WRITE:
                    if (contexts.containsKey(dataModifIdent)) {
                        LOG.debug("RemoteDevice{{}} was already configured, reconfiguring node...", nodeId);
                        refreshNetconfDeviceContext(dataModifIdent, rootNode.getDataAfter());
                    } else {
                        LOG.debug("Config for node {} created", nodeId);
                        startNetconfDeviceContext(dataModifIdent, rootNode.getDataAfter());
                    }
                    break;
                case DELETE:
                    LOG.debug("Config for node {} deleted", nodeId);
                    stopNetconfDeviceContext(dataModifIdent);
                    break;
                default:
                    LOG.warn("Unknown operation for {}.", nodeId);
            }
        }
    }

    private void refreshNetconfDeviceContext(final InstanceIdentifier<Node> instanceIdentifier, final Node node) {
        final NetconfTopologyContext context = contexts.get(instanceIdentifier);
        context.refresh(createSetup(instanceIdentifier, node));
    }

    private void startNetconfDeviceContext(final InstanceIdentifier<Node> instanceIdentifier, final Node node) {
        final NetconfNode netconfNode = node.getAugmentation(NetconfNode.class);
        Preconditions.checkNotNull(netconfNode);
        Preconditions.checkNotNull(netconfNode.getHost());
        Preconditions.checkNotNull(netconfNode.getHost().getIpAddress());

        final Timeout actorResponseWaitTime = new Timeout(Duration.create(netconfNode.getActorResponseWaitTime(),
                "seconds"));

        final ServiceGroupIdentifier serviceGroupIdent =
                ServiceGroupIdentifier.create(instanceIdentifier.toString());

        final NetconfTopologyContext newNetconfTopologyContext =
                new NetconfTopologyContext(createSetup(instanceIdentifier, node), serviceGroupIdent,
                        actorResponseWaitTime, mountPointService);

        final ClusterSingletonServiceRegistration clusterSingletonServiceRegistration =
                clusterSingletonServiceProvider.registerClusterSingletonService(newNetconfTopologyContext);

        clusterRegistrations.put(instanceIdentifier, clusterSingletonServiceRegistration);
        contexts.put(instanceIdentifier, newNetconfTopologyContext);
    }

    private void stopNetconfDeviceContext(final InstanceIdentifier<Node> instanceIdentifier) {
        if (contexts.containsKey(instanceIdentifier)) {
            try {
                clusterRegistrations.get(instanceIdentifier).close();
                contexts.get(instanceIdentifier).closeFinal();
            } catch (final Exception e) {
                LOG.warn("Error at closing topology context. InstanceIdentifier: " + instanceIdentifier);
            }
            contexts.remove(instanceIdentifier);
            clusterRegistrations.remove(instanceIdentifier);
        }
    }

    @Override
    public void close() {
        if (dataChangeListenerRegistration != null) {
            dataChangeListenerRegistration.close();
            dataChangeListenerRegistration = null;
        }
        contexts.forEach((instanceIdentifier, netconfTopologyContext) -> {
            try {
                netconfTopologyContext.closeFinal();
            } catch (final Exception e) {
                LOG.error("Error at closing topology context. InstanceIdentifier: " + instanceIdentifier, e);
            }
        });
        clusterRegistrations.forEach((instanceIdentifier, clusterSingletonServiceRegistration) -> {
            try {
                clusterSingletonServiceRegistration.close();
            } catch (final Exception e) {
                LOG.error("Error at unregistering from cluster. InstanceIdentifier: " + instanceIdentifier, e);
            }
        });
        contexts.clear();
        clusterRegistrations.clear();
    }

    private ListenerRegistration<NetconfTopologyManager> registerDataTreeChangeListener(final String topologyId) {
        final WriteTransaction wtx = dataBroker.newWriteOnlyTransaction();
        initTopology(wtx, LogicalDatastoreType.CONFIGURATION, topologyId);
        initTopology(wtx, LogicalDatastoreType.OPERATIONAL, topologyId);
        Futures.addCallback(wtx.submit(), new FutureCallback<Void>() {
            @Override
            public void onSuccess(final Void result) {
                LOG.debug("topology initialization successful");
            }

            @Override
            public void onFailure(@Nonnull final Throwable throwable) {
                LOG.error("Unable to initialize netconf-topology, {}", throwable);
            }
        });

        LOG.debug("Registering datastore listener");
        return dataBroker.registerDataTreeChangeListener(
                new DataTreeIdentifier<>(LogicalDatastoreType.CONFIGURATION,
                        NetconfTopologyUtils.createTopologyListPath(topologyId).child(Node.class)), this);
    }

    private void initTopology(final WriteTransaction wtx, final LogicalDatastoreType datastoreType, final String topologyId) {
        final NetworkTopology networkTopology = new NetworkTopologyBuilder().build();
        final InstanceIdentifier<NetworkTopology> networkTopologyId =
                InstanceIdentifier.builder(NetworkTopology.class).build();
        wtx.merge(datastoreType, networkTopologyId, networkTopology);
        final Topology topology = new TopologyBuilder().setTopologyId(new TopologyId(topologyId)).build();
        wtx.merge(datastoreType, networkTopologyId.child(Topology.class,
                new TopologyKey(new TopologyId(topologyId))), topology);
    }

    private NetconfTopologySetup createSetup(final InstanceIdentifier<Node> instanceIdentifier, final Node node) {
        final NetconfTopologySetupBuilder builder = NetconfTopologySetupBuilder.create()
                .setClusterSingletonServiceProvider(clusterSingletonServiceProvider)
                .setDataBroker(dataBroker)
                .setInstanceIdentifier(instanceIdentifier)
                .setRpcProviderRegistry(rpcProviderRegistry)
                .setNode(node)
                .setActorSystem(actorSystem)
                .setEventExecutor(eventExecutor)
                .setKeepaliveExecutor(keepaliveExecutor)
                .setProcessingExecutor(processingExecutor)
                .setTopologyId(topologyId)
                .setNetconfClientDispatcher(clientDispatcher)
                .setSchemaResourceDTO(NetconfTopologyUtils.setupSchemaCacheDTO(node))
                .setIdleTimeout(writeTxIdleTimeout)
                .setEncryptionService(encryptionService);

        return builder.build();
    }
}
