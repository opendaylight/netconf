/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.mdsal.yang.library;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.opendaylight.mdsal.common.api.CommitInfo.emptyFluentFuture;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.WriteTransaction;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContextListener;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

public abstract class AbstractYangLibraryWriterTest {
    @Mock
    private DOMSchemaService schemaService;
    @Mock
    private DataBroker dataBroker;
    @Mock
    private WriteTransaction writeTransaction;

    YangLibraryWriter writer;

    @Before
    public void setUp() {
        doReturn(writeTransaction).when(dataBroker).newWriteOnlyTransaction();
        doNothing().when(writeTransaction).put(eq(LogicalDatastoreType.OPERATIONAL), any(), any());
        doReturn(emptyFluentFuture()).when(writeTransaction).commit();
        // FIXME: use a mock for this
        doReturn(new ListenerRegistration<EffectiveModelContextListener>() {
            @Override
            public void close() {

            }

            @Override
            public EffectiveModelContextListener getInstance() {
                return null;
            }
        }).when(schemaService).registerSchemaContextListener(any());
        writer = new YangLibraryWriter(schemaService, dataBroker);
    }

    @Test
    public void testNoUpdate() {
        writer.onModelContextUpdated(YangParserTestUtils.parseYangResources(YangLibraryTest.class,
            "/test-module.yang", "/test-submodule.yang"));
        verifyNoInteractions(dataBroker);
    }

    final <T extends DataObject> void assertOperationalUpdate(final InstanceIdentifier<T> path, final T object) {
        writer.onModelContextUpdated(YangParserTestUtils.parseYangResources(YangLibraryTest.class,
            "/test-module.yang", "/test-submodule.yang", "/ietf-yang-library.yang"));
        verify(writeTransaction).put(LogicalDatastoreType.OPERATIONAL, path, object);
        verify(writeTransaction).commit();
    }
}
