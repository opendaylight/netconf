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
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class HandlingPriorityTest {
    @Test
    public void testHandlingPriority() {
        assertEquals(0,
            HandlingPriority.HANDLE_WITH_DEFAULT_PRIORITY.compareTo(HandlingPriority.HANDLE_WITH_DEFAULT_PRIORITY));

        assertEquals(-1,
            HandlingPriority.HANDLE_WITH_DEFAULT_PRIORITY.compareTo(HandlingPriority.HANDLE_WITH_MAX_PRIORITY));
        assertEquals(1,
            HandlingPriority.HANDLE_WITH_MAX_PRIORITY.compareTo(HandlingPriority.HANDLE_WITH_DEFAULT_PRIORITY));
        assertEquals(0, new HandlingPriority(Integer.MIN_VALUE)
            .compareTo(HandlingPriority.HANDLE_WITH_DEFAULT_PRIORITY));

        HandlingPriority prio = new HandlingPriority(10);
        assertTrue(prio.increasePriority(1).compareTo(new HandlingPriority(11)) == 0);

        assertFalse(HandlingPriority.HANDLE_WITH_MAX_PRIORITY.equals(new Object()));
        assertEquals(HandlingPriority.HANDLE_WITH_MAX_PRIORITY, new HandlingPriority(Integer.MAX_VALUE));
        assertEquals(HandlingPriority.HANDLE_WITH_MAX_PRIORITY.hashCode(),
            new HandlingPriority(Integer.MAX_VALUE).hashCode());
    }
}
