
package org.opendaylight.netconf.topology.pipeline;
/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

import akka.actor.ActorSystem;
import akka.actor.TypedActor;
import akka.actor.TypedProps;
import akka.actor.UntypedActor;
import akka.cluster.Cluster;
import akka.cluster.Member;
import akka.dispatch.OnComplete;
import java.util.Set;
import org.opendaylight.controller.cluster.schema.provider.impl.RemoteSchemaProvider;
import org.opendaylight.netconf.topology.pipeline.messages.AnnounceMasterSourceProviderUp;
import org.opendaylight.netconf.topology.pipeline.messages.AnnounceClusteredDeviceSourcesResolverUp;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.PotentialSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceRegistry;
import scala.concurrent.Future;


public class ClusteredDeviceSourcesResolver extends UntypedActor {

    private final String topologyId;
    private final String nodeId;
    private final ActorSystem actorSystem;
    private final SchemaSourceRegistry schemaRegistry;

    private MasterSourceProvider remoteYangTextSourceProvider;

    public ClusteredDeviceSourcesResolver(String topologyId, String nodeId, ActorSystem actorSystem, SchemaSourceRegistry schemaRegistry) {
        this.topologyId = topologyId;
        this.nodeId = nodeId;
        this.actorSystem = actorSystem;
        this.schemaRegistry = schemaRegistry;
    }

    @Override
    public void preStart(){
        Cluster cluster = Cluster.get(actorSystem);
        for(Member node : cluster.state().getMembers()) {
            if(!node.address().equals(cluster.selfAddress())) {
                final String path = node.address() + "/user/" + topologyId + "/" + nodeId + "/masterSourceProvider";
                actorSystem.actorSelection(path).tell(new AnnounceClusteredDeviceSourcesResolverUp(), getSelf());
            }
        }
    }

    @Override
    public void onReceive(Object o) {
        if(o instanceof AnnounceMasterSourceProviderUp) {
            if(remoteYangTextSourceProvider == null) {
                remoteYangTextSourceProvider = TypedActor.get(actorSystem).typedActorOf(
                        new TypedProps<>(MasterSourceProvider.class,
                                MasterSourceProviderImpl.class), getSender());
                registerProvidedSourcesToSchemaRegistry();
            }
        }
    }

    private void registerProvidedSourcesToSchemaRegistry() {
        Future sourcesFuture = remoteYangTextSourceProvider.getProvidedSources();
        final RemoteSchemaProvider remoteProvider = new RemoteSchemaProvider(remoteYangTextSourceProvider, actorSystem.dispatcher());

        sourcesFuture.onComplete(new OnComplete<Set<SourceIdentifier>>() {
            @Override
            public void onComplete(Throwable throwable, Set<SourceIdentifier> sourceIdentifiers) throws Throwable {
                for (SourceIdentifier sourceId : sourceIdentifiers) {
                    schemaRegistry.registerSchemaSource(remoteProvider,
                            PotentialSchemaSource.create(sourceId, YangTextSchemaSource.class, PotentialSchemaSource.Costs.REMOTE_IO.getValue()));
                }
            }
        }, actorSystem.dispatcher());
    }
}
