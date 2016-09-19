/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */


package org.opendaylight.netconf.topology.pipeline.clustered;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.TypedActor;
import akka.actor.TypedProps;
import akka.cluster.Cluster;
import akka.cluster.Member;
import akka.dispatch.Futures;
import akka.dispatch.OnComplete;
import java.util.List;
import java.util.Set;
import org.opendaylight.controller.cluster.schema.provider.impl.RemoteSchemaProvider;
import org.opendaylight.netconf.topology.pipeline.MasterSourceProviderOnSameNodeException;
import org.opendaylight.netconf.topology.pipeline.messages.AnnounceClusteredDeviceSourcesResolverUp;
import org.opendaylight.netconf.topology.util.messages.AnnounceMasterOnSameNodeUp;
import org.opendaylight.netconf.topology.util.messages.AnnounceMasterSourceProviderUp;
import org.opendaylight.netconf.util.NetconfTopologyPathCreator;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaSourceRepresentation;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.PotentialSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceRegistration;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.concurrent.Future;
import scala.concurrent.Promise;


public class ClusteredDeviceSourcesResolverImpl implements ClusteredDeviceSourcesResolver {

    private static Logger LOG = LoggerFactory.getLogger(ClusteredDeviceSourcesResolver.class);

    private final String topologyId;
    private final String nodeId;
    private final ActorSystem actorSystem;
    private final SchemaSourceRegistry schemaRegistry;
    private final List<SchemaSourceRegistration<? extends SchemaSourceRepresentation>> sourceRegistrations;

    private final Promise<Set<SourceIdentifier>> resolvedSourcesPromise;
    private MasterSourceProvider remoteYangTextSourceProvider;

    public ClusteredDeviceSourcesResolverImpl(String topologyId, String nodeId, ActorSystem actorSystem,
                                              SchemaSourceRegistry schemaRegistry,
                                              List<SchemaSourceRegistration<? extends SchemaSourceRepresentation>> sourceRegistrations) {
        this.topologyId = topologyId;
        this.nodeId = nodeId;
        this.actorSystem = actorSystem;
        this.schemaRegistry = schemaRegistry;
        this.sourceRegistrations = sourceRegistrations;
        resolvedSourcesPromise = Futures.promise();
    }

    @Override
    public void preStart(){
        Cluster cluster = Cluster.get(actorSystem);
        for(Member node : cluster.state().getMembers()) {
            if(!node.address().equals(cluster.selfAddress())) {
                final NetconfTopologyPathCreator pathCreator = new NetconfTopologyPathCreator(node.address().toString(), topologyId);
                final String path = pathCreator.withSuffix(nodeId).withSuffix(NetconfTopologyPathCreator.MASTER_SOURCE_PROVIDER).build();
                actorSystem.actorSelection(path).tell(new AnnounceClusteredDeviceSourcesResolverUp(), TypedActor.context().self());
            }
        }
    }

    @Override
    public void onReceive(Object o, ActorRef actorRef) {
        if(o instanceof AnnounceMasterSourceProviderUp) {
            if(remoteYangTextSourceProvider == null) {
                remoteYangTextSourceProvider = TypedActor.get(actorSystem).typedActorOf(
                        new TypedProps<>(MasterSourceProvider.class,
                                MasterSourceProviderImpl.class), actorRef);
                registerProvidedSourcesToSchemaRegistry();
            }
        } else if(o instanceof AnnounceMasterOnSameNodeUp) {
            resolvedSourcesPromise.failure(new MasterSourceProviderOnSameNodeException());
        }
    }

    private void registerProvidedSourcesToSchemaRegistry() {
        Future<Set<SourceIdentifier>> sourcesFuture = remoteYangTextSourceProvider.getProvidedSources();
        resolvedSourcesPromise.completeWith(sourcesFuture);
        final RemoteSchemaProvider remoteProvider = new RemoteSchemaProvider(remoteYangTextSourceProvider, actorSystem.dispatcher());

        sourcesFuture.onComplete(new OnComplete<Set<SourceIdentifier>>() {
            @Override
            public void onComplete(Throwable throwable, Set<SourceIdentifier> sourceIdentifiers) throws Throwable {
                for (SourceIdentifier sourceId : sourceIdentifiers) {
                   sourceRegistrations.add(schemaRegistry.registerSchemaSource(remoteProvider,
                           PotentialSchemaSource.create(sourceId, YangTextSchemaSource.class, PotentialSchemaSource.Costs.REMOTE_IO.getValue())));
                }
            }
        }, actorSystem.dispatcher());
    }

    @Override
    public Future<Set<SourceIdentifier>> getResolvedSources() {
        return resolvedSourcesPromise.future();
    }
}
