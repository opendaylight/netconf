/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.dagger.mdsal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MdsalDomBrokerTest {
    private MdsalDomBrokerTestFactory component;

    @BeforeEach
    void setUp() {
        component = DaggerMdsalDomBrokerTestFactory.create();
    }

    @AfterEach
    void tearDown() {
        if (component != null) {
            component.close();
        }
    }

    @Test
    void testCorrectlyInitializedServices() {
        assertNotNull(component.domActionService(), "Action Service should be initialized");
        assertNotNull(component.domNotificationRouter(), "Notification Router should be initialized");
        assertNotNull(component.domRpcRouter(), "RPC Router should be initialized");
    }

    @Test
    void testComponentLifecycleAndClosing() {
        final var domRpcRouter = component.domRpcRouter();
        assertFalse(domRpcRouter.isClosed(), "RPC Router should not be closed");
        component.close();
        assertTrue(domRpcRouter.isClosed(), "Service should be closed after component is closed");
    }

    @Test
    void testSingletonScope() {
        final var router1 = component.domRpcRouter();
        final var router2 = component.domRpcRouter();
        assertSame(router1, router2, "Multiple calls should return the same instance");
    }

    @Test
    void testNewComponentIsIndependent() {
        final var router = component.domRpcRouter();
        try (var secondComponent = DaggerMdsalDomBrokerTestFactory.create()) {
            final var anotherRouter = secondComponent.domRpcRouter();
            assertNotSame(router, anotherRouter, "Different components should return different service instances");
        }
    }
}
