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
import akka.actor.PoisonPill;
import akka.actor.TypedActor;
import akka.cluster.Cluster;
import akka.cluster.Member;
import java.util.Set;
import org.opendaylight.controller.cluster.schema.provider.impl.RemoteYangTextSourceProviderImpl;
import org.opendaylight.netconf.topology.pipeline.messages.AnnounceClusteredDeviceSourcesResolverUp;
import org.opendaylight.netconf.topology.util.messages.AnnounceMasterOnSameNodeUp;
import org.opendaylight.netconf.topology.util.messages.AnnounceMasterSourceProviderUp;
import org.opendaylight.netconf.util.NetconfTopologyPathCreator;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaRepository;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MasterSourceProviderImpl extends RemoteYangTextSourceProviderImpl
        implements MasterSourceProvider {

    private static Logger LOG = LoggerFactory.getLogger(MasterSourceProviderImpl.class);

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
            LOG.debug("Received source resolver up");
            actorRef.tell(new AnnounceMasterSourceProviderUp(), TypedActor.context().self());
        }
    }

    @Override
    public void preStart() {
        Cluster cluster = Cluster.get(actorSystem);
        cluster.join(cluster.selfAddress());
        LOG.debug("Notifying members master schema source provider is up.");
        for(Member node : cluster.state().getMembers()) {
            final NetconfTopologyPathCreator pathCreator = new NetconfTopologyPathCreator(node.address().toString(),topologyId);
            final String path = pathCreator.withSuffix(nodeId).withSuffix(NetconfTopologyPathCreator.CLUSTERED_DEVICE_SOURCES_RESOLVER).build();
            if(node.address().equals(cluster.selfAddress())) {
                actorSystem.actorSelection(path).tell(new AnnounceMasterOnSameNodeUp(), TypedActor.context().self());
                actorSystem.actorSelection(path).tell(PoisonPill.getInstance(), TypedActor.context().self());
            } else {
                //TODO extract string constant to util class
                actorSystem.actorSelection(path).tell(new AnnounceMasterSourceProviderUp(), TypedActor.context().self());
            }
        }
    }
}
