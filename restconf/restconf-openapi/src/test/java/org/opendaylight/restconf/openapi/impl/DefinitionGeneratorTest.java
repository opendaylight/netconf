/*
 * Copyright (c) 2014, 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.impl;

import static org.junit.Assert.assertEquals;
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
    public void testEnumType() throws IOException {
        final var module = CONTEXT.findModule("definition-test").orElseThrow();
        final DefinitionGenerator generator = new DefinitionGenerator();
        final var jsonObject = generator.convertToSchemas(module, CONTEXT, new DefinitionNames(), true);
        assertNotNull(jsonObject);

        var properties = jsonObject.get("definition-test_enum-container").getProperties();
        assertEquals("up", properties.get("status").get("default").asText());
    }

    @Test
    public void testUnionTypes() throws IOException {
        final var module = CONTEXT.findModule("definition-test").orElseThrow();
        final DefinitionGenerator generator = new DefinitionGenerator();
        final var jsonObject = generator.convertToSchemas(module, CONTEXT, new DefinitionNames(), true);
        assertNotNull(jsonObject);

        var properties = jsonObject.get("definition-test_union-container").getProperties();
        assertEquals("5", properties.get("testUnion1").get("default").asText());
        assertEquals("false", properties.get("testUnion2").get("default").asText());
    }

    @Test
    public void testBinaryType() throws IOException {
        final var module = CONTEXT.findModule("definition-test").orElseThrow();
        final DefinitionGenerator generator = new DefinitionGenerator();
        final var jsonObject = generator.convertToSchemas(module, CONTEXT, new DefinitionNames(), true);
        assertNotNull(jsonObject);

        var properties = jsonObject.get("definition-test_binary-container").getProperties();
        assertEquals("SGVsbG8gdGVzdCE=", properties.get("binary-data").get("default").asText());
    }

    @Test
    public void testBooleanType() throws IOException {
        final var module = CONTEXT.findModule("definition-test").orElseThrow();
        final DefinitionGenerator generator = new DefinitionGenerator();
        final var jsonObject = generator.convertToSchemas(module, CONTEXT, new DefinitionNames(), true);
        assertNotNull(jsonObject);

        var properties = jsonObject.get("definition-test_union-container").getProperties();
        assertEquals("true", properties.get("testBoolean").get("default").asText());
    }

    @Test
    public void testNumberType() throws IOException {
        final var module = CONTEXT.findModule("definition-test").orElseThrow();
        final DefinitionGenerator generator = new DefinitionGenerator();
        final var jsonObject = generator.convertToSchemas(module, CONTEXT, new DefinitionNames(), true);
        assertNotNull(jsonObject);

        var properties = jsonObject.get("definition-test_number-container").getProperties();
        assertEquals("42", properties.get("testInteger").get("default").asText());
        assertEquals("100", properties.get("testUnsignedInteger").get("default").asText());
        assertEquals("3.14", properties.get("testDecimal").get("default").asText());
        assertEquals("3.14159265359", properties.get("testDouble").get("default").asText());
    }

    @Test
    public void testStringFromRegex() throws IOException {
        final var module = CONTEXT.findModule("strings-from-regex").orElseThrow();
        final var generator = new DefinitionGenerator();
        final var jsonObject = generator.convertToSchemas(module, CONTEXT, new DefinitionNames(), true);
        assertNotNull(jsonObject);

        var properties = jsonObject.get("strings-from-regex_test").getProperties();
        assertEquals("00:00:00:00:00:00", properties.get("mac-address").get("default").asText());
        assertEquals("0000-00-00T00:00:00Z", properties.get("login-date-time").get("default").asText());
        assertEquals("0.0.0.0", properties.get("ipv4-address").get("default").asText());
    }
}
