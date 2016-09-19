/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.topology;

import com.google.common.base.Optional;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.annotation.Nonnull;
import org.opendaylight.controller.md.sal.common.api.clustering.CandidateAlreadyRegisteredException;
import org.opendaylight.controller.md.sal.common.api.clustering.Entity;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipCandidateRegistration;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipChange;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipListener;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipListenerRegistration;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipService;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestingEntityOwnershipService implements EntityOwnershipService {

    private static final Logger LOG = LoggerFactory.getLogger(TestingEntityOwnershipService.class);

    private final List<EntityOwnershipListener> listeners = new ArrayList<>();
    private final ExecutorService executorService = Executors.newFixedThreadPool(1);

    private Entity entity;
    private boolean masterSet = false;

    @Override
    public EntityOwnershipCandidateRegistration registerCandidate(final Entity entity) throws CandidateAlreadyRegisteredException {
        LOG.warn("Registering Candidate");
        this.entity = entity;
        return new EntityOwnershipCandidateRegistration() {
            @Override
            public void close() {
                LOG.debug("Closing candidate registration");
            }

            @Override
            public Entity getInstance() {
                return entity;
            }
        };
    }

    @Override
    public EntityOwnershipListenerRegistration registerListener(final String entityType, final EntityOwnershipListener listener) {
        listeners.add(listener);
        if (listeners.size() == 3) {
            distributeOwnership();
        }
        return new EntityOwnershipListenerRegistration() {
            @Nonnull
            @Override
            public String getEntityType() {
                return entityType;
            }

            @Override
            public void close() {
                listeners.remove(listener);
            }

            @Override
            public EntityOwnershipListener getInstance() {
                return listener;
            }
        };
    }

    @Override
    public Optional<EntityOwnershipState> getOwnershipState(final Entity forEntity) {
        return null;
    }

    @Override
    public boolean isCandidateRegistered(@Nonnull Entity entity) {
        return true;
    }

    public void distributeOwnership() {
        LOG.debug("Distributing ownership");
        executorService.submit(new Runnable() {
            @Override
            public void run() {
                masterSet = false;
                LOG.debug("Distributing ownership for {} listeners", listeners.size());
                for (final EntityOwnershipListener listener : listeners) {
                    if (!masterSet) {
                        listener.ownershipChanged(new EntityOwnershipChange(entity, false, true, true));
                        masterSet = true;
                    } else {
                        listener.ownershipChanged(new EntityOwnershipChange(entity, false, false, true));
                    }
                }

            }
        });
    }
}
