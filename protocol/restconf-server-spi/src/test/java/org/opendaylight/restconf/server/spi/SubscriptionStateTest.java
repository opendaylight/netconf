/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.opendaylight.restconf.server.spi.RestconfStream.SubscriptionState.ACTIVE;
import static org.opendaylight.restconf.server.spi.RestconfStream.SubscriptionState.END;
import static org.opendaylight.restconf.server.spi.RestconfStream.SubscriptionState.START;
import static org.opendaylight.restconf.server.spi.RestconfStream.SubscriptionState.SUSPENDED;

import java.util.List;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.restconf.server.spi.RestconfStream.SubscriptionState;

@ExtendWith(MockitoExtension.class)
class SubscriptionStateTest {
    @ParameterizedTest
    @MethodSource
    void moveStateValid(final SubscriptionState oldState, final SubscriptionState newState) {
        assertTrue(oldState.canMoveTo(newState));
    }

    private static List<Arguments> moveStateValid() {
        return List.of(
            arguments(START, ACTIVE),
            arguments(ACTIVE, SUSPENDED),
            arguments(ACTIVE, END),
            arguments(SUSPENDED, ACTIVE),
            arguments(SUSPENDED, END));
    }

    @ParameterizedTest
    @MethodSource
    void moveStateInvalid(final SubscriptionState oldState, final SubscriptionState newState) {
        assertFalse(oldState.canMoveTo(newState));
    }

    private static List<Arguments> moveStateInvalid() {
        return List.of(
            arguments(SUSPENDED, START),
            arguments(ACTIVE, START),
            arguments(END, START),
            arguments(END, ACTIVE),
            arguments(END, SUSPENDED));
    }
}
