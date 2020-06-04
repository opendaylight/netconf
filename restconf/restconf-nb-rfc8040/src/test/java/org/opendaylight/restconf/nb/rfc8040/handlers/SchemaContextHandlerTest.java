/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.handlers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.FileNotFoundException;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.netconf.api.tx.NetconfOperationDOMTransactionChain;
import org.opendaylight.restconf.nb.rfc8040.TestRestconfUtils;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

/**
 * Tests for handling {@link SchemaContext}.
 */
@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class SchemaContextHandlerTest {

    private static final String PATH_FOR_ACTUAL_SCHEMA_CONTEXT = "/modules";
    private static final String PATH_FOR_NEW_SCHEMA_CONTEXT = "/modules/modules-behind-mount-point";

    private static EffectiveModelContext SCHEMA_CONTEXT;

    private SchemaContextHandler schemaContextHandler;

    @Mock
    private DOMSchemaService mockDOMSchemaService;

    @BeforeClass
    public static void beforeClass() throws FileNotFoundException {
        SCHEMA_CONTEXT = YangParserTestUtils.parseYangFiles(
            TestRestconfUtils.loadFiles(PATH_FOR_ACTUAL_SCHEMA_CONTEXT));
    }

    @AfterClass
    public static void afterClass() {
        SCHEMA_CONTEXT = null;
    }

    @Before
    public void setup() throws Exception {
        final TransactionChainHandler txHandler = mock(TransactionChainHandler.class);
        final NetconfOperationDOMTransactionChain domTx = mock(NetconfOperationDOMTransactionChain.class);
        when(txHandler.get()).thenReturn(domTx);
        final DOMDataTreeWriteTransaction wTx = mock(DOMDataTreeWriteTransaction.class);
        when(domTx.newWriteOnlyTransaction()).thenReturn(wTx);
        doReturn(CommitInfo.emptyFluentFuture()).when(wTx).commit();

        this.schemaContextHandler = new SchemaContextHandler(txHandler, mockDOMSchemaService);

        this.schemaContextHandler.onModelContextUpdated(SchemaContextHandlerTest.SCHEMA_CONTEXT);
    }

    /**
     * Testing init and close.
     */
    @Test
    public void testInitAndClose() {
        ListenerRegistration<?> mockListenerReg = mock(ListenerRegistration.class);
        doReturn(mockListenerReg).when(mockDOMSchemaService)
            .registerSchemaContextListener(schemaContextHandler);

        schemaContextHandler.init();

        verify(mockDOMSchemaService).registerSchemaContextListener(schemaContextHandler);

        schemaContextHandler.close();

        verify(mockListenerReg).close();
    }

    /**
     * Test getting actual {@link SchemaContext}.
     *
     * <p>
     * Get <code>SchemaContext</code> from <code>SchemaContextHandler</code> and compare it to actual
     * <code>SchemaContext</code>.
     */
    @Test
    public void getSchemaContextTest() {
        assertEquals("SchemaContextHandler should has reference to actual SchemaContext",
                SCHEMA_CONTEXT, this.schemaContextHandler.get());
    }

    /**
     * Test updating of {@link SchemaContext}.
     *
     * <p>
     * Create new <code>SchemaContext</code>, set it to <code>SchemaContextHandler</code> and check if
     * <code>SchemaContextHandler</code> reference to new <code>SchemaContext</code> instead of old one.
     */
    @Test
    public void onGlobalContextUpdateTest() throws Exception {
        // create new SchemaContext and update SchemaContextHandler
        final EffectiveModelContext newSchemaContext =
                YangParserTestUtils.parseYangFiles(TestRestconfUtils.loadFiles(PATH_FOR_NEW_SCHEMA_CONTEXT));
        this.schemaContextHandler.onModelContextUpdated(newSchemaContext);

        assertNotEquals("SchemaContextHandler should not has reference to old SchemaContext",
                SCHEMA_CONTEXT, this.schemaContextHandler.get());
        assertEquals("SchemaContextHandler should has reference to new SchemaContext",
                newSchemaContext, this.schemaContextHandler.get());
    }
}
