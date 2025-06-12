/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.impl;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.doReturn;

import com.google.common.util.concurrent.MoreExecutors;
import java.net.InetSocketAddress;
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
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.parser.repo.SharedSchemaRepository;

@ExtendWith(MockitoExtension.class)
class NC1474Test {
    @Mock
    private NetconfRpcService deviceRpc;
    @Mock
    private BaseNetconfSchema baseSchema;
    @Mock
    private EffectiveModelContext modelContext;

    @Test
    void testNetconfBaseRevisionQuirkHandled() throws Exception {
        final var schemaProvider = new DefaultDeviceNetconfSchemaProvider(new SharedSchemaRepository());

        final var deviceId = new RemoteDeviceId("test", InetSocketAddress.createUnresolved("foo", 12345));

        doReturn(modelContext).when(baseSchema).modelContext();

        final var future = schemaProvider.deviceNetconfSchemaFor(deviceId, NetconfSessionPreferences.fromStrings(Set.of(
            "urn:ietf:params:xml:ns:yang:ietf-inet-types?module=ietf-inet-types&amp;revision=2013-07-15",
            "urn:ietf:params:xml:ns:netconf:base:1.0?module=ietf-netconf&amp;revision=2013-09-29")),
            deviceRpc, baseSchema, MoreExecutors.directExecutor());

        final var deviceNetconfSchema = future.get(5, TimeUnit.SECONDS);
        assertNotNull(deviceNetconfSchema);

//        doReturn(Futures.immediateFuture(source)).when(schemaRepository)
//            .getSchemaSource(any(), eq(YangTextSource.class));
//        doReturn(TEST_MODEL_FUTURE).when(contextFactory).createEffectiveModelContext(anyCollection());

//        final var namespace = ;
//        final var quirkModule = QName.create(namespace, "2013-09-29", "ietf-netconf");
//        final var setup = new SchemaSetup(schemaRepository, contextFactory, DEVICE_ID,
//            new NetconfDeviceSchemas(Set.of(quirkModule), FeatureSet.builder().build(), Set.of(), List.of()),

//
//        Futures.getDone(setup.startResolution());
//
//        final var captor = ArgumentCaptor.<Collection<SourceIdentifier>>captor();
//        verify(contextFactory).createEffectiveModelContext(captor.capture());
//        final var expected = new SourceIdentifier("ietf-netconf", Revision.of("2011-06-01"));
//        assertEquals(List.of(expected), captor.getValue());
    }
}
