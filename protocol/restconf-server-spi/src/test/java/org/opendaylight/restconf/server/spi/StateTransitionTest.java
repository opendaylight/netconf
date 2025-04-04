/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.opendaylight.restconf.server.spi.SubscriptionUtil.moveState;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StateTransitionTest {

    @BeforeEach
    void before() {
        // Checking default stating state
    }

    @ParameterizedTest
    @MethodSource
    void moveStateValid(final SubscriptionState oldState, final SubscriptionState newState) {
        assertMoveTo(oldState, newState);
    }

    private static List<Arguments> moveStateValid() {
        return List.of(Arguments.of(SubscriptionState.START, SubscriptionState.ACTIVE),
            Arguments.of(SubscriptionState.ACTIVE, SubscriptionState.SUSPENDED),
            Arguments.of(SubscriptionState.ACTIVE, SubscriptionState.END),
            Arguments.of(SubscriptionState.SUSPENDED, SubscriptionState.ACTIVE),
            Arguments.of(SubscriptionState.SUSPENDED, SubscriptionState.END));
    }

    @ParameterizedTest
    @MethodSource
    void moveStateInvalid(final SubscriptionState oldState, final SubscriptionState newState) {
        final var ex = assertThrows(IllegalStateException.class, () -> assertMoveTo(oldState, newState));
        assertEquals("Cannot transition from %s to %s".formatted(oldState, newState), ex.getMessage());
    }

    private static List<Arguments> moveStateInvalid() {
        return List.of(Arguments.of(SubscriptionState.SUSPENDED, SubscriptionState.START),
            Arguments.of(SubscriptionState.ACTIVE, SubscriptionState.START),
            Arguments.of(SubscriptionState.END, SubscriptionState.START),
            Arguments.of(SubscriptionState.END, SubscriptionState.ACTIVE),
            Arguments.of(SubscriptionState.END, SubscriptionState.SUSPENDED));
    }

    private void assertMoveTo(final SubscriptionState oldState, final SubscriptionState newState) {
        assertEquals(newState, moveState(oldState, newState));
    }
}
