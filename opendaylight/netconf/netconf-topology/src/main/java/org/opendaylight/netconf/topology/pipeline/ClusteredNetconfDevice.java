/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.pipeline;

import akka.actor.ActorContext;
import akka.actor.ActorSystem;
import akka.actor.TypedActor;
import akka.actor.TypedProps;
import akka.dispatch.OnComplete;
import akka.japi.Creator;
import com.google.common.base.Optional;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import java.text.SimpleDateFormat;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipChange;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipListener;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;
import org.opendaylight.netconf.api.NetconfMessage;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceCommunicator;
import org.opendaylight.netconf.sal.connect.api.RemoteDeviceHandler;
import org.opendaylight.netconf.sal.connect.netconf.NetconfDevice;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfDeviceCommunicator;
import org.opendaylight.netconf.sal.connect.netconf.listener.NetconfSessionPreferences;
import org.opendaylight.netconf.sal.connect.netconf.sal.NetconfDeviceRpc;
import org.opendaylight.netconf.sal.connect.netconf.schema.mapping.NetconfMessageTransformer;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yangtools.yang.common.SimpleDateFormatUtil;
import org.opendaylight.yangtools.yang.model.api.ModuleIdentifier;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaRepository;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClusteredNetconfDevice extends NetconfDevice implements EntityOwnershipListener {

    private static final Logger LOG = LoggerFactory.getLogger(ClusteredNetconfDevice.class);

    private boolean isMaster = false;
    private NetconfDeviceCommunicator listener;
    private NetconfSessionPreferences sessionPreferences;
    private SchemaRepository schemaRepo;
    private final ActorSystem actorSystem;
    private final String topologyId;
    private final String nodeId;
    private final ActorContext cachedContext;

    private MasterSourceProvider masterSourceProvider;

    public ClusteredNetconfDevice(final SchemaResourcesDTO schemaResourcesDTO, final RemoteDeviceId id, final RemoteDeviceHandler<NetconfSessionPreferences> salFacade,
                                  final ExecutorService globalProcessingExecutor, SchemaRepository schemaRepo, ActorSystem actorSystem, String topologyId, String nodeId,
                                  ActorContext cachedContext) {
        super(schemaResourcesDTO, id, salFacade, globalProcessingExecutor);
        this.schemaRepo = schemaRepo;
        this.actorSystem = actorSystem;
        this.topologyId = topologyId;
        this.nodeId = nodeId;
        this.cachedContext = cachedContext;
    }

    @Override
    public void onRemoteSessionUp(NetconfSessionPreferences remoteSessionCapabilities, NetconfDeviceCommunicator listener) {
        this.listener = listener;
        this.sessionPreferences = remoteSessionCapabilities;
        slaveSetupSchema();
    }


    @Override
    protected void handleSalInitializationSuccess(SchemaContext result, NetconfSessionPreferences remoteSessionCapabilities, DOMRpcService deviceRpc) {
        super.handleSalInitializationSuccess(result, remoteSessionCapabilities, deviceRpc);

        final Set<SourceIdentifier> sourceIds = Sets.newHashSet();
        for(ModuleIdentifier id : result.getAllModuleIdentifiers()) {
            sourceIds.add(SourceIdentifier.create(id.getName(), (SimpleDateFormatUtil.DEFAULT_DATE_REV == id.getRevision() ? Optional.<String>absent() :
                    Optional.of(SimpleDateFormatUtil.getRevisionFormat().format(id.getRevision())))));
        }

        //TODO extract string constant to util class
        MasterSourceProvider masterSourceProvider = TypedActor.get(cachedContext).typedActorOf(
                new TypedProps<>(MasterSourceProvider.class,
                        new Creator<MasterSourceProviderImpl>() {
                            @Override
                            public MasterSourceProviderImpl create() throws Exception {
                                return new MasterSourceProviderImpl(schemaRepo, sourceIds, actorSystem, topologyId, nodeId);
                            }
                        }), "masterSourceProvider");
    }

    @Override
    public void onRemoteSessionDown() {
        super.onRemoteSessionDown();
        listener = null;
        sessionPreferences = null;
    }

    private void slaveSetupSchema() {
        //TODO extract string constant to util class
        ClusteredDeviceSourcesResolver resolver = TypedActor.get(cachedContext).typedActorOf(
                new TypedProps<>(ClusteredDeviceSourcesResolver.class,
                        new Creator<ClusteredDeviceSourcesResolverImpl>() {
                            @Override
                            public ClusteredDeviceSourcesResolverImpl create() throws Exception {
                                return new ClusteredDeviceSourcesResolverImpl(topologyId, nodeId, actorSystem, schemaRegistry, sourceRegistrations);
                            }
                        }), "clusteredDeviceSourcesResolver");


        final FutureCallback<SchemaContext> schemaContextFuture = new FutureCallback<SchemaContext>() {
            @Override
            public void onSuccess(SchemaContext schemaContext) {
                LOG.debug("{}: Schema context built successfully.", id);
                ClusteredNetconfDevice.super.handleSalInitializationSuccess(
                        schemaContext, sessionPreferences, getDeviceSpecificRpc(schemaContext, listener));
            }

            @Override
            public void onFailure(Throwable throwable) {
                LOG.warn("{}: Unexpected error resolving device sources: {}", id, throwable);
                handleSalInitializationFailure(throwable, listener);
            }
        };

        resolver.getResolvedSources().onComplete(
                new OnComplete<Set<SourceIdentifier>>() {
                    @Override
                    public void onComplete(Throwable throwable, Set<SourceIdentifier> sourceIdentifiers) throws Throwable {
                        if(throwable != null) {
                            if(throwable instanceof MasterSourceProviderOnSameNodeException) {
                                //do nothing
                            } else {
                                LOG.warn("{}: Unexpected error resolving device sources: {}", id, throwable);
                                handleSalInitializationFailure(throwable, listener);
                            }
                        } else {
                            LOG.trace("{}: Trying to build schema context from {}", id, sourceIdentifiers);
                            Futures.addCallback(schemaContextFactory.createSchemaContext(sourceIdentifiers), schemaContextFuture);
                        }
                    }
                }, actorSystem.dispatcher());
    }

    private NetconfDeviceRpc getDeviceSpecificRpc(SchemaContext result, RemoteDeviceCommunicator<NetconfMessage> listener) {
        return new NetconfDeviceRpc(result, listener, new NetconfMessageTransformer(result, true));
    }

    @Override
    public void ownershipChanged(EntityOwnershipChange ownershipChange) {
        LOG.warn("Entity ownership change received {}", ownershipChange);
        if(ownershipChange.isOwner()) {
            super.onRemoteSessionUp(sessionPreferences, listener);
        } else if (ownershipChange.wasOwner()) {
            slaveSetupSchema();
        }
    }
}
