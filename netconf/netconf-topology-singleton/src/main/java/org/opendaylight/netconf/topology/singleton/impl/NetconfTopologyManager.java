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
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import io.netty.util.concurrent.EventExecutor;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.controller.cluster.ActorSystemProvider;
import org.opendaylight.controller.config.threadpool.ScheduledThreadPool;
import org.opendaylight.controller.config.threadpool.ThreadPool;
import org.opendaylight.mdsal.binding.api.ClusteredDataTreeChangeListener;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.DataObjectModification;
import org.opendaylight.mdsal.binding.api.DataTreeIdentifier;
import org.opendaylight.mdsal.binding.api.DataTreeModification;
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
import org.opendaylight.netconf.sal.connect.api.DeviceActionFactory;
import org.opendaylight.netconf.sal.connect.api.SchemaResourceManager;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
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

    private final Map<InstanceIdentifier<Node>, NetconfTopologyContext> contexts = new ConcurrentHashMap<>();
    private final Map<InstanceIdentifier<Node>, ClusterSingletonServiceRegistration>
            clusterRegistrations = new ConcurrentHashMap<>();

    private final DataBroker dataBroker;
    private final DOMRpcProviderService rpcProviderRegistry;
    private final DOMActionProviderService actionProviderRegistry;
    private final ClusterSingletonServiceProvider clusterSingletonServiceProvider;
    private final ScheduledExecutorService keepaliveExecutor;
    private final ListeningExecutorService processingExecutor;
    private final ActorSystem actorSystem;
    private final EventExecutor eventExecutor;
    private final NetconfClientDispatcher clientDispatcher;
    private final String topologyId;
    private final Duration writeTxIdleTimeout;
    private final DOMMountPointService mountPointService;
    private final AAAEncryptionService encryptionService;
    private final DeviceActionFactory deviceActionFactory;
    private final SchemaResourceManager resourceManager;
    private ListenerRegistration<NetconfTopologyManager> dataChangeListenerRegistration;
    private String privateKeyPath;
    private String privateKeyPassphrase;

    public NetconfTopologyManager(final DataBroker dataBroker, final DOMRpcProviderService rpcProviderRegistry,
                                  final DOMActionProviderService actionProviderService,
                                  final ClusterSingletonServiceProvider clusterSingletonServiceProvider,
                                  final ScheduledThreadPool keepaliveExecutor, final ThreadPool processingExecutor,
                                  final ActorSystemProvider actorSystemProvider,
                                  final EventExecutor eventExecutor, final NetconfClientDispatcher clientDispatcher,
                                  final String topologyId, final Config config,
                                  final DOMMountPointService mountPointService,
                                  final AAAEncryptionService encryptionService,
                                  final DeviceActionFactory deviceActionFactory,
                                  final SchemaResourceManager resourceManager) {
        this.dataBroker = requireNonNull(dataBroker);
        this.rpcProviderRegistry = requireNonNull(rpcProviderRegistry);
        this.actionProviderRegistry = requireNonNull(actionProviderService);
        this.clusterSingletonServiceProvider = requireNonNull(clusterSingletonServiceProvider);
        this.keepaliveExecutor = keepaliveExecutor.getExecutor();
        this.processingExecutor = MoreExecutors.listeningDecorator(processingExecutor.getExecutor());
        this.actorSystem = requireNonNull(actorSystemProvider).getActorSystem();
        this.eventExecutor = requireNonNull(eventExecutor);
        this.clientDispatcher = requireNonNull(clientDispatcher);
        this.topologyId = requireNonNull(topologyId);
        this.writeTxIdleTimeout = Duration.apply(config.getWriteTransactionIdleTimeout().toJava(), TimeUnit.SECONDS);
        this.mountPointService = mountPointService;
        this.encryptionService = requireNonNull(encryptionService);
        this.deviceActionFactory = requireNonNull(deviceActionFactory);
        this.resourceManager = requireNonNull(resourceManager);
    }

    // Blueprint init method
    public void init() {
        dataChangeListenerRegistration = registerDataTreeChangeListener();
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
        final NetconfNode netconfNode = node.augmentation(NetconfNode.class);
        requireNonNull(netconfNode);
        requireNonNull(netconfNode.getHost());
        requireNonNull(netconfNode.getHost().getIpAddress());

        final Timeout actorResponseWaitTime = new Timeout(
                Duration.create(netconfNode.getActorResponseWaitTime().toJava(), "seconds"));

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
        return new NetconfTopologyContext(setup, serviceGroupIdent, actorResponseWaitTime, mountPointService,
            deviceActionFact);
    }

    @Override
    public void close() {
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

    /**
     * Sets the private key path from location specified in configuration file using blueprint.
     */
    public void setPrivateKeyPath(final String privateKeyPath) {
        this.privateKeyPath = privateKeyPath;
    }

    /**
     * Sets the private key passphrase from location specified in configuration file using blueprint.
     */
    public void setPrivateKeyPassphrase(final String privateKeyPassphrase) {
        this.privateKeyPassphrase = privateKeyPassphrase;
    }

    private ListenerRegistration<NetconfTopologyManager> registerDataTreeChangeListener() {
        final WriteTransaction wtx = dataBroker.newWriteOnlyTransaction();
        initTopology(wtx, LogicalDatastoreType.CONFIGURATION);
        initTopology(wtx, LogicalDatastoreType.OPERATIONAL);
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

    private void initTopology(final WriteTransaction wtx, final LogicalDatastoreType datastoreType) {
        final NetworkTopology networkTopology = new NetworkTopologyBuilder().build();
        final InstanceIdentifier<NetworkTopology> networkTopologyId =
                InstanceIdentifier.builder(NetworkTopology.class).build();
        wtx.merge(datastoreType, networkTopologyId, networkTopology);
        final Topology topology = new TopologyBuilder().setTopologyId(new TopologyId(topologyId)).build();
        wtx.merge(datastoreType, networkTopologyId.child(Topology.class,
                new TopologyKey(new TopologyId(topologyId))), topology);
    }

    private NetconfTopologySetup createSetup(final InstanceIdentifier<Node> instanceIdentifier, final Node node) {
        final NetconfNode netconfNode = node.augmentation(NetconfNode.class);
        final RemoteDeviceId deviceId = NetconfTopologyUtils.createRemoteDeviceId(node.getNodeId(), netconfNode);

        final NetconfTopologySetupBuilder builder = NetconfTopologySetupBuilder.create()
                .setClusterSingletonServiceProvider(clusterSingletonServiceProvider)
                .setDataBroker(dataBroker)
                .setInstanceIdentifier(instanceIdentifier)
                .setRpcProviderRegistry(rpcProviderRegistry)
                .setActionProviderRegistry(actionProviderRegistry)
                .setNode(node)
                .setActorSystem(actorSystem)
                .setEventExecutor(eventExecutor)
                .setKeepaliveExecutor(keepaliveExecutor)
                .setProcessingExecutor(processingExecutor)
                .setTopologyId(topologyId)
                .setNetconfClientDispatcher(clientDispatcher)
                .setSchemaResourceDTO(resourceManager.getSchemaResources(netconfNode, deviceId))
                .setIdleTimeout(writeTxIdleTimeout)
                .setPrivateKeyPath(privateKeyPath)
                .setPrivateKeyPassphrase(privateKeyPassphrase)
                .setEncryptionService(encryptionService);

        return builder.build();
    }
}
