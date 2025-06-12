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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.opendaylight.netconf.client.mdsal.AbstractTestModelTest.TEST_MODEL_FUTURE;

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
class NC1474Test {
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

    @Test
    void testNetconfBaseRevisionQuirkHandled() throws Exception {
        final var schemaProvider = new DefaultDeviceNetconfSchemaProvider(schemaRegistry, schemaRepository,
            contextFactory);
        final var deviceId = new RemoteDeviceId("test", InetSocketAddress.createUnresolved("foo", 12345));

        doReturn(modelContext).when(baseSchema).modelContext();
        final var expectedId = new SourceIdentifier("ietf-netconf", Revision.of("2011-06-01"));
        doAnswer(invocation -> {
            final var id = invocation.getArgument(0, SourceIdentifier.class);
            final var type = invocation.getArgument(1, Class.class);

            if (YangTextSource.class.equals(type) && expectedId.equals(id)) {
                return Futures.immediateFuture(source);
            }
            return Futures.immediateFailedFuture(
                new MissingSchemaSourceException(id, "Only 2011-06-01 is supported in this test"));
        }).when(schemaRepository).getSchemaSource(any(SourceIdentifier.class), eq(YangTextSource.class));
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            final var ids = (Collection<SourceIdentifier>) invocation.getArgument(0);
            assertNotNull(ids);
            assertEquals(1, ids.size());
            assertEquals(expectedId, ids.iterator().next());
            return TEST_MODEL_FUTURE;
        }).when(contextFactory).createEffectiveModelContext(anyCollection());
        doReturn(registration).when(schemaRegistry).registerSchemaSource(any(), any());

        final var prefs = NetconfSessionPreferences.fromStrings(Set.of(
            "urn:ietf:params:xml:ns:yang:ietf-inet-types?module=ietf-inet-types&amp;revision=2013-07-15",
            "urn:ietf:params:xml:ns:netconf:base:1.0?module=ietf-netconf&amp;revision=2013-09-29"));
        final var future = schemaProvider.deviceNetconfSchemaFor(
            deviceId, prefs, deviceRpc, baseSchema, MoreExecutors.directExecutor());

        final var deviceNetconfSchema = future.get(5, TimeUnit.SECONDS);
        assertNotNull(deviceNetconfSchema);
    }
}
