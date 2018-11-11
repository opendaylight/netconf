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

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.restconf.nb.rfc8040.TestRestconfUtils;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

/**
 * Tests for handling {@link SchemaContext}.
 */
public class SchemaContextHandlerTest {

    private static final String PATH_FOR_ACTUAL_SCHEMA_CONTEXT = "/modules";
    private static final String PATH_FOR_NEW_SCHEMA_CONTEXT = "/modules/modules-behind-mount-point";

    private SchemaContextHandler schemaContextHandler;
    private SchemaContext schemaContext;
    private final DOMSchemaService mockDOMSchemaService = Mockito.mock(DOMSchemaService.class);

    @Before
    public void setup() throws Exception {
        final TransactionChainHandler txHandler = Mockito.mock(TransactionChainHandler.class);
        final DOMTransactionChain domTx = Mockito.mock(DOMTransactionChain.class);
        Mockito.when(txHandler.get()).thenReturn(domTx);
        final DOMDataTreeWriteTransaction wTx = Mockito.mock(DOMDataTreeWriteTransaction.class);
        Mockito.when(domTx.newWriteOnlyTransaction()).thenReturn(wTx);
        Mockito.doReturn(CommitInfo.emptyFluentFuture()).when(wTx).commit();

        this.schemaContextHandler = SchemaContextHandler.newInstance(txHandler, mockDOMSchemaService);

        this.schemaContext =
                YangParserTestUtils.parseYangFiles(TestRestconfUtils.loadFiles(PATH_FOR_ACTUAL_SCHEMA_CONTEXT));
        this.schemaContextHandler.onGlobalContextUpdated(this.schemaContext);
    }

    /**
     * Testing init and close.
     */
    @Test
    public void testInitAndClose() {
        ListenerRegistration<?> mockListenerReg = Mockito.mock(ListenerRegistration.class);
        Mockito.doReturn(mockListenerReg).when(mockDOMSchemaService)
            .registerSchemaContextListener(schemaContextHandler);

        schemaContextHandler.init();

        Mockito.verify(mockDOMSchemaService).registerSchemaContextListener(schemaContextHandler);

        schemaContextHandler.close();

        Mockito.verify(mockListenerReg).close();
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
                this.schemaContext, this.schemaContextHandler.get());
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
        final SchemaContext newSchemaContext =
                YangParserTestUtils.parseYangFiles(TestRestconfUtils.loadFiles(PATH_FOR_NEW_SCHEMA_CONTEXT));
        this.schemaContextHandler.onGlobalContextUpdated(newSchemaContext);

        assertNotEquals("SchemaContextHandler should not has reference to old SchemaContext",
                this.schemaContext, this.schemaContextHandler.get());
        assertEquals("SchemaContextHandler should has reference to new SchemaContext",
                newSchemaContext, this.schemaContextHandler.get());
    }
}
