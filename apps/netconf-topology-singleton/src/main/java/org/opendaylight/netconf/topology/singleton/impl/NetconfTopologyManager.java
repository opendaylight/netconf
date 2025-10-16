/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.MoreExecutors;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.util.Timeout;
import org.opendaylight.aaa.encrypt.AAAEncryptionService;
import org.opendaylight.controller.cluster.ActorSystemProvider;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.DataTreeChangeListener;
import org.opendaylight.mdsal.binding.api.DataTreeModification;
import org.opendaylight.mdsal.binding.api.RpcProviderService;
import org.opendaylight.mdsal.binding.api.WriteTransaction;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.singleton.api.ClusterSingletonServiceProvider;
import org.opendaylight.mdsal.singleton.api.ServiceGroupIdentifier;
import org.opendaylight.netconf.client.NetconfClientFactory;
import org.opendaylight.netconf.client.mdsal.api.BaseNetconfSchemaProvider;
import org.opendaylight.netconf.client.mdsal.api.DeviceActionFactory;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.api.SchemaResourceManager;
import org.opendaylight.netconf.common.NetconfTimer;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologySetup;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologyUtils;
import org.opendaylight.netconf.topology.spi.NetconfClientConfigurationBuilderFactory;
import org.opendaylight.netconf.topology.spi.NetconfNodeUtils;
import org.opendaylight.netconf.topology.spi.NetconfTopologyRPCProvider;
import org.opendaylight.netconf.topology.spi.NetconfTopologySchemaAssembler;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev240911.NetconfNodeAugment;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev240911.netconf.node.augment.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.binding.DataObjectIdentifier;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.common.Uint16;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@Component(service = { }, configurationPid = "org.opendaylight.netconf.topology.singleton")
@Designate(ocd = NetconfTopologyManager.Configuration.class)
// Non-final for testing
public class NetconfTopologyManager implements DataTreeChangeListener<Node>, AutoCloseable {
    @ObjectClassDefinition
    public @interface Configuration {
        @AttributeDefinition(min = "1", description = "Name of the Network Topology instance to manage")
        String topology$_$id() default "topology-netconf";

        @AttributeDefinition(min = "0", max = "65535",
            description = "Idle time in seconds after which write transaction is cancelled automatically. If 0, "
                + "automatic cancellation is turned off.")
        int write$_$transaction$_$idle$_$timeout() default 0;
    }

    private static final Logger LOG = LoggerFactory.getLogger(NetconfTopologyManager.class);

    private final ConcurrentHashMap<DataObjectIdentifier<Node>, NetconfTopologyContext> contexts =
        new ConcurrentHashMap<>();
    private final ConcurrentHashMap<DataObjectIdentifier<Node>, Registration> clusterRegistrations =
        new ConcurrentHashMap<>();

    private final BaseNetconfSchemaProvider baseSchemaProvider;
    private final DataBroker dataBroker;
    private final ClusterSingletonServiceProvider clusterSingletonServiceProvider;
    private final NetconfTimer timer;
    private final NetconfTopologySchemaAssembler schemaAssembler;
    private final ActorSystem actorSystem;
    private final NetconfClientFactory clientFactory;
    private final String topologyId;
    private final Duration writeTxIdleTimeout;
    private final DOMMountPointService mountPointService;
    private final DeviceActionFactory deviceActionFactory;
    private final NetconfClientConfigurationBuilderFactory builderFactory;
    private final SchemaResourceManager resourceManager;

    private Registration dataChangeListenerRegistration;
    private NetconfTopologyRPCProvider rpcProvider;

    @Activate
    public NetconfTopologyManager(@Reference final BaseNetconfSchemaProvider baseSchemaProvider,
            @Reference final DataBroker dataBroker,
            @Reference final ClusterSingletonServiceProvider clusterSingletonServiceProvider,
            @Reference final NetconfTimer timer,
            @Reference final NetconfTopologySchemaAssembler schemaAssembler,
            @Reference final ActorSystemProvider actorSystemProvider,
            @Reference final NetconfClientFactory clientFactory,
            @Reference final DOMMountPointService mountPointService,
            @Reference final AAAEncryptionService encryptionService,
            @Reference final RpcProviderService rpcProviderService,
            @Reference final DeviceActionFactory deviceActionFactory,
            @Reference final SchemaResourceManager resourceManager,
            @Reference final NetconfClientConfigurationBuilderFactory builderFactory,
            final Configuration configuration) {
        this(baseSchemaProvider, dataBroker, clusterSingletonServiceProvider, timer, schemaAssembler,
            actorSystemProvider.getActorSystem(), clientFactory, mountPointService, encryptionService,
            rpcProviderService, deviceActionFactory, resourceManager, builderFactory, configuration.topology$_$id(),
            Uint16.valueOf(configuration.write$_$transaction$_$idle$_$timeout()));
    }

