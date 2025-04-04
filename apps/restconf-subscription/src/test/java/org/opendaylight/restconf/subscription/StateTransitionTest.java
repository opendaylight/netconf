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

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.restconf.server.api.TransportSession;
import org.opendaylight.restconf.server.spi.SubscriptionState;
import org.opendaylight.yangtools.yang.common.Uint32;

@ExtendWith(MockitoExtension.class)
class StateTransitionTest {
    @Mock
    private TransportSession session;

    private final SubscriptionStateMachine stateMachine = new SubscriptionStateMachine();

    @BeforeEach
    void before() {
        // Checking default stating state
        stateMachine.registerSubscription(session, Uint32.ONE);
        assertEquals(SubscriptionState.START, stateMachine.lookupSubscriptionState(Uint32.ONE));
    }

    @ParameterizedTest
    @MethodSource
    void moveFromStartValid(final SubscriptionState newState) {
        assertMoveTo(newState);
    }

    private static List<Arguments> moveFromStartValid() {
        return List.of(Arguments.of(SubscriptionState.ACTIVE), Arguments.of(SubscriptionState.END));
    }

    @ParameterizedTest
    @MethodSource
    void moveFromStartInvalid(final SubscriptionState newState) {
        final var ex = assertThrows(IllegalStateException.class, () -> stateMachine.moveTo(Uint32.ONE, newState));
        assertEquals("Subscription 1 cannot transition from START to " + newState, ex.getMessage());
        assertEquals(SubscriptionState.START, stateMachine.lookupSubscriptionState(Uint32.ONE));
    }

    private static List<Arguments> moveFromStartInvalid() {
        return List.of(Arguments.of(SubscriptionState.SUSPENDED), Arguments.of(SubscriptionState.SUSPENDED));
    }

    @Test
    void moveFromActiveToEnd() {
        assertMoveTo(SubscriptionState.ACTIVE);
        assertMoveTo(SubscriptionState.END);
    }

    @Test
    void moveFromActiveToSuspededToEnd() {
        assertMoveTo(SubscriptionState.ACTIVE);
        assertMoveTo(SubscriptionState.SUSPENDED);
        assertMoveTo(SubscriptionState.END);
    }

    @ParameterizedTest
    @MethodSource
    void moveToInvalid(final SubscriptionState first, final SubscriptionState second) {
        assertMoveTo(first);
        final var ex = assertThrows(IllegalStateException.class, () -> stateMachine.moveTo(Uint32.ONE, second));
        assertEquals("Subscription 1 cannot transition from " + first + " to " + second, ex.getMessage());
        assertEquals(first, stateMachine.lookupSubscriptionState(Uint32.ONE));
    }

    private static List<Arguments> moveToInvalid() {
        return List.of(
            Arguments.of(SubscriptionState.ACTIVE, SubscriptionState.START),
            Arguments.of(SubscriptionState.END, SubscriptionState.SUSPENDED));
    }

    private void assertMoveTo(final SubscriptionState newState) {
        stateMachine.moveTo(Uint32.ONE, newState);
        assertEquals(newState, stateMachine.lookupSubscriptionState(Uint32.ONE));
    }
}
