/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology.util;

import org.opendaylight.controller.md.sal.common.api.clustering.CandidateAlreadyRegisteredException;
import org.opendaylight.controller.md.sal.common.api.clustering.Entity;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipCandidateRegistration;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipChange;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipListener;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipListenerRegistration;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipService;
import org.opendaylight.netconf.topology.RoleChangeStrategy;
import org.opendaylight.netconf.topology.NodeListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NodeRoleChangeStrategy implements RoleChangeStrategy, EntityOwnershipListener {

    private static final Logger LOG = LoggerFactory.getLogger(NodeRoleChangeStrategy.class);

    private final EntityOwnershipService entityOwnershipService;
    private final String entityType;
    private final String entityName;
    private final Entity entity;
    private NodeListener ownershipCandidate;

    private EntityOwnershipCandidateRegistration candidateRegistration = null;
    private EntityOwnershipListenerRegistration ownershipListenerRegistration = null;

    public NodeRoleChangeStrategy(final EntityOwnershipService entityOwnershipService,
                                  final String entityType,
                                  final String entityName) {
        this.entityOwnershipService = entityOwnershipService;
        this.entityType = entityType + "/" + entityName;
        this.entityName = entityName;
        this.entity = new Entity(this.entityType, entityName);
    }

    @Override
    public void registerRoleCandidate(NodeListener electionCandidate) {
        LOG.debug("Registering role candidate type: {} , name: {}", entityType, entityName);
        this.ownershipCandidate = electionCandidate;
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
        LOG.debug("Unregistering role candidate");
        if (candidateRegistration != null) {
            candidateRegistration.close();
            candidateRegistration = null;
        }
        if (ownershipListenerRegistration != null) {
            ownershipListenerRegistration.close();
            ownershipListenerRegistration = null;
        }
    }

    @Override
    public void onRoleChanged(RoleChangeDTO roleChangeDTO) {
        LOG.debug("Role was changed {}", roleChangeDTO);
        ownershipCandidate.onRoleChanged(roleChangeDTO);
    }

    @Override
    public void ownershipChanged(EntityOwnershipChange ownershipChange) {
        LOG.debug("Ownership has changed {}", ownershipChange);
        ownershipCandidate.onRoleChanged(new RoleChangeDTO(ownershipChange.wasOwner(), ownershipChange.isOwner(), ownershipChange.hasOwner()));
    }
}
