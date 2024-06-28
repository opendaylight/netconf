/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.yanglib.writer;

import static org.awaitility.Awaitility.await;

import java.time.Duration;
import org.junit.Test;
import org.opendaylight.mdsal.binding.dom.adapter.test.AbstractConcurrentDataBrokerTest;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.spi.FixedDOMSchemaService;
import org.opendaylight.mdsal.eos.dom.api.DOMEntityOwnershipService;
import org.opendaylight.mdsal.eos.dom.simple.SimpleDOMEntityOwnershipService;
import org.opendaylight.mdsal.singleton.impl.EOSClusterSingletonServiceProvider;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.YangLibrary;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

//TODO: Migrate this test to JUnit5 after migrating the mdsal tests.
public class YangLibraryWriterSingletonIntegrationTest extends AbstractConcurrentDataBrokerTest {
    private final DOMEntityOwnershipService eos = new SimpleDOMEntityOwnershipService();

    @Test
    public void testIntegrationNoLegacy() throws Exception {
        testIntegration(false);
    }

    @Test
    public void testIntegrationLegacy() throws Exception {
        testIntegration(true);
    }

    private void testIntegration(final boolean writeLegacy) throws Exception {
        try (var cssProvider = new EOSClusterSingletonServiceProvider(eos)) {
            try (var singleton = new YangLibraryWriterSingleton(cssProvider, new FixedDOMSchemaService(modelContext()),
                    getDataBroker(), writeLegacy)) {
                await().atMost(Duration.ofSeconds(5)).until(this::yangLibraryExists);
            }
            await().atMost(Duration.ofSeconds(5)).until(() -> !yangLibraryExists());
        }
    }

    private boolean yangLibraryExists() throws Exception {
        try (var tx = getDataBroker().newReadOnlyTransaction()) {
            return tx.exists(LogicalDatastoreType.OPERATIONAL, InstanceIdentifier.create(YangLibrary.class)).get();
        }
    }
}
