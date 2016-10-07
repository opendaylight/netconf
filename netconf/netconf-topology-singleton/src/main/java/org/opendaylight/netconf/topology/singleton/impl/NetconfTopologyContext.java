/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.singleton.impl;

import static org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologyUtils.DEFAULT_SCHEMA_REPOSITORY;

import akka.actor.ActorRef;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import org.opendaylight.mdsal.singleton.common.api.ClusterSingletonService;
import org.opendaylight.mdsal.singleton.common.api.ClusterSingletonServiceRegistration;
import org.opendaylight.mdsal.singleton.common.api.ServiceGroupIdentifier;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.netconf.topology.singleton.api.RemoteDeviceConnector;
import org.opendaylight.netconf.topology.singleton.impl.actors.NetconfNodeActor;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologySetup;
import org.opendaylight.netconf.topology.singleton.impl.utils.NetconfTopologyUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class NetconfTopologyContext implements ClusterSingletonService {

    private static final Logger LOG = LoggerFactory.getLogger(NetconfTopologyContext.class);

    private final ServiceGroupIdentifier serviceGroupIdent;
    private final NetconfTopologySetup netconfTopologyDeviceSetup;
    private final ClusterSingletonServiceRegistration clusterSingletonServiceRegistration;
    private final RemoteDeviceId remoteDeviceId;
    private final RemoteDeviceConnector remoteDeviceConnector;
    private NetconfNodeManager netconfNodeManager;

    private ActorRef masterActorRef;

    NetconfTopologyContext(final NetconfTopologySetup netconfTopologyDeviceSetup) {
        this.netconfTopologyDeviceSetup = Preconditions.checkNotNull(netconfTopologyDeviceSetup);
        serviceGroupIdent =
                ServiceGroupIdentifier.create(netconfTopologyDeviceSetup.getInstanceIdentifier().toString());
        clusterSingletonServiceRegistration =
                netconfTopologyDeviceSetup.getClusterSingletonServiceProvider().registerClusterSingletonService(this);

        remoteDeviceId = NetconfTopologyUtils.createRemoteDeviceId(netconfTopologyDeviceSetup.getNode().getNodeId(),
                netconfTopologyDeviceSetup.getNode().getAugmentation(NetconfNode.class));

        remoteDeviceConnector = new RemoteDeviceConnectorImpl(netconfTopologyDeviceSetup, remoteDeviceId);

        netconfNodeManager = createNodeDeviceManager();

    }

    @Override
    public void instantiateServiceInstance() {
        LOG.warn("Master was selected: ", remoteDeviceId.getHost().getIpAddress());

        // master should not listen on netconf-node operational datastore
        netconfNodeManager.close();
        netconfNodeManager = null;


        masterActorRef = netconfTopologyDeviceSetup.getActorSystem().actorOf(NetconfNodeActor.props(
                netconfTopologyDeviceSetup, remoteDeviceId, DEFAULT_SCHEMA_REPOSITORY, DEFAULT_SCHEMA_REPOSITORY),
                            NetconfTopologyUtils.createMasterActorName(remoteDeviceId.getName()));

        remoteDeviceConnector.startRemoteDeviceConnection(masterActorRef);

    }

    @Override
    public ListenableFuture<Void> closeServiceInstance() {
        final SettableFuture<Void> settableFuture = SettableFuture.create();

        if (masterActorRef != null) {
            netconfTopologyDeviceSetup.getActorSystem().stop(masterActorRef);
        }

        // in case that master changes role to slave, new NodeDeviceManager must be created and listener registered
        netconfNodeManager = createNodeDeviceManager();

        if (remoteDeviceConnector != null) {
            remoteDeviceConnector.stopRemoteDeviceConnection();
        }
        try {
            if (clusterSingletonServiceRegistration != null) {
                clusterSingletonServiceRegistration.close();
            }
            settableFuture.set(null);
        } catch (Exception e) {
            settableFuture.setException(e);
        }
        return settableFuture;
    }

    @Override
    public ServiceGroupIdentifier getIdentifier() {
        return serviceGroupIdent;
    }

    private NetconfNodeManager createNodeDeviceManager() {
        final NetconfNodeManager ndm =
                new NetconfNodeManager(netconfTopologyDeviceSetup, remoteDeviceId, DEFAULT_SCHEMA_REPOSITORY,
                        DEFAULT_SCHEMA_REPOSITORY);
        ndm.registerDataTreeChangeListener(netconfTopologyDeviceSetup.getTopologyId(),
                netconfTopologyDeviceSetup.getNode().getKey());

        return ndm;
    }

}
