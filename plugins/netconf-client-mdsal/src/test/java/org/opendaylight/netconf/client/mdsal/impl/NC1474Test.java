/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.netconf.client.mdsal.AbstractTestModelTest;
import org.opendaylight.netconf.client.mdsal.api.BaseNetconfSchema;
import org.opendaylight.netconf.client.mdsal.api.NetconfRpcService;
import org.opendaylight.netconf.client.mdsal.api.NetconfSessionPreferences;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.source.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.api.source.YangTextSource;
import org.opendaylight.yangtools.yang.model.repo.api.EffectiveModelContextFactory;
import org.opendaylight.yangtools.yang.model.repo.api.MissingSchemaSourceException;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaRepository;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceRegistry;

@ExtendWith(MockitoExtension.class)
class NC1474Test extends AbstractTestModelTest {
    private static final RemoteDeviceId DEVICE_ID = new RemoteDeviceId("test",
        InetSocketAddress.createUnresolved("foo", 12345));
    private static final SourceIdentifier EXPECTED_ID = new SourceIdentifier("ietf-netconf", Revision.of("2011-06-01"));

    @Mock
    private NetconfRpcService deviceRpc;
    @Mock
    private BaseNetconfSchema baseSchema;
    @Mock
    private EffectiveModelContext modelContext;
    @Mock
    private EffectiveModelContextFactory contextFactory;
    @Mock
    private SchemaSourceRegistry schemaRegistry;
    @Mock
    private YangTextSource source;
    @Mock
    private SchemaRepository schemaRepository;
    @Mock
    private Registration registration;

    /**
     * Verifies the "ietf-netconf quirk": when a device advertises ietf-netconf@2013-09-29,
     * DefaultDeviceNetconfSchemaProvider must resolve and request ietf-netconf@2011-06-01 instead.
     *
     * <p>Test strategy:
     * 1) Preferences include ietf-netconf@2013-09-29 (the quirk input).
     * 2) SchemaRepository returns a YangTextSource only for 2011-06-01 and fails for any other revision.
     *    This makes failures explicit if the provider does not downgrade the revision.
     * 3) EffectiveModelContextFactory is stubbed to assert it receives exactly one SourceIdentifier:
     *    (ietf-netconf, 2011-06-01).
     * 4) If all stubs are satisfied and no exception is thrown, the quirk worked.
     */
    @Test
    void testNetconfBaseRevisionQuirkHandled() throws Exception {
        doReturn(modelContext).when(baseSchema).modelContext();

        // Repository: allow ONLY ietf-netconf@2011-06-01. Anything else fails.
        doAnswer(invocation -> {
            final var id = invocation.getArgument(0, SourceIdentifier.class);
            final var type = invocation.getArgument(1, Class.class);

            if (!YangTextSource.class.equals(type) || !EXPECTED_ID.equals(id)) {
                fail("Missing ietf-netconf with revision 2011-06-01");
            }
            return Futures.immediateFuture(source);
        }).when(schemaRepository).getSchemaSource(any(SourceIdentifier.class), eq(YangTextSource.class));

        // Factory: ensure we build the context from exactly one identifier: EXPECTED_ID.
        doAnswer(invocation -> {
            final var ids = invocation.getArgument(0, Collection.class);
            assertNotNull(ids);
            assertEquals(1, ids.size());
            assertEquals(EXPECTED_ID, ids.iterator().next());
            return TEST_MODEL_FUTURE;
        }).when(contextFactory).createEffectiveModelContext(anyCollection());
        doReturn(registration).when(schemaRegistry).registerSchemaSource(any(), any());

        final var schemaProvider = new DefaultDeviceNetconfSchemaProvider(schemaRegistry, schemaRepository,
            contextFactory);
        // Device capabilities intentionally contain ietf-netconf@2013-09-29 (the quirk trigger).
        final var prefs = NetconfSessionPreferences.fromStrings(Set.of(
            "urn:ietf:params:xml:ns:yang:ietf-inet-types?module=ietf-inet-types&amp;revision=2013-07-15",
            "urn:ietf:params:xml:ns:netconf:base:1.0?module=ietf-netconf&amp;revision=2013-09-29"));
        final var future = schemaProvider.deviceNetconfSchemaFor(
            DEVICE_ID, prefs, deviceRpc, baseSchema, MoreExecutors.directExecutor());

        final var deviceNetconfSchema = future.get(5, TimeUnit.SECONDS);
        assertNotNull(deviceNetconfSchema);
    }
}
