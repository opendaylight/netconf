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

import org.junit.jupiter.api.BeforeEach;
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

    @BeforeEach
    void before() {
        // initializing state machine
        subscriptionStateMachine = new SubscriptionStateMachine();
        subscriptionStateMachine.registerSubscription(session, Uint32.ONE);

        // Checking default stating state
        assertEquals(SubscriptionState.START, subscriptionStateMachine.lookupSubscriptionState(Uint32.ONE));
    }

    @Test
    void transitionStateTest() {
        // Transition state
        subscriptionStateMachine.moveTo(Uint32.ONE, SubscriptionState.ACTIVE);
        assertEquals(SubscriptionState.ACTIVE, subscriptionStateMachine.lookupSubscriptionState(Uint32.ONE));
    }

    @Test
    void illegalTransitionStateTest() {
        // Trying illegal state transition
        assertThrows(IllegalStateException.class,
            () -> subscriptionStateMachine.withState(Uint32.ONE, SubscriptionState.START));
    }
}
