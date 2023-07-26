/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.legacy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

/**
 * Tests for handling {@link SchemaContext}.
 */
@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class SchemaContextHandlerTest {
    private static EffectiveModelContext SCHEMA_CONTEXT;

    private SchemaContextHandler schemaContextHandler;

    @Mock
    private DOMDataBroker mockDOMDataBroker;
    @Mock
    private DOMSchemaService mockDOMSchemaService;
    @Mock
    private ListenerRegistration<?> mockListenerReg;

    @BeforeClass
    public static void beforeClass() {
        SCHEMA_CONTEXT = YangParserTestUtils.parseYangResourceDirectory("/modules");
    }

    @AfterClass
    public static void afterClass() {
        SCHEMA_CONTEXT = null;
    }

    @Before
    public void setup() throws Exception {
        doReturn(mockListenerReg).when(mockDOMSchemaService).registerSchemaContextListener(any());

        schemaContextHandler = new SchemaContextHandler(mockDOMDataBroker, mockDOMSchemaService);
        verify(mockDOMSchemaService).registerSchemaContextListener(schemaContextHandler);

        schemaContextHandler.onModelContextUpdated(SCHEMA_CONTEXT);
    }

    /**
     * Testing init and close.
     */
    @Test
    public void testInitAndClose() {
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
                SCHEMA_CONTEXT, schemaContextHandler.get());
    }

    /**
     * Test updating of {@link SchemaContext}.
     *
     * <p>
     * Create new <code>SchemaContext</code>, set it to <code>SchemaContextHandler</code> and check if
     * <code>SchemaContextHandler</code> reference to new <code>SchemaContext</code> instead of old one.
     */
    @Test
    public void onGlobalContextUpdateTest() {
        // create new SchemaContext and update SchemaContextHandler
        final EffectiveModelContext newSchemaContext =
                YangParserTestUtils.parseYangResourceDirectory("/modules/modules-behind-mount-point");
        schemaContextHandler.onModelContextUpdated(newSchemaContext);

        assertNotEquals("SchemaContextHandler should not has reference to old SchemaContext",
                SCHEMA_CONTEXT, schemaContextHandler.get());
        assertEquals("SchemaContextHandler should has reference to new SchemaContext",
                newSchemaContext, schemaContextHandler.get());
    }
}
