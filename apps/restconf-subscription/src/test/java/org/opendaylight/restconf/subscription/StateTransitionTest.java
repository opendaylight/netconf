/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.subscription;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.restconf.server.api.TransportSession;
import org.opendaylight.yangtools.yang.common.Uint32;

@ExtendWith(MockitoExtension.class)
class StateTransitionTest {
    @Mock
    private TransportSession session;

    private SubscriptionStateMachine subscriptionStateMachine;

    @Test
    void legalStateTransitionTest() {
        // initializing state machine
        subscriptionStateMachine = new SubscriptionStateMachine();
        subscriptionStateMachine.registerSubscription(session, Uint32.ONE);

        // Checking default stating state
        var state = subscriptionStateMachine.getSubscriptionState(Uint32.ONE);
        assertEquals(SubscriptionState.START, state);

        // Checking legal state transition
        assertEquals(true, subscriptionStateMachine.isLegalTransition(state, SubscriptionState.ACTIVE));

        // Moving state
        subscriptionStateMachine.moveTo(Uint32.ONE, SubscriptionState.ACTIVE);
        state = subscriptionStateMachine.getSubscriptionState(Uint32.ONE);
        assertEquals(SubscriptionState.ACTIVE, state);
    }

    @Test
    void illegalStateTransitionTest() {
        // initializing state machine
        subscriptionStateMachine = new SubscriptionStateMachine();
        subscriptionStateMachine.registerSubscription(session, Uint32.ONE);

        // Checking default stating state
        var state = subscriptionStateMachine.getSubscriptionState(Uint32.ONE);
        assertEquals(SubscriptionState.START, state);

        // Checking illegal state transition
        assertEquals(false, subscriptionStateMachine.isLegalTransition(state, SubscriptionState.SUSPENDED));
    }
}
