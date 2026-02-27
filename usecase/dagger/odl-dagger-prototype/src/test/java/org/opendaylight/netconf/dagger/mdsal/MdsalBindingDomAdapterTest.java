/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.dagger.mdsal;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

public class MdsalBindingDomAdapterTest {
    private MdsalBindingDomAdapterTestFactory component;

    @BeforeEach
    void setUp() {
        component = DaggerMdsalBindingDomAdapterTestFactory.create();
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
        assertNotNull(component.notificationPublishService(), "Notification publish should be initialized");

        EffectiveModelContext effectiveModelContext = component.modelContext();
        assertNotNull(effectiveModelContext);
    }
}
