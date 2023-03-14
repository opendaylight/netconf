/*
 * Copyright (c) 2014, 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.rest.doc.impl;

import static org.junit.Assert.assertNotNull;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import java.io.IOException;
import java.io.StringWriter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.yangtools.yang.common.Revision;

public final class SwaggerObjectTest extends AbstractApiDocTest {

    private JsonGenerator definitions;
    private StringWriter jsonObjectWriter;

    @Before
    public void startUp() throws IOException {
        final JsonFactory factory = new JsonFactory();
        jsonObjectWriter = new StringWriter();
        definitions = factory.createGenerator(jsonObjectWriter);
        definitions.writeStartObject();
    }

    @After
    public void tearDown() throws IOException {
        jsonObjectWriter.close();
    }

    @Test
    public void testConvertToJsonSchema() throws IOException {
        final var module = CONTEXT.findModule("opflex", Revision.of("2014-05-28")).orElseThrow();
        final DefinitionGenerator generator = new DefinitionGenerator();

        generator.convertToJsonSchema(module, CONTEXT, definitions, new DefinitionNames(),
            ApiDocServiceImpl.OAversion.V2_0, true);
        definitions.writeEndObject();
        definitions.close();
        final var result = jsonObjectWriter.toString();
        assertNotNull(result);
    }

    @Test
    public void testActionTypes() throws IOException {
        final var module = CONTEXT.findModule("action-types").orElseThrow();
        final DefinitionGenerator generator = new DefinitionGenerator();
        generator.convertToJsonSchema(module, CONTEXT, definitions, new DefinitionNames(),
                ApiDocServiceImpl.OAversion.V2_0, true);
        definitions.close();
        final var result = jsonObjectWriter.toString();
        assertNotNull(result);
    }

    @Test
    public void testStringTypes() throws IOException {
        final var module = CONTEXT.findModule("string-types").orElseThrow();
        final DefinitionGenerator generator = new DefinitionGenerator();
        generator.convertToJsonSchema(module, CONTEXT, definitions, new DefinitionNames(),
                ApiDocServiceImpl.OAversion.V2_0, true);
        definitions.close();
        final var result = jsonObjectWriter.toString();
        assertNotNull(result);
    }
}
