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
import org.opendaylight.controller.md.sal.binding.api.ClusteredDataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataObjectModification;
import org.opendaylight.controller.md.sal.binding.api.DataTreeIdentifier;
import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.controller.md.sal.common.api.clustering.CandidateAlreadyRegisteredException;
import org.opendaylight.controller.md.sal.common.api.clustering.Entity;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipCandidateRegistration;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipChange;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipListener;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipListenerRegistration;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipService;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netconf.topology.NodeListener;
import org.opendaylight.netconf.topology.RoleChangeStrategy;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TopologyRoleChangeStrategy implements RoleChangeStrategy, ClusteredDataTreeChangeListener<Node>, EntityOwnershipListener {

    private static final Logger LOG = LoggerFactory.getLogger(TopologyRoleChangeStrategy.class);

    private final DataBroker dataBroker;

    private final EntityOwnershipService entityOwnershipService;
    private NodeListener ownershipCandidate;
    private final String entityType;
    // use topologyId as entityName
    private final Entity entity;

    private EntityOwnershipCandidateRegistration candidateRegistration = null;
    private EntityOwnershipListenerRegistration ownershipListenerRegistration = null;

    private ListenerRegistration<TopologyRoleChangeStrategy> datastoreListenerRegistration;

    public TopologyRoleChangeStrategy(final DataBroker dataBroker,
                                      final EntityOwnershipService entityOwnershipService,
                                      final String entityType,
                                      final String entityName) {
        this.dataBroker = dataBroker;
        this.entityOwnershipService = entityOwnershipService;
        this.entityType = entityType;
        this.entity = new Entity(entityType, entityName);

        datastoreListenerRegistration = null;
    }

    @Override
    public void registerRoleCandidate(NodeListener electionCandidate) {
        LOG.warn("Registering candidate");
        ownershipCandidate = electionCandidate;
        try {
            if (candidateRegistration != null) {
                unregisterRoleCandidate();
            }
            candidateRegistration = entityOwnershipService.registerCandidate(entity);
            ownershipListenerRegistration = entityOwnershipService.registerListener(entityType, this);
        } catch (CandidateAlreadyRegisteredException e) {
            LOG.error("Candidate already registered for election", e);
            throw new IllegalStateException("Candidate already registered for election", e);
        }
    }

    @Override
    public void unregisterRoleCandidate() {
        candidateRegistration.close();
        candidateRegistration = null;
        ownershipListenerRegistration.close();
        ownershipListenerRegistration = null;
    }

    @Override
    public void onRoleChanged(RoleChangeDTO roleChangeDTO) {
        if (roleChangeDTO.isOwner()) {
            LOG.warn("Gained ownership of entity, registering datastore listener");

            if (datastoreListenerRegistration == null) {
                LOG.debug("Listener on path {}", TopologyUtil.createTopologyListPath(entityType).child(Node.class).getPathArguments());
                datastoreListenerRegistration = dataBroker.registerDataTreeChangeListener(
                        new DataTreeIdentifier<>(LogicalDatastoreType.CONFIGURATION, TopologyUtil.createTopologyListPath(entityType).child(Node.class)), this);
            }
        } else if (datastoreListenerRegistration != null) {
            LOG.warn("No longer owner of entity, unregistering datastore listener");
            datastoreListenerRegistration.close();
            datastoreListenerRegistration = null;
        }
        ownershipCandidate.onRoleChanged(roleChangeDTO);
    }

    @Override
    public void ownershipChanged(final EntityOwnershipChange ownershipChange) {
        onRoleChanged(new RoleChangeDTO(ownershipChange.wasOwner(), ownershipChange.isOwner(), ownershipChange.hasOwner()));
    }

    @Override
    public void onDataTreeChanged(@Nonnull Collection<DataTreeModification<Node>> changes) {
        for (DataTreeModification<Node> change : changes) {
            final DataObjectModification<Node> rootNode = change.getRootNode();
            switch (rootNode.getModificationType()) {
                case WRITE:
                    LOG.debug("Data was Created {}, {}", rootNode.getIdentifier(), rootNode.getDataAfter());
                    ownershipCandidate.onNodeCreated(TopologyUtil.getNodeId(rootNode.getIdentifier()), rootNode.getDataAfter());
                    break;
                case SUBTREE_MODIFIED:
                    LOG.debug("Data was Updated {}, {}", rootNode.getIdentifier(), rootNode.getDataAfter());
                    ownershipCandidate.onNodeUpdated(TopologyUtil.getNodeId(rootNode.getIdentifier()), rootNode.getDataAfter());
                    break;
                case DELETE:
                    LOG.debug("Data was Deleted {}", rootNode.getIdentifier());
                    ownershipCandidate.onNodeDeleted(TopologyUtil.getNodeId(rootNode.getIdentifier()));
            }
        }
    }
}
