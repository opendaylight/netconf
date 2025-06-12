/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import com.google.common.util.concurrent.Futures;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.netconf.client.mdsal.api.NetconfDeviceSchemas;
import org.opendaylight.netconf.client.mdsal.api.NetconfSessionPreferences;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.model.api.source.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.api.source.YangTextSource;
import org.opendaylight.yangtools.yang.model.api.stmt.FeatureSet;
import org.opendaylight.yangtools.yang.model.repo.api.EffectiveModelContextFactory;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaRepository;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceRegistry;

@ExtendWith(MockitoExtension.class)
class NC1474Test {
    @Mock
    private SchemaSourceRegistry registry;
    @Mock
    private SchemaRepository repository;
    @Mock
    private EffectiveModelContextFactory contextFactory;

    @Test
    void testNetconfBaseRevisionQuirkHandled() throws Exception {
        final var schemaProvider = new DefaultDeviceNetconfSchemaProvider(registry, repository, contextFactory);



        doReturn(Futures.immediateFuture(source)).when(schemaRepository)
            .getSchemaSource(any(), eq(YangTextSource.class));
        doReturn(TEST_MODEL_FUTURE).when(contextFactory).createEffectiveModelContext(anyCollection());

        final var namespace = "urn:ietf:params:xml:ns:netconf:base:1.0";
        final var quirkModule = QName.create(namespace, "2013-09-29", "ietf-netconf");
        final var setup = new SchemaSetup(schemaRepository, contextFactory, DEVICE_ID,
            new NetconfDeviceSchemas(Set.of(quirkModule), FeatureSet.builder().build(), Set.of(), List.of()),
            NetconfSessionPreferences.fromStrings(
                Set.of(namespace + "?module=ietf-netconf&amp;revision=2013-09-29")));

        Futures.getDone(setup.startResolution());

        final var captor = ArgumentCaptor.<Collection<SourceIdentifier>>captor();
        verify(contextFactory).createEffectiveModelContext(captor.capture());
        final var expected = new SourceIdentifier("ietf-netconf", Revision.of("2011-06-01"));
        assertEquals(List.of(expected), captor.getValue());
    }
}
