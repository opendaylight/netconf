/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl;

import static java.util.Objects.requireNonNull;

import akka.actor.ActorSystem;
import akka.util.Timeout;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.MoreExecutors;
import io.netty.util.concurrent.EventExecutor;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.controller.cluster.ActorSystemProvider;
import org.opendaylight.controller.config.threadpool.ScheduledThreadPool;
import org.opendaylight.controller.config.threadpool.ThreadPool;
import org.opendaylight.mdsal.binding.api.ClusteredDataTreeChangeListener;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.DataObjectModification;
import org.opendaylight.mdsal.binding.api.DataTreeIdentifier;
import org.opendaylight.mdsal.binding.api.DataTreeModification;
import org.opendaylight.mdsal.binding.api.RpcProviderService;
import org.opendaylight.mdsal.binding.api.WriteTransaction;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMActionProviderService;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMRpcProviderService;
import org.opendaylight.mdsal.singleton.common.api.ClusterSingletonServiceProvider;
import org.opendaylight.mdsal.singleton.common.api.ClusterSingletonServiceRegistration;
import org.opendaylight.mdsal.singleton.common.api.ServiceGroupIdentifier;
import org.opendaylight.netconf.client.NetconfClientDispatcher;
import org.opendaylight.netconf.client.mdsal.api.BaseNetconfSchemas;
import org.opendaylight.netconf.client.mdsal.api.DeviceActionFactory;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.api.SchemaResourceManager;
import org.opendaylight.netconf.topology.singleton.api.NetconfTopologySingletonService;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologySetup;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologySetup.NetconfTopologySetupBuilder;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologyUtils;
import org.opendaylight.netconf.topology.spi.NetconfClientConfigurationBuilderFactory;
import org.opendaylight.netconf.topology.spi.NetconfNodeUtils;
import org.opendaylight.netconf.topology.spi.NetconfTopologyRPCProvider;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev221225.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev221225.NetconfNodeTopologyService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.topology.singleton.config.rev170419.Config;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetconfTopologyManager
        implements ClusteredDataTreeChangeListener<Node>, NetconfTopologySingletonService, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(NetconfTopologyManager.class);

    private final Map<InstanceIdentifier<Node>, NetconfTopologyContext> contexts = new ConcurrentHashMap<>();
    private final Map<InstanceIdentifier<Node>, ClusterSingletonServiceRegistration>
            clusterRegistrations = new ConcurrentHashMap<>();

    private final BaseNetconfSchemas baseSchemas;
    private final DataBroker dataBroker;
    private final DOMRpcProviderService rpcProviderRegistry;
    private final DOMActionProviderService actionProviderRegistry;
    private final ClusterSingletonServiceProvider clusterSingletonServiceProvider;
    private final ScheduledExecutorService keepaliveExecutorService;
    private final Executor processingExecutorService;
    private final ActorSystem actorSystem;
    private final EventExecutor eventExecutor;
    private final NetconfClientDispatcher clientDispatcher;
    private final String topologyId;
    private final Duration writeTxIdleTimeout;
    private final DOMMountPointService mountPointService;
    private final AAAEncryptionService encryptionService;
    private final RpcProviderService rpcProviderService;
    private final DeviceActionFactory deviceActionFactory;
    private final NetconfClientConfigurationBuilderFactory builderFactory;
    private final SchemaResourceManager resourceManager;

    private ListenerRegistration<NetconfTopologyManager> dataChangeListenerRegistration;
    private Registration rpcReg;


    public NetconfTopologyManager(final BaseNetconfSchemas baseSchemas, final DataBroker dataBroker,
                                  final DOMRpcProviderService rpcProviderRegistry,
                                  final DOMActionProviderService actionProviderService,
                                  final ClusterSingletonServiceProvider clusterSingletonServiceProvider,
                                  final ScheduledThreadPool keepaliveExecutor, final ThreadPool processingExecutor,
                                  final ActorSystemProvider actorSystemProvider,
                                  final EventExecutor eventExecutor, final NetconfClientDispatcher clientDispatcher,
                                  final String topologyId, final Config config,
                                  final DOMMountPointService mountPointService,
                                  final AAAEncryptionService encryptionService,
                                  final RpcProviderService rpcProviderService,
                                  final DeviceActionFactory deviceActionFactory,
                                  final SchemaResourceManager resourceManager,
                                  final NetconfClientConfigurationBuilderFactory builderFactory) {
        this.baseSchemas = requireNonNull(baseSchemas);
        this.dataBroker = requireNonNull(dataBroker);
        this.rpcProviderRegistry = requireNonNull(rpcProviderRegistry);
        actionProviderRegistry = requireNonNull(actionProviderService);
        this.clusterSingletonServiceProvider = requireNonNull(clusterSingletonServiceProvider);
        keepaliveExecutorService = keepaliveExecutor.getExecutor();
        processingExecutorService = processingExecutor.getExecutor();
        actorSystem = requireNonNull(actorSystemProvider).getActorSystem();
        this.eventExecutor = requireNonNull(eventExecutor);
        this.clientDispatcher = requireNonNull(clientDispatcher);
        this.topologyId = requireNonNull(topologyId);
        writeTxIdleTimeout = Duration.ofSeconds(config.getWriteTransactionIdleTimeout().toJava());
        this.mountPointService = mountPointService;
        this.encryptionService = requireNonNull(encryptionService);
        this.rpcProviderService = requireNonNull(rpcProviderService);
        this.deviceActionFactory = requireNonNull(deviceActionFactory);
        this.resourceManager = requireNonNull(resourceManager);
        this.builderFactory = requireNonNull(builderFactory);
    }

    // Blueprint init method
    public void init() {
        dataChangeListenerRegistration = registerDataTreeChangeListener();
        rpcReg = rpcProviderService.registerRpcImplementation(NetconfNodeTopologyService.class,
            new NetconfTopologyRPCProvider(dataBroker, encryptionService, topologyId));
    }

    @Override
    public void onDataTreeChanged(final Collection<DataTreeModification<Node>> changes) {
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

    // ClusterSingletonServiceRegistration registerClusterSingletonService method throws a Runtime exception if there
    // are problems with registration and client has to deal with it. Only thing we can do if this error occurs is to
    // retry registration several times and log the error.
    // TODO change to a specific documented Exception when changed in ClusterSingletonServiceProvider
    @SuppressWarnings("checkstyle:IllegalCatch")
    private void startNetconfDeviceContext(final InstanceIdentifier<Node> instanceIdentifier, final Node node) {
        final NetconfNode netconfNode = requireNonNull(node.augmentation(NetconfNode.class));

        final Timeout actorResponseWaitTime = Timeout.create(
                Duration.ofSeconds(netconfNode.getActorResponseWaitTime().toJava()));

        final ServiceGroupIdentifier serviceGroupIdent =
                ServiceGroupIdentifier.create(instanceIdentifier.toString());

        final NetconfTopologyContext newNetconfTopologyContext = newNetconfTopologyContext(
            createSetup(instanceIdentifier, node), serviceGroupIdent, actorResponseWaitTime, deviceActionFactory);

        int tries = 3;
        while (true) {
            try {
                final ClusterSingletonServiceRegistration clusterSingletonServiceRegistration =
                        clusterSingletonServiceProvider.registerClusterSingletonService(newNetconfTopologyContext);
                clusterRegistrations.put(instanceIdentifier, clusterSingletonServiceRegistration);
                contexts.put(instanceIdentifier, newNetconfTopologyContext);
                break;
            } catch (final RuntimeException e) {
                LOG.warn("Unable to register cluster singleton service {}, trying again", newNetconfTopologyContext, e);

                if (--tries <= 0) {
                    LOG.error("Unable to register cluster singleton service {} - done trying, closing topology context",
                            newNetconfTopologyContext, e);
                    close(newNetconfTopologyContext);
                    break;
                }
            }
        }
    }

    private void stopNetconfDeviceContext(final InstanceIdentifier<Node> instanceIdentifier) {
        final NetconfTopologyContext netconfTopologyContext = contexts.remove(instanceIdentifier);
        if (netconfTopologyContext != null) {
            close(clusterRegistrations.remove(instanceIdentifier));
            close(netconfTopologyContext);
        }
    }

    @VisibleForTesting
    protected NetconfTopologyContext newNetconfTopologyContext(final NetconfTopologySetup setup,
            final ServiceGroupIdentifier serviceGroupIdent, final Timeout actorResponseWaitTime,
            final DeviceActionFactory deviceActionFact) {
        return new NetconfTopologyContext(resourceManager, mountPointService, builderFactory, deviceActionFactory,
            actorResponseWaitTime, serviceGroupIdent, setup);
    }

    @Override
    public void close() {
        if (rpcReg != null) {
            rpcReg.close();
            rpcReg = null;
        }
        if (dataChangeListenerRegistration != null) {
            dataChangeListenerRegistration.close();
            dataChangeListenerRegistration = null;
        }

        contexts.values().forEach(NetconfTopologyManager::close);
        clusterRegistrations.values().forEach(NetconfTopologyManager::close);

        contexts.clear();
        clusterRegistrations.clear();
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    private static void close(final AutoCloseable closeable) {
        try {
            closeable.close();
        } catch (Exception e) {
            LOG.warn("Error closing {}", closeable, e);
        }
    }

    private ListenerRegistration<NetconfTopologyManager> registerDataTreeChangeListener() {
        final WriteTransaction wtx = dataBroker.newWriteOnlyTransaction();
        // FIXME: how does this play out with lifecycle? In a cluster, someone needs to ensure this call happens, but
        //        also we need to to make sure config -> oper is properly synchronized. Non-clustered case relies on
        //        oper being transient and perhaps on a put() instead, how do we handle that in the clustered case?
        wtx.merge(LogicalDatastoreType.OPERATIONAL, InstanceIdentifier.builder(NetworkTopology.class)
            .child(Topology.class, new TopologyKey(new TopologyId(topologyId)))
            .build(), new TopologyBuilder().setTopologyId(new TopologyId(topologyId)).build());
        wtx.commit().addCallback(new FutureCallback<CommitInfo>() {
            @Override
            public void onSuccess(final CommitInfo result) {
                LOG.debug("topology initialization successful");
            }

            @Override
            public void onFailure(final Throwable throwable) {
                LOG.error("Unable to initialize netconf-topology", throwable);
            }
        }, MoreExecutors.directExecutor());

        LOG.debug("Registering datastore listener");
        return dataBroker.registerDataTreeChangeListener(DataTreeIdentifier.create(LogicalDatastoreType.CONFIGURATION,
            NetconfTopologyUtils.createTopologyListPath(topologyId).child(Node.class)), this);
    }

    private NetconfTopologySetup createSetup(final InstanceIdentifier<Node> instanceIdentifier, final Node node) {
        final NetconfNode netconfNode = node.augmentation(NetconfNode.class);
        final RemoteDeviceId deviceId = NetconfNodeUtils.toRemoteDeviceId(node.getNodeId(), netconfNode);

        return NetconfTopologySetupBuilder.create()
                .setClusterSingletonServiceProvider(clusterSingletonServiceProvider)
                .setBaseSchemas(baseSchemas)
                .setDataBroker(dataBroker)
                .setInstanceIdentifier(instanceIdentifier)
                .setRpcProviderRegistry(rpcProviderRegistry)
                .setActionProviderRegistry(actionProviderRegistry)
                .setNode(node)
                .setActorSystem(actorSystem)
                .setEventExecutor(eventExecutor)
                .setKeepaliveExecutor(keepaliveExecutorService)
                .setProcessingExecutor(processingExecutorService)
                .setTopologyId(topologyId)
                .setNetconfClientDispatcher(clientDispatcher)
                .setSchemaResourceDTO(resourceManager.getSchemaResources(netconfNode.getSchemaCacheDirectory(),
                    deviceId))
                .setIdleTimeout(writeTxIdleTimeout)
                .build();
    }
}
