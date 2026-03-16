/*
 * Copyright (c) 2026 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.dagger.mdsal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.http.netconfcentral.org.ns.toaster.rev091120.DisplayString;
import org.opendaylight.yang.gen.v1.http.netconfcentral.org.ns.toaster.rev091120.Toaster;
import org.opendaylight.yang.gen.v1.http.netconfcentral.org.ns.toaster.rev091120.Toaster.ToasterStatus;
import org.opendaylight.yang.gen.v1.http.netconfcentral.org.ns.toaster.rev091120.ToasterBuilder;
import org.opendaylight.yangtools.binding.DataObjectIdentifier;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.model.api.ModuleLike;
import org.opendaylight.yangtools.yang.model.api.source.SourceIdentifier;

class MdsalDomBrokerTest {
    private static final DataObjectIdentifier<Toaster> TOASTER_DOI = DataObjectIdentifier.builder(Toaster.class)
        .build();
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
    void testReadWriteOnDataBroker() throws Exception {
        final var dataBroker = component.dataBroker();
        final var toasterOperData = new ToasterBuilder()
            .setToasterManufacturer(new DisplayString("testManu"))
            .setToasterModelNumber(new DisplayString("TestModel"))
            .setToasterStatus(ToasterStatus.Up)
            .setDarknessFactor(Uint32.TEN)
            .build();

        // Write operational data.
        final var operWriteTransaction = dataBroker.newWriteOnlyTransaction();
        operWriteTransaction.put(LogicalDatastoreType.OPERATIONAL, TOASTER_DOI, toasterOperData);
        operWriteTransaction.commit().get(2, TimeUnit.SECONDS);

        // Verify the read data matches the written data.
        try (var readTransaction = dataBroker.newReadOnlyTransaction()) {
            final var toastOperational = readTransaction.read(LogicalDatastoreType.OPERATIONAL, TOASTER_DOI)
                .get(2, TimeUnit.SECONDS)
                .orElseThrow();
            assertEquals(toasterOperData, toastOperational);
        }

        // Write configuration data.
        final var toasterConfigData = new ToasterBuilder()
            .setDarknessFactor(Uint32.ONE)
            .build();

        final var configWriteTransaction = dataBroker.newWriteOnlyTransaction();
        configWriteTransaction.put(LogicalDatastoreType.CONFIGURATION, TOASTER_DOI, toasterConfigData);
        configWriteTransaction.commit().get(2, TimeUnit.SECONDS);

        // Verify the read data matches the written data.
        try (var readTransaction = dataBroker.newReadOnlyTransaction()) {
            final var toastConfig = readTransaction.read(LogicalDatastoreType.CONFIGURATION, TOASTER_DOI)
                    .get(2, TimeUnit.SECONDS)
                    .orElseThrow();
            assertEquals(toasterConfigData, toastConfig);
        }
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
            .hasSize(44)
            .extracting(ModuleLike::getSourceIdentifier)
            .extracting(SourceIdentifier::toYangFilename)
            .containsExactlyInAnyOrder(
                "ietf-inet-types@2013-07-15.yang",
                "ietf-netconf@2011-06-01.yang",
                "odl-general-entity@2015-09-30.yang",
                "distributed-datastore-provider@2025-01-30.yang",
                "odl-controller-cds-types@2025-01-31.yang",
                "toaster@2009-11-20.yang",
                "ietf-datastores@2018-02-14.yang",
                "ietf-interfaces@2018-02-20.yang",
                "ietf-ip@2018-02-22.yang",
                "ietf-netconf-acm@2018-02-14.yang",
                "ietf-netconf-with-defaults@2011-06-01.yang",
                "ietf-network-instance@2019-01-21.yang",
                "ietf-restconf@2017-01-26.yang",
                "ietf-restconf-monitoring@2017-01-26.yang",
                "ietf-restconf-subscribed-notifications@2019-11-17.yang",
                "ietf-subscribed-notifications@2019-09-09.yang",
                "ietf-yang-library@2019-01-04.yang",
                "ietf-yang-patch@2017-02-22.yang",
                "ietf-yang-schema-mount@2019-01-14.yang",
                "ietf-yang-types@2013-07-15.yang",
                "aaa@2016-12-14.yang",
                "aaa-app-config@2017-06-19.yang",
                "aaa-cert@2015-11-26.yang",
                "aaa-cert-mdsal@2016-03-21.yang",
                "aaa-cert-rpc@2015-12-15.yang",
                "aaa-password-service-config@2017-06-19.yang",
                "iana-crypt-hash@2014-08-06.yang",
                "iana-http-versions@2026-02-04.yang",
                "iana-tls-cipher-suite-algs@2024-10-16.yang",
                "ietf-crypto-types@2024-10-10.yang",
                "ietf-http-client@2024-02-08.yang",
                "ietf-http-server@2026-02-04.yang",
                "ietf-keystore@2024-10-10.yang",
                "ietf-restconf-server@2025-12-04.yang",
                "ietf-tcp-client@2024-10-10.yang",
                "ietf-tcp-common@2024-10-10.yang",
                "ietf-tcp-server@2024-10-10.yang",
                "ietf-tls-client@2024-10-10.yang",
                "ietf-tls-common@2024-10-10.yang",
                "ietf-tls-server@2024-10-10.yang",
                "ietf-truststore@2024-10-10.yang",
                "ietf-udp-client@2025-12-16.yang",
                "ietf-udp-server@2025-12-16.yang",
                "ietf-x509-cert-to-name@2014-12-10.yang"
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
