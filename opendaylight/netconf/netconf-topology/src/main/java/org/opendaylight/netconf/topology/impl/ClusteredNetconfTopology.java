/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.impl;

import akka.actor.ActorSystem;
import akka.actor.TypedActor;
import akka.actor.TypedActorExtension;
import akka.actor.TypedProps;
import akka.japi.Creator;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import io.netty.util.concurrent.EventExecutor;
import java.util.Collection;
import java.util.Collections;
import javassist.ClassPool;
import org.opendaylight.controller.config.threadpool.ScheduledThreadPool;
import org.opendaylight.controller.config.threadpool.ThreadPool;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipService;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.ProviderContext;
import org.opendaylight.controller.sal.core.api.Broker;
import org.opendaylight.netconf.client.NetconfClientDispatcher;
import org.opendaylight.netconf.sal.connect.api.RemoteDevice;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceHandler;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfDeviceCommunicator;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.netconf.topology.AbstractNetconfTopology;
import org.opendaylight.netconf.topology.pipeline.ClusteredNetconfDeviceCommunicator;
import org.opendaylight.netconf.topology.NetconfTopology;
import org.opendaylight.netconf.topology.NodeManagerCallback;
import org.opendaylight.netconf.topology.NodeManagerCallback.NodeManagerCallbackFactory;
import org.opendaylight.netconf.topology.SchemaRepositoryProvider;
import org.opendaylight.netconf.topology.TopologyManager;
import org.opendaylight.netconf.topology.TopologyManagerCallback;
import org.opendaylight.netconf.topology.TopologyManagerCallback.TopologyManagerCallbackFactory;
import org.opendaylight.netconf.topology.example.LoggingSalNodeWriter;
import org.opendaylight.netconf.topology.pipeline.TopologyMountPointFacade;
import org.opendaylight.netconf.topology.pipeline.TopologyMountPointFacade.ConnectionStatusListenerRegistration;
import org.opendaylight.netconf.topology.util.BaseTopologyManager;
import org.opendaylight.netconf.topology.util.NodeRoleChangeStrategy;
import org.opendaylight.netconf.topology.util.NodeWriter;
import org.opendaylight.netconf.topology.util.TopologyRoleChangeStrategy;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.$YangModuleInfoImpl;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yangtools.binding.data.codec.gen.impl.StreamWriterGenerator;
import org.opendaylight.yangtools.binding.data.codec.impl.BindingNormalizedNodeCodecRegistry;
import org.opendaylight.yangtools.sal.binding.generator.impl.ModuleInfoBackedContext;
import org.opendaylight.yangtools.sal.binding.generator.util.BindingRuntimeContext;
import org.opendaylight.yangtools.sal.binding.generator.util.JavassistUtils;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.parser.repo.SharedSchemaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClusteredNetconfTopology extends AbstractNetconfTopology implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(ClusteredNetconfTopology.class);

    private final BindingNormalizedNodeCodecRegistry codecRegistry;

    private final ActorSystem actorSystem;
    private final EntityOwnershipService entityOwnershipService;
    private TopologyManager topologyManager;

    public ClusteredNetconfTopology(final String topologyId, final NetconfClientDispatcher clientDispatcher,
                               final BindingAwareBroker bindingAwareBroker, final Broker domBroker,
                               final EventExecutor eventExecutor, final ScheduledThreadPool keepaliveExecutor,
                               final ThreadPool processingExecutor, final SchemaRepositoryProvider schemaRepositoryProvider,
                               final ActorSystem actorSystem, final EntityOwnershipService entityOwnershipService) {
        super(topologyId, clientDispatcher,
                bindingAwareBroker, domBroker, eventExecutor,
                keepaliveExecutor, processingExecutor, schemaRepositoryProvider);

        final ModuleInfoBackedContext moduleInfoBackedContext = ModuleInfoBackedContext.create();
        moduleInfoBackedContext.addModuleInfos(Collections.singletonList($YangModuleInfoImpl.getInstance()));
        final Optional<SchemaContext> schemaContextOptional = moduleInfoBackedContext.tryToCreateSchemaContext();
        Preconditions.checkState(schemaContextOptional.isPresent());
        final SchemaContext topologySchemaCtx = schemaContextOptional.get();

        final JavassistUtils javassist = JavassistUtils.forClassPool(ClassPool.getDefault());
        codecRegistry = new BindingNormalizedNodeCodecRegistry(StreamWriterGenerator.create(javassist));
        codecRegistry.onBindingRuntimeContextUpdated(BindingRuntimeContext.create(moduleInfoBackedContext, topologySchemaCtx));

        this.actorSystem = actorSystem;
        this.entityOwnershipService = entityOwnershipService;
        registerToSal(this, this);
        LOG.warn("Clustered netconf topo started");
    }

    public SharedSchemaRepository getSchemaRepository(){
        return sharedSchemaRepository;
    }

    @Override
    public void onSessionInitiated(final ProviderContext session) {
        dataBroker = session.getSALService(DataBroker.class);
        final NodeWriter writer = new TopologyNodeWriter(topologyId, dataBroker);
        TypedActorExtension typedActorExtension = TypedActor.get(this.actorSystem);
        LOG.warn("Registering actor on path {}", actorSystem.name() + "/user/" + topologyId);
        topologyManager = typedActorExtension.typedActorOf(new TypedProps<>(TopologyManager.class, new Creator<BaseTopologyManager>() {
            @Override
            public BaseTopologyManager create() throws Exception {
                return new BaseTopologyManager(actorSystem,
                        codecRegistry,
                        dataBroker,
                        topologyId,
                        new TopologyCallbackFactory(ClusteredNetconfTopology.this, entityOwnershipService, writer),
                        new NetconfNodeOperationalDataAggregator(),
                        new LoggingSalNodeWriter(writer),
                        new TopologyRoleChangeStrategy(dataBroker, entityOwnershipService, "topology-netconf", "topology-manager"));
            }
        }), topologyId);
    }

    @Override
    protected NetconfDeviceCommunicator initCommunicator(RemoteDeviceId deviceId, RemoteDevice device) {
        return new ClusteredNetconfDeviceCommunicator(deviceId, device);
    }

    public void notifySalFacade(NodeId nodeId, SchemaContext context) {
        ClusteredNetconfDeviceCommunicator communicator = (ClusteredNetconfDeviceCommunicator) activeConnectors.get(nodeId).getCommunicator();
        RemoteDeviceHandler salFacade = activeConnectors.get(nodeId).getFacade();
        communicator.notifySalFacade(salFacade, context);
    }

    public void initSchemaDownload(NodeId nodeId) {
        ClusteredNetconfDeviceCommunicator communicator = (ClusteredNetconfDeviceCommunicator) activeConnectors.get(nodeId).getCommunicator();
        communicator.callOnDeviceUp();
    }

    @Override
    public void close() throws Exception {
        // close all existing connectors, delete whole topology in datastore?
        for (NetconfConnectorDTO connectorDTO : activeConnectors.values()) {
            connectorDTO.getCommunicator().disconnect();
        }
        activeConnectors.clear();
    }

    @Override
    protected RemoteDeviceHandler<NetconfSessionPreferences> createSalFacade(final RemoteDeviceId id, final Broker domBroker, final BindingAwareBroker bindingBroker, long defaultRequestTimeoutMillis) {
        return new TopologyMountPointFacade(id, domBroker, bindingBroker, defaultRequestTimeoutMillis);
    }

    @Override
    public void registerMountPoint(NodeId nodeId) {
        ((TopologyMountPointFacade) activeConnectors.get(nodeId).getFacade()).registerMountPoint();
    }

    @Override
    public void unregisterMountPoint(NodeId nodeId) {
        Preconditions.checkState(activeConnectors.containsKey(nodeId), "Cannot unregister nonexistent mountpoint");
        ((TopologyMountPointFacade) activeConnectors.get(nodeId).getFacade()).unregisterMountPoint();
    }

    @Override
    public ConnectionStatusListenerRegistration registerConnectionStatusListener(final NodeId node, final RemoteDeviceHandler<NetconfSessionPreferences> listener) {
        Preconditions.checkState(activeConnectors.containsKey(node), "Need to connect a node before a connection listener can be registered");
        return ((TopologyMountPointFacade) activeConnectors.get(node).getFacade()).registerConnectionStatusListener(listener);
    }

    @Override
    public Collection<ProviderFunctionality> getProviderFunctionality() {
        return Collections.emptySet();
    }

    static class TopologyCallbackFactory implements TopologyManagerCallbackFactory {

        private final NetconfTopology netconfTopology;
        private final EntityOwnershipService entityOwnershipService;
        private final NodeWriter writer;

        TopologyCallbackFactory(final NetconfTopology netconfTopology, final EntityOwnershipService entityOwnershipService, final NodeWriter writer) {
            this.netconfTopology = netconfTopology;
            this.entityOwnershipService = entityOwnershipService;
            this.writer = writer;
        }

        @Override
        public TopologyManagerCallback create(final ActorSystem actorSystem, final String topologyId) {
            return new NetconfTopologyManagerCallback(actorSystem, topologyId, new NodeCallbackFactory(netconfTopology, entityOwnershipService), new LoggingSalNodeWriter(writer));
        }
    }

    private static class NodeCallbackFactory implements NodeManagerCallbackFactory {

        private final NetconfTopology netconfTopology;
        private final EntityOwnershipService entityOwnershipService;

        NodeCallbackFactory(final NetconfTopology netconfTopology, final EntityOwnershipService entityOwnershipService) {
            this.netconfTopology = netconfTopology;
            this.entityOwnershipService = entityOwnershipService;
        }

        @Override
        public NodeManagerCallback create(final String nodeId, final String topologyId, final ActorSystem actorSystem) {
            return new NetconfNodeManagerCallback(nodeId, topologyId, actorSystem, netconfTopology, new NodeRoleChangeStrategy(entityOwnershipService, "netconf-node", nodeId));
        }
    }
}
