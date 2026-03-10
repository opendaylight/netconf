/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.dagger.mdsal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opendaylight.yangtools.yang.model.api.ModuleLike;
import org.opendaylight.yangtools.yang.model.api.source.SourceIdentifier;

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
        assertNotNull(component.notificationPublishService(), "Notification publish should be initialized");
        assertNotNull(component.modelContext(), "Model context should be initialized");
        assertNotNull(component.clusterSingletonServiceProvider(), "Cluster provided should be initialized");
        assertNotNull(component.entityOwnershipService(), "Entity ownership should be initialized");
        assertNotNull(component.dataBroker(), "Data broker should be initialized");
    }

    @Test
    void testComponentLifecycleAndClosing() {
        final var domRpcRouter = component.domRpcRouter();
        assertFalse(domRpcRouter.isClosed(), "RPC Router should not be closed");
        component.close();
        assertTrue(domRpcRouter.isClosed(), "Service should be closed after component is closed");
    }

    @Test
    void testLoadedSchemaContextFromClassPath() {
        final var modelContext = component.modelContext();
        final var classPathModels = modelContext.getModules();
        assertThat(classPathModels)
            .hasSize(6)
            .extracting(ModuleLike::getSourceIdentifier)
            .extracting(SourceIdentifier::toYangFilename)
            .containsExactlyInAnyOrder(
                "ietf-inet-types@2013-07-15.yang",
                "ietf-netconf@2011-06-01.yang",
                "odl-codegen-extensions@2024-06-27.yang",
                "odl-general-entity@2015-09-30.yang",
                "distributed-datastore-provider@2025-01-30.yang",
                "odl-controller-cds-types@2025-01-31.yang"
            );

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