    @Inject
    public NetconfTopologyManager(final BaseNetconfSchemaProvider baseSchemaProvider, final DataBroker dataBroker,
            final ClusterSingletonServiceProvider clusterSingletonServiceProvider, final NetconfTimer timer,
            final NetconfTopologySchemaAssembler schemaAssembler, final ActorSystemProvider actorSystemProvider,
            final NetconfClientFactory clientFactory, final DOMMountPointService mountPointService,
            final AAAEncryptionService encryptionService, final RpcProviderService rpcProviderService,
            final DeviceActionFactory deviceActionFactory, final SchemaResourceManager resourceManager,
            final NetconfClientConfigurationBuilderFactory builderFactory) {
        this(baseSchemaProvider, dataBroker, clusterSingletonServiceProvider, timer, schemaAssembler,
            actorSystemProvider.getActorSystem(), clientFactory, mountPointService, encryptionService,
            rpcProviderService, deviceActionFactory, resourceManager, builderFactory,
            NetconfNodeUtils.DEFAULT_TOPOLOGY_NAME, Uint16.ZERO);
    }

    @SuppressFBWarnings(value = "MC_OVERRIDABLE_METHOD_CALL_IN_CONSTRUCTOR",
        justification = "Non-final for mocking, but we register for DTCL and that leaks 'this'")
    public NetconfTopologyManager(final BaseNetconfSchemaProvider baseSchemaProvider, final DataBroker dataBroker,
            final ClusterSingletonServiceProvider clusterSingletonServiceProvider, final NetconfTimer timer,
            final NetconfTopologySchemaAssembler schemaAssembler, final ActorSystem actorSystem,
            final NetconfClientFactory clientFactory, final DOMMountPointService mountPointService,
            final AAAEncryptionService encryptionService, final RpcProviderService rpcProviderService,
            final DeviceActionFactory deviceActionFactory, final SchemaResourceManager resourceManager,
            final NetconfClientConfigurationBuilderFactory builderFactory, final String topologyId,
            final Uint16 writeTransactionIdleTimeout) {
        this.baseSchemaProvider = requireNonNull(baseSchemaProvider);
        this.dataBroker = requireNonNull(dataBroker);
        this.clusterSingletonServiceProvider = requireNonNull(clusterSingletonServiceProvider);
        this.timer = requireNonNull(timer);
        this.schemaAssembler = requireNonNull(schemaAssembler);
        this.actorSystem = requireNonNull(actorSystem);
        this.clientFactory = requireNonNull(clientFactory);
        this.topologyId = requireNonNull(topologyId);
        writeTxIdleTimeout = Duration.ofSeconds(writeTransactionIdleTimeout.toJava());
        this.mountPointService = mountPointService;
        this.deviceActionFactory = requireNonNull(deviceActionFactory);
        this.resourceManager = requireNonNull(resourceManager);
        this.builderFactory = requireNonNull(builderFactory);

        dataChangeListenerRegistration = registerDataTreeChangeListener();
        rpcProvider = new NetconfTopologyRPCProvider(rpcProviderService, dataBroker, encryptionService, topologyId);
    }

