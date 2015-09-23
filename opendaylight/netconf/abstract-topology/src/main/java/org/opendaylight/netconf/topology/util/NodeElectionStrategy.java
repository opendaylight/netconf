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
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipChange;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipListener;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipService;
import org.opendaylight.netconf.topology.ElectionStrategy;
import org.opendaylight.netconf.topology.NodeListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NodeElectionStrategy implements ElectionStrategy, EntityOwnershipListener {

    private static final Logger LOG = LoggerFactory.getLogger(NodeElectionStrategy.class);

    private final EntityOwnershipService entityOwnershipService;
    private final String entityType;
    private final String entityName;
    private NodeListener nodeManager;

    public NodeElectionStrategy(final EntityOwnershipService entityOwnershipService,
                                final String entityType,
                                final String entityName) {
        this.entityOwnershipService = entityOwnershipService;
        this.entityType = entityType;
        this.entityName = entityName;
    }

    @Override
    public void preElect(final NodeListener nodeManager) {
        this.nodeManager = nodeManager;
        try {
            entityOwnershipService.registerCandidate(new Entity(entityType, entityName));
            entityOwnershipService.registerListener(entityType, this);
        } catch (CandidateAlreadyRegisteredException e) {
            LOG.error("Candidate already registered for election", e);
            throw new IllegalStateException("Candidate already registered for election", e);
        }
    }

    @Override
    public void ownershipChanged(EntityOwnershipChange ownershipChange) {
        nodeManager.ownershipChanged(ownershipChange);
    }
}
