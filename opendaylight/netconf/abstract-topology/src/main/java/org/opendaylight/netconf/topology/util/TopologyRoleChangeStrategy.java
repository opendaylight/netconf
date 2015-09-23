/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.util;

import java.util.Collection;
import javax.annotation.Nonnull;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataObjectModification;
import org.opendaylight.controller.md.sal.binding.api.DataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataTreeIdentifier;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.controller.md.sal.common.api.clustering.CandidateAlreadyRegisteredException;
import org.opendaylight.controller.md.sal.common.api.clustering.Entity;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipChange;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipService;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netconf.topology.RoleChangeStrategy;
import org.opendaylight.netconf.topology.NodeListener;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yangtools.yang.binding.Identifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier.PathArgument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TopologyRoleChangeStrategy implements RoleChangeStrategy, DataTreeChangeListener<Node> {

    private static final Logger LOG = LoggerFactory.getLogger(TopologyRoleChangeStrategy.class);

    private final DataBroker dataBroker;

    private final EntityOwnershipService entityOwnershipService;
    private NodeListener topologyManager;
    private final String entityType;
    // use topologyId as entityName
    private final String entityName;

    public TopologyRoleChangeStrategy(final DataBroker dataBroker,
                                      final EntityOwnershipService entityOwnershipService,
                                      final String entityType,
                                      final String entityName) {
        this.dataBroker = dataBroker;
        this.entityOwnershipService = entityOwnershipService;
        this.entityType = entityType;
        this.entityName = entityName;
    }

    @Override
    public void preElect(final NodeListener electionCandidate) {
        topologyManager = electionCandidate;
        try {
            entityOwnershipService.registerCandidate(new Entity(entityType, entityName));
            entityOwnershipService.registerListener(entityType, this);
        } catch (CandidateAlreadyRegisteredException e) {
            LOG.error("Candidate already registered for election", e);
            throw new IllegalStateException("Candidate already registered for election", e);
        }
    }

    @Override
    public void ownershipChanged(final EntityOwnershipChange ownershipChange) {
        if (ownershipChange.isOwner()) {
            dataBroker.registerDataTreeChangeListener(
                    new DataTreeIdentifier<>(LogicalDatastoreType.CONFIGURATION, createTopologyId(entityName).child(Node.class)), this);
        }
        topologyManager.ownershipChanged(ownershipChange);
    }

    @Override
    public void onDataTreeChanged(@Nonnull Collection<DataTreeModification<Node>> changes) {
        for (DataTreeModification<Node> change : changes) {
            final DataObjectModification<Node> rootNode = change.getRootNode();
            switch (rootNode.getModificationType()) {
                case WRITE:
                    topologyManager.nodeCreated(getNodeId(rootNode.getIdentifier()), rootNode.getDataAfter());
                case SUBTREE_MODIFIED:
                    topologyManager.nodeUpdated(getNodeId(rootNode.getIdentifier()), rootNode.getDataAfter());
                case DELETE:
                    topologyManager.nodeDeleted(getNodeId(rootNode.getIdentifier()));
            }
        }
    }

    /**
     * Determines the Netconf Node Node ID, given the node's instance
     * identifier.
     *
     * @param pathArgument Node's path arument
     * @return     NodeId for the node
     */
    private NodeId getNodeId(final PathArgument pathArgument) {
        if (pathArgument instanceof InstanceIdentifier.IdentifiableItem<?, ?>) {

            final Identifier key = ((InstanceIdentifier.IdentifiableItem) pathArgument).getKey();
            if(key instanceof NodeKey) {
                return ((NodeKey) key).getNodeId();
            }
        }
        throw new IllegalStateException("Unable to create NodeId from: " + pathArgument);
    }

    private static InstanceIdentifier<Topology> createTopologyId(final String topologyId) {
        final InstanceIdentifier<NetworkTopology> networkTopology = InstanceIdentifier.create(NetworkTopology.class);
        return networkTopology.child(Topology.class, new TopologyKey(new TopologyId(topologyId)));
    }
}
