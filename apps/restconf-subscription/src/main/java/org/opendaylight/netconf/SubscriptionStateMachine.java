/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
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
@Component
public class SubscriptionStateMachine {
    private static final Logger LOG = LoggerFactory.getLogger(SubscriptionStateMachine.class);

    // All legal transitions from one state to another
    private static final Map<SubscriptionState, Set<SubscriptionState>> TRANSITIONS = new EnumMap<>(Map.of(
        SubscriptionState.START, Set.of(SubscriptionState.ACTIVE, SubscriptionState.END),
        SubscriptionState.ACTIVE, Set.of(SubscriptionState.SUSPENDED, SubscriptionState.END),
        SubscriptionState.SUSPENDED, Set.of(SubscriptionState.ACTIVE, SubscriptionState.END)
    ));

    private Map<TransportSession, Map<Uint32, SubscriptionState>> subscriptionStateMap = new HashMap<>();

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
        subscriptionStateMap.put(session, Map.of(subscriptionId, SubscriptionState.START));
    }

    /**
     * Moves subscription from its current state to new one assuming this transition is legal.
     *
     * @param session session from which we are taking the subscription
     * @param subscriptionId id of the subscription
     * @param toType new state assigned to subscription
     * @throws IllegalStateException if transition to a new state not legal
     * @throws NoSuchElementException if subscription was not found in subscription map
     */
    public void moveTo(final TransportSession session, final Uint32 subscriptionId, SubscriptionState toType) {
        final var transition = TRANSITIONS.get(getSubscriptionState(session, subscriptionId)).contains(toType);
        // Check if this state transition is allowed
        if (!transition) {
            throw new IllegalStateException(String.format("Illegal transition to {} state.", toType));
        }
        subscriptionStateMap.replace(session, Map.of(subscriptionId, toType));
    }

    /**
     * Retrieves state of given subscription.
     *
     * @param session session from which we are taking the subscription
     * @param subscriptionId id of the subscription
     * @return state of subscription
     * @throws NoSuchElementException if subscription was not found in subscription map
     */
    public SubscriptionState getSubscriptionState(final TransportSession session, final Uint32 subscriptionId) {
        final var currentState = subscriptionStateMap.get(session).get(subscriptionId);
        // Check if subscription exist on specified session
        if (currentState == null) {
            throw new NoSuchElementException("No such subscription was registered.");
        }
        return currentState;
    }
}
