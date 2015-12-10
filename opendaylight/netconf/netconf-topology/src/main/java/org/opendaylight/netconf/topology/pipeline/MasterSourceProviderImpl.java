/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.pipeline;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.TypedActor;
import akka.cluster.Cluster;
import akka.cluster.Member;
import java.util.Set;
import org.opendaylight.controller.cluster.schema.provider.impl.RemoteYangTextSourceProviderImpl;
import org.opendaylight.netconf.topology.pipeline.messages.AnnounceMasterSourceProviderUp;
import org.opendaylight.netconf.topology.pipeline.messages.AnnounceClusteredDeviceSourcesResolverUp;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaRepository;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;

public class MasterSourceProviderImpl extends RemoteYangTextSourceProviderImpl
        implements MasterSourceProvider {

    private final ActorSystem actorSystem;
    private final String topologyId;
    private final String nodeId;

    public MasterSourceProviderImpl(SchemaRepository schemaRepo, Set<SourceIdentifier> providedSources, ActorSystem actorSystem, String topologyId, String nodeId) {
        super(schemaRepo, providedSources);
        this.actorSystem = actorSystem;
        this.topologyId = topologyId;
        this.nodeId = nodeId;
    }

    @Override
    public void onReceive(Object o, ActorRef actorRef) {
        if(o instanceof AnnounceClusteredDeviceSourcesResolverUp) {
            actorRef.tell(new AnnounceMasterSourceProviderUp(), TypedActor.context().self());
        }
    }

    @Override
    public void preStart() {
        Cluster cluster = Cluster.get(actorSystem);
        for(Member node : cluster.state().getMembers()) {
            if(!node.address().equals(cluster.selfAddress())) {
                final String path = node.address() + "/user/" + topologyId + "/" + nodeId + "/clusteredDeviceSourcesResolver";
                actorSystem.actorSelection(path).tell(new AnnounceMasterSourceProviderUp(), TypedActor.context().self());
            }
        }
    }
}
