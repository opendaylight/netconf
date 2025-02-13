/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.subscription;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
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

    // All legal transitions from one state to another
    private static final Map<SubscriptionState, Set<SubscriptionState>> TRANSITIONS = new EnumMap<>(Map.of(
        SubscriptionState.START, Set.of(SubscriptionState.ACTIVE, SubscriptionState.END),
        SubscriptionState.ACTIVE, Set.of(SubscriptionState.SUSPENDED, SubscriptionState.END),
        SubscriptionState.SUSPENDED, Set.of(SubscriptionState.ACTIVE, SubscriptionState.END)
    ));

    private final Map<Uint32, SessionStatePair> subscriptionStateMap = new HashMap<>();

    @Inject
    @Activate
    public SubscriptionStateMachine() {
        LOG.debug("SubscriptionStateMachine initialized");
    }

    /**
     * Register new subscription with default {@link SubscriptionState#START} state.
     *
     * @param session session on which we are registering the subscription
     * @param subscriptionId id of the newly registered subscription
     */
    public void registerSubscription(final TransportSession session, final Uint32 subscriptionId) {
        subscriptionStateMap.put(subscriptionId, new SessionStatePair(session, SubscriptionState.START));
    }

    /**
     * Moves subscription from its current state to new one assuming this transition is legal.
     *
     * @param subscriptionId id of the subscription
     * @param toType new state assigned to subscription
     * @throws IllegalStateException if transition to a new state is not legal
     */
    public void moveTo(final Uint32 subscriptionId, final SubscriptionState toType) {
        final var transition = TRANSITIONS.get(getSubscriptionState(subscriptionId)).contains(toType);
        // Check if this state transition is allowed
        if (!transition) {
            throw new IllegalStateException(String.format("Illegal transition to %s state.", toType));
        }
        subscriptionStateMap.replace(subscriptionId, new SessionStatePair(getSubscriptionSession(subscriptionId),
            toType));
    }

    /**
     * Retrieves state of given subscription.
     *
     * @param subscriptionId id of the subscription
     * @return current state of subscription
     */
    public SubscriptionState getSubscriptionState(final Uint32 subscriptionId) {
        final var currentState = subscriptionStateMap.get(subscriptionId);
        // Check if subscription exist
        if (currentState == null) {
            return null;
        }
        return currentState.state();
    }

    /**
     * Retrieves session of given subscription.
     *
     * @param subscriptionId id of the subscription
     * @return session tied to the subscription
     */
    public TransportSession getSubscriptionSession(final Uint32 subscriptionId) {
        final var currentState = subscriptionStateMap.get(subscriptionId);
        // Check if subscription exist
        if (currentState == null) {
            return null;
        }
        return currentState.session();
    }

    /**
     * Internal helper class that not meant to be used anywhere else. Used to increase readability of the code and allow
     * us store subscription session and state in one map.
     */
    private record SessionStatePair(TransportSession session, SubscriptionState state) { }
}
