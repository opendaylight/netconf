/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.api.operations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.opendaylight.netconf.server.api.operations.HandlingPriority.HANDLE_WITH_DEFAULT_PRIORITY;
import static org.opendaylight.netconf.server.api.operations.HandlingPriority.HANDLE_WITH_MAX_PRIORITY;

import org.junit.jupiter.api.Test;

class HandlingPriorityTest {
    @Test
    @SuppressWarnings("SelfComparison")
    void testHandlingPriority() {
        assertEquals(0, HANDLE_WITH_DEFAULT_PRIORITY.compareTo(HANDLE_WITH_DEFAULT_PRIORITY));

        assertEquals(-1, HANDLE_WITH_DEFAULT_PRIORITY.compareTo(HANDLE_WITH_MAX_PRIORITY));
        assertEquals(1, HANDLE_WITH_MAX_PRIORITY.compareTo(HANDLE_WITH_DEFAULT_PRIORITY));
        assertEquals(0, new HandlingPriority(Integer.MIN_VALUE).compareTo(HANDLE_WITH_DEFAULT_PRIORITY));

        final var prio = new HandlingPriority(10);
        assertEquals(0, prio.increasePriority(1).compareTo(new HandlingPriority(11)));

        assertFalse(HANDLE_WITH_MAX_PRIORITY.equals(new Object()));
        assertEquals(HANDLE_WITH_MAX_PRIORITY, new HandlingPriority(Integer.MAX_VALUE));
        assertEquals(HANDLE_WITH_MAX_PRIORITY.hashCode(), new HandlingPriority(Integer.MAX_VALUE).hashCode());
    }
}
