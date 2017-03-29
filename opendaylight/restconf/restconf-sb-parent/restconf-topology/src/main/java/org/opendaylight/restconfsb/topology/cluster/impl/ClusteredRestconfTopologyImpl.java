/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconfsb.topology.cluster.impl;

import akka.actor.ActorContext;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.TypedActor;
import akka.actor.TypedActorExtension;
import akka.actor.TypedProps;
import akka.japi.Creator;
import akka.util.Timeout;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import javassist.ClassPool;
import javax.annotation.Nullable;
import org.opendaylight.controller.config.threadpool.ScheduledThreadPool;
import org.opendaylight.controller.config.threadpool.ThreadPool;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipService;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPointService;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.ProviderContext;
import org.opendaylight.controller.sal.binding.api.BindingAwareProvider;
import org.opendaylight.controller.sal.core.api.Broker;
import org.opendaylight.controller.sal.core.api.Provider;
import org.opendaylight.netconf.topology.NodeManagerCallback;
import org.opendaylight.netconf.topology.NodeManagerCallback.NodeManagerCallbackFactory;
import org.opendaylight.netconf.topology.TopologyManager;
import org.opendaylight.netconf.topology.TopologyManagerCallback;
import org.opendaylight.netconf.topology.TopologyManagerCallback.TopologyManagerCallbackFactory;
import org.opendaylight.netconf.topology.example.LoggingSalNodeWriter;
import org.opendaylight.netconf.topology.util.BaseTopologyManager;
import org.opendaylight.netconf.topology.util.NodeRoleChangeStrategy;
import org.opendaylight.netconf.topology.util.NodeWriter;
import org.opendaylight.netconf.topology.util.TopologyRoleChangeStrategy;
import org.opendaylight.restconfsb.communicator.impl.sender.SenderFactory;
import org.opendaylight.restconfsb.topology.cluster.impl.device.ClusteredDeviceManager;
import org.opendaylight.restconfsb.topology.cluster.impl.device.ClusteredDeviceManagerImpl;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.node.rev160511.$YangModuleInfoImpl;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.node.rev160511.RestconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.node.rev160511.node.status.Module;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.binding.data.codec.gen.impl.StreamWriterGenerator;
import org.opendaylight.yangtools.binding.data.codec.impl.BindingNormalizedNodeCodecRegistry;
import org.opendaylight.yangtools.sal.binding.generator.impl.ModuleInfoBackedContext;
import org.opendaylight.yangtools.sal.binding.generator.util.BindingRuntimeContext;
import org.opendaylight.yangtools.sal.binding.generator.util.JavassistUtils;
import org.opendaylight.yangtools.yang.binding.YangModuleInfo;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClusteredRestconfTopologyImpl implements ClusteredRestconfTopology, BindingAwareProvider, Provider, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(ClusteredRestconfTopologyImpl.class);

    private static final String TOPOLOGY_ID = "topology-restconf";

    private final BindingAwareBroker bindingAwareBroker;
    private final Broker domBroker;
    private final SenderFactory senderFactory;
    private final ScheduledThreadPool reconnectExecutor;
    private final ThreadPool processingExecutor;
    private final Timeout askTimeout;
    private final Map<NodeId, ClusteredDeviceManager> activeConnectors = new ConcurrentHashMap<>();
    private final BindingNormalizedNodeCodecRegistry codecRegistry;
    private final ActorSystem actorSystem;
    private final EntityOwnershipService entityOwnershipService;
    private TopologyManager topologyManager;
    private DOMMountPointService mountPointService = null;
    private DataBroker dataBroker = null;

    public ClusteredRestconfTopologyImpl(final BindingAwareBroker bindingAwareBroker, final Broker domBroker,
                                         final SenderFactory senderFactory,
                                         final ScheduledThreadPool reconnectExecutor,
                                         final ThreadPool processingExecutor,
                                         final ActorSystem actorSystem, final long askTimeout,
                                         final EntityOwnershipService entityOwnershipService) {

        this.bindingAwareBroker = bindingAwareBroker;
        this.domBroker = domBroker;
        this.senderFactory = senderFactory;
        this.reconnectExecutor = reconnectExecutor;
        this.processingExecutor = processingExecutor;
        this.askTimeout = new Timeout(askTimeout, TimeUnit.SECONDS);

        final ModuleInfoBackedContext moduleInfoBackedContext = ModuleInfoBackedContext.create();
        final ImmutableList<YangModuleInfo> moduleInfos = ImmutableList.of($YangModuleInfoImpl.getInstance(),
                org.opendaylight.yang.gen.v1.urn.opendaylight.restconf.sb.clustered.node.rev160615.$YangModuleInfoImpl.getInstance());
        moduleInfoBackedContext.addModuleInfos(moduleInfos);
        final Optional<SchemaContext> schemaContextOptional = moduleInfoBackedContext.tryToCreateSchemaContext();
        Preconditions.checkState(schemaContextOptional.isPresent());
        final SchemaContext topologySchemaCtx = schemaContextOptional.get();

        final JavassistUtils javassist = JavassistUtils.forClassPool(ClassPool.getDefault());
        codecRegistry = new BindingNormalizedNodeCodecRegistry(StreamWriterGenerator.create(javassist));
        codecRegistry.onBindingRuntimeContextUpdated(BindingRuntimeContext.create(moduleInfoBackedContext, topologySchemaCtx));

        this.actorSystem = actorSystem;
        this.entityOwnershipService = entityOwnershipService;
        registerToSal(this);
        LOG.warn("Clustered restconf topo started");
    }

    private void registerToSal(final ClusteredRestconfTopologyImpl provider) {
        domBroker.registerProvider(provider);
        bindingAwareBroker.registerProvider(provider);
    }

    @Override
    public void close() throws Exception {
        // close all existing connectors, delete whole topology in datastore?
        for (final ClusteredDeviceManager deviceManager : activeConnectors.values()) {
            deviceManager.unregisterMountPoint();
        }
        activeConnectors.clear();
    }

    @Override
    public void registerMasterMountPoint(final ActorContext context, final NodeId nodeId) {
        final ClusteredDeviceManager connector = activeConnectors.get(nodeId);
        if (activeConnectors.containsKey(nodeId)) {
            connector.registerMasterMountPoint(actorSystem, context);
        } else {
            LOG.warn("Connector {} not found, cancel registering mountpoint", nodeId);
        }
    }

    @Override
    public void registerSlaveMountPoint(final ActorContext context, final NodeId nodeId, final ActorRef masterRef) {
        final ClusteredDeviceManager connector = activeConnectors.get(nodeId);
        if (connector != null) {
            connector.unregisterMountPoint();
            connector.registerSlaveMountPoint(actorSystem, context, masterRef);
        } else {
            LOG.warn("Connector {} not found, cancel registering mountpoint", nodeId);
        }
    }

    @Override
    public void unregisterMountPoint(final NodeId nodeId) {
        LOG.info("Unregistering {}", nodeId.getValue());
//        Preconditions.checkState(activeConnectors.containsKey(nodeId), "Cannot unregister nonexistent mountpoint");
        if (activeConnectors.containsKey(nodeId)) {
            activeConnectors.get(nodeId).unregisterMountPoint();
        } else {
            LOG.info("Connector {} not present, ignoring", nodeId);
        }
    }

    public void onSessionInitiated(final ProviderContext session) {
        dataBroker = session.getSALService(DataBroker.class);
        final NodeWriter writer = new TopologyNodeWriter(TOPOLOGY_ID, dataBroker);
        final TypedActorExtension typedActorExtension = TypedActor.get(this.actorSystem);
        LOG.warn("Registering actor on path {}", actorSystem.name() + "/user/" + TOPOLOGY_ID);
        final TypedProps<BaseTopologyManager> props = new TypedProps<>(TopologyManager.class, new Creator<BaseTopologyManager>() {
            @Override
            public BaseTopologyManager create() throws Exception {
                return new BaseTopologyManager(actorSystem,
                        codecRegistry,
                        dataBroker,
                        TOPOLOGY_ID,
                        new TopologyCallbackFactory(ClusteredRestconfTopologyImpl.this, entityOwnershipService, writer),
                        new RestconfOperationalStateAggregator(),
                        new LoggingSalNodeWriter(writer),
                        new TopologyRoleChangeStrategy(dataBroker, entityOwnershipService, "topology-restconf", "topology-manager"));
            }
        }).withTimeout(askTimeout);
        topologyManager = typedActorExtension.typedActorOf(props, TOPOLOGY_ID);
    }

    @Override
    public void onSessionInitiated(final Broker.ProviderSession session) {
        mountPointService = session.getService(DOMMountPointService.class);
    }

    @Override
    public Collection<Provider.ProviderFunctionality> getProviderFunctionality() {
        return Collections.emptySet();
    }

    @Override
    public String getTopologyId() {
        return TOPOLOGY_ID;
    }

    @Override
    public Timeout getAskTimeout() {
        return askTimeout;
    }

    @Override
    public ListenableFuture<List<Module>> connectNode(final NodeId nodeId, final Node configNode) {
        LOG.info("Connecting RemoteDevice{{}} , with config {}", nodeId, configNode);
        final RestconfNode restconfNode = configNode.getAugmentation(RestconfNode.class);

        Preconditions.checkNotNull(restconfNode.getAddress());
        Preconditions.checkNotNull(restconfNode.getPort());

        final ClusteredDeviceManager deviceManager =
                new ClusteredDeviceManagerImpl(configNode, senderFactory, processingExecutor, reconnectExecutor, mountPointService, askTimeout);
        final ListenableFuture<List<Module>> future = deviceManager.getSupportedModules(restconfNode);//connect();

        activeConnectors.put(nodeId, deviceManager);

        Futures.addCallback(future, new FutureCallback<List<Module>>() {

            @Override
            public void onSuccess(@Nullable final List<Module> result) {
                LOG.debug("Connector for : " + nodeId.getValue() + " started succesfully");
            }

            @Override
            public void onFailure(final Throwable t) {
                LOG.error("Connector for : " + nodeId.getValue() + " failed");
                // remove this node from active connectors?
            }
        });

        return future;
    }

    @Override
    public ListenableFuture<Void> disconnectNode(final NodeId nodeId) {
        LOG.debug("Disconnecting RemoteDevice{{}}", nodeId.getValue());
        if (!activeConnectors.containsKey(nodeId)) {
            return Futures.immediateFailedFuture(new IllegalStateException("Unable to disconnect device that is not connected"));
        }

        // retrieve connection, and disconnect it
        final ClusteredDeviceManager deviceManager = activeConnectors.remove(nodeId);
        try {
            deviceManager.unregisterMountPoint();
        } catch (final Exception e) {
            throw new IllegalStateException(e);
        }
        return Futures.immediateFuture(null);
    }

    static class TopologyCallbackFactory implements TopologyManagerCallbackFactory {

        private final ClusteredRestconfTopologyImpl topology;
        private final EntityOwnershipService entityOwnershipService;
        private final NodeWriter writer;

        TopologyCallbackFactory(final ClusteredRestconfTopologyImpl topology, final EntityOwnershipService entityOwnershipService,
                                final NodeWriter writer) {
            this.topology = topology;
            this.entityOwnershipService = entityOwnershipService;
            this.writer = writer;
        }

        @Override
        public TopologyManagerCallback create(final ActorSystem actorSystem, final String topologyId) {
            return new RestconfTopologyManagerCallback(actorSystem, topologyId,
                    new NodeCallbackFactory(topology, entityOwnershipService),
                    new LoggingSalNodeWriter(writer));
        }
    }

    private static class NodeCallbackFactory implements NodeManagerCallbackFactory {

        private final ClusteredRestconfTopologyImpl topology;
        private final EntityOwnershipService entityOwnershipService;

        NodeCallbackFactory(final ClusteredRestconfTopologyImpl topology, final EntityOwnershipService entityOwnershipService) {
            this.topology = topology;
            this.entityOwnershipService = entityOwnershipService;
        }

        @Override
        public NodeManagerCallback create(final String nodeId, final String topologyId, final ActorSystem actorSystem) {
            return new RestconfNodeManagerCallback(nodeId, topologyId, actorSystem, topology,
                    new NodeRoleChangeStrategy(entityOwnershipService, "restconf-node", nodeId));
        }
    }
}