    @Override
    public void onDataTreeChanged(final List<DataTreeModification<Node>> changes) {
        for (var change : changes) {
            final var rootNode = change.getRootNode();
            final var dataModifIdent = change.path();
            final var nodeId = rootNode.coerceKeyStep(Node.class).key().getNodeId();
            switch (rootNode.modificationType()) {
                case SUBTREE_MODIFIED:
                    LOG.debug("Config for node {} updated", nodeId);
                    refreshNetconfDeviceContext(dataModifIdent, rootNode.dataAfter());
                    break;
                case WRITE:
                    if (contexts.containsKey(dataModifIdent)) {
                        LOG.debug("RemoteDevice{{}} was already configured, reconfiguring node...", nodeId);
                        refreshNetconfDeviceContext(dataModifIdent, rootNode.dataAfter());
                    } else {
                        LOG.debug("Config for node {} created", nodeId);
                        startNetconfDeviceContext(dataModifIdent, rootNode.dataAfter());
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

    private void refreshNetconfDeviceContext(final DataObjectIdentifier<Node> instanceIdentifier, final Node node) {
        final var context = contexts.get(instanceIdentifier);
        context.refresh(createSetup(instanceIdentifier, node));
    }

    // ClusterSingletonServiceRegistration registerClusterSingletonService method throws a Runtime exception if there
    // are problems with registration and client has to deal with it. Only thing we can do if this error occurs is to
    // retry registration several times and log the error.
    // TODO change to a specific documented Exception when changed in ClusterSingletonServiceProvider
    @SuppressWarnings("checkstyle:IllegalCatch")
    private void startNetconfDeviceContext(final DataObjectIdentifier<Node> instanceIdentifier, final Node node) {
        final var netconfNodeAugment = requireNonNull(node.augmentation(NetconfNodeAugment.class));
        final NetconfNode netconfNode = requireNonNull(netconfNodeAugment.getNetconfNode());

        final Timeout actorResponseWaitTime = Timeout.create(
                Duration.ofSeconds(netconfNode.getActorResponseWaitTime().toJava()));

        final ServiceGroupIdentifier serviceGroupIdent = new ServiceGroupIdentifier(instanceIdentifier.toString());

        final NetconfTopologyContext newNetconfTopologyContext = newNetconfTopologyContext(
            createSetup(instanceIdentifier, node), serviceGroupIdent, actorResponseWaitTime, deviceActionFactory);

        int tries = 3;
        while (true) {
            try {
                final var clusterSingletonServiceRegistration =
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

    private void stopNetconfDeviceContext(final DataObjectIdentifier<Node> instanceIdentifier) {
        final var netconfTopologyContext = contexts.remove(instanceIdentifier);
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

    @PreDestroy
    @Deactivate
    @Override
    public void close() {
        if (rpcProvider != null) {
            rpcProvider.close();
            rpcProvider = null;
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

    private Registration registerDataTreeChangeListener() {
        final WriteTransaction wtx = dataBroker.newWriteOnlyTransaction();
        // FIXME: how does this play out with lifecycle? In a cluster, someone needs to ensure this call happens, but
        //        also we need to to make sure config -> oper is properly synchronized. Non-clustered case relies on
        //        oper being transient and perhaps on a put() instead, how do we handle that in the clustered case?
        wtx.merge(LogicalDatastoreType.OPERATIONAL, DataObjectIdentifier.builder(NetworkTopology.class)
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
        return dataBroker.registerTreeChangeListener(LogicalDatastoreType.CONFIGURATION,
            NetconfTopologyUtils.createTopologyListPath(topologyId).toBuilder().toReferenceBuilder()
                .child(Node.class)
                .build(), this);
    }

    private NetconfTopologySetup createSetup(final DataObjectIdentifier<Node> instanceIdentifier, final Node node) {
        final NetconfNode netconfNode = node.augmentation(NetconfNodeAugment.class).getNetconfNode();
        final RemoteDeviceId deviceId = NetconfNodeUtils.toRemoteDeviceId(node.getNodeId(), netconfNode);

        return NetconfTopologySetup.builder()
            .setClusterSingletonServiceProvider(clusterSingletonServiceProvider)
            .setBaseSchemaProvider(baseSchemaProvider)
            .setDataBroker(dataBroker)
            .setInstanceIdentifier(instanceIdentifier)
            .setNode(node)
            .setActorSystem(actorSystem)
            .setTimer(timer)
            .setSchemaAssembler(schemaAssembler)
            .setTopologyId(topologyId)
            .setNetconfClientFactory(clientFactory)
            .setDeviceSchemaProvider(resourceManager.getSchemaResources(netconfNode.getSchemaCacheDirectory(),
                deviceId))
            .setIdleTimeout(writeTxIdleTimeout)
            .build();
    }
}
