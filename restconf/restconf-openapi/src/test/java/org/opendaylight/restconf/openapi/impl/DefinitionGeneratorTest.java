/*
 * Copyright (c) 2014, 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.impl;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import org.junit.Test;
import org.opendaylight.restconf.openapi.AbstractOpenApiTest;
import org.opendaylight.yangtools.yang.common.Revision;

public final class DefinitionGeneratorTest extends AbstractOpenApiTest {
    @Test
    public void testConvertToSchemas() throws IOException {
        final var module = CONTEXT.findModule("opflex", Revision.of("2014-05-28")).orElseThrow();
        final DefinitionGenerator generator = new DefinitionGenerator();
        final var schemas = generator.convertToSchemas(module, CONTEXT, new DefinitionNames(), true);
        assertNotNull(schemas);
    }

    @Test
    public void testActionTypes() throws IOException {
        final var module = CONTEXT.findModule("action-types").orElseThrow();
        final DefinitionGenerator generator = new DefinitionGenerator();
        final var schemas = generator.convertToSchemas(module, CONTEXT, new DefinitionNames(), true);
        assertNotNull(schemas);
    }

    @Test
    public void testStringTypes() throws IOException {
        final var module = CONTEXT.findModule("string-types").orElseThrow();
        final DefinitionGenerator generator = new DefinitionGenerator();
        final var schemas = generator.convertToSchemas(module, CONTEXT, new DefinitionNames(), true);
        assertNotNull(schemas);
    }

    @Test
    public void testStringFromRegex() throws IOException {
        final var module = CONTEXT.findModule("strings-from-regex-test").orElseThrow();
        final var generator = new DefinitionGenerator();
        final var jsonObject = generator.convertToJsonSchema(module, CONTEXT, new DefinitionNames(), true);
        assertNotNull(jsonObject);

        var properties = jsonObject.get("strings-from-regex-test_test").get("properties");
        for (var property : properties) {
            assertFalse(property.get("default").asText().isEmpty());
        }
    }
}
