/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.rest.impl.schema.context;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

import java.io.FileNotFoundException;
import java.lang.ref.SoftReference;
import java.net.URISyntaxException;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.controller.md.sal.rest.common.TestRestconfUtils;
import org.opendaylight.restconf.rest.api.schema.context.SchemaContextHandler;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.parser.spi.meta.ReactorException;


/**
 * Tests for handling {@link SchemaContext}
 *
 */
public class SchemaContextHandlerTest {

    private SchemaContextHandler schemaContextHandler;
    private SchemaContext schemaContext;
    private SoftReference<SchemaContext> schemaContextRef;

    @Before
    public void setup() throws ReactorException, FileNotFoundException, URISyntaxException {
        schemaContextHandler = new SchemaContextHandlerImpl();

        schemaContext = TestRestconfUtils.loadSchemaContext("/modules");
        schemaContextHandler.onGlobalContextUpdated(schemaContext);

        schemaContextRef = new SoftReference<SchemaContext>(schemaContext);
    }

    /**
     * Testing init of {@link SchemaContextHandlerImpl}
     *
     */
    @Test
    public void schemaContextHandlerImplInitTest() {
        assertNotNull("Handler should be created and not null", schemaContextHandler);
    }

    /**
     * Test getting actual {@link SchemaContext}.
     *
     */
    @Test
    public void getSchemaContextTest() {
        assertEquals("Actual SchemaContext should be in Handler",
                schemaContext, schemaContextHandler.getSchemaContext());
    }

    /**
     * Test updating of {@link SchemaContext} .
     *
     */
    @Test
    public void onGlobalContextUpdateTest() throws ReactorException, FileNotFoundException, URISyntaxException {
        // create new SchemaContext and update SchemaContextHandler
        schemaContext = TestRestconfUtils.loadSchemaContext("/modules/modules-behind-mount-point");
        schemaContextHandler.onGlobalContextUpdated(schemaContext);

        assertNotEquals("Old SchemaContext should removed from Handler",
                schemaContextRef.get(), schemaContextHandler.getSchemaContext());
        assertEquals("Handler should reference new SchemaContext",
                schemaContext,schemaContextHandler.getSchemaContext());
    }

}
