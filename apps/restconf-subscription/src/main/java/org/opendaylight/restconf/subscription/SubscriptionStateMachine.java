/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.subscription;

import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.server.api.TransportSession;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@Component(service = SubscriptionStateMachine.class)
public class SubscriptionStateMachine {

    private static final Logger LOG = LoggerFactory.getLogger(SubscriptionStateMachine.class);

    private final Map<Uint32, TransportSession> subscriptionStateMap = new HashMap<>();

    @Inject
    @Activate
    public SubscriptionStateMachine() {
        LOG.debug("SubscriptionStateMachine initialized");
    }

    /**
     * Register new subscription.
     *
     * @param session session on which we are registering the subscription
     * @param subscriptionId id of the newly registered subscription
     */
    @NonNullByDefault
    public void registerSubscription(final TransportSession session, final Uint32 subscriptionId) {
        subscriptionStateMap.put(subscriptionId, session);
    }

    /**
     * Retrieves session of given subscription, if present.
     *
     * @param subscriptionId id of the subscription
     * @return session tied to the subscription, or {@code null}
     */
    public @Nullable TransportSession lookupSubscriptionSession(final @NonNull Uint32 subscriptionId) {
        return subscriptionStateMap.get(subscriptionId);
    }
}
