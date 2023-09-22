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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.math.BigDecimal;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

public final class DefinitionGeneratorTest {
    private static EffectiveModelContext context;
    private static DOMSchemaService schemaService;

    @BeforeClass
    public static void beforeClass() {
        schemaService = mock(DOMSchemaService.class);
        context = YangParserTestUtils.parseYangResourceDirectory("/yang");
        when(schemaService.getGlobalContext()).thenReturn(context);
    }

    @Test
    public void testConvertToSchemas() throws IOException {
        final var module = context.findModule("opflex", Revision.of("2014-05-28")).orElseThrow();
        final var schemas = DefinitionGenerator.convertToSchemas(module, context, new DefinitionNames(), true);
        assertNotNull(schemas);
    }

    @Test
    public void testActionTypes() throws IOException {
        final var module = context.findModule("action-types").orElseThrow();
        final var schemas = DefinitionGenerator.convertToSchemas(module, context, new DefinitionNames(), true);
        assertNotNull(schemas);
    }

    @Test
    public void testStringTypes() throws IOException {
        final var module = context.findModule("string-types").orElseThrow();
        final var schemas = DefinitionGenerator.convertToSchemas(module, context, new DefinitionNames(), true);
        assertNotNull(schemas);
    }

    @Test
    public void testEnumType() throws IOException {
        final var module = context.findModule("definition-test").orElseThrow();
        final var schemas = DefinitionGenerator.convertToSchemas(module, context, new DefinitionNames(), true);
        assertNotNull(schemas);

        final var properties = schemas.get("definition-test_enum-container").properties();
        assertEquals("up", properties.get("status").defaultValue());
    }

    @Test
    public void testUnionTypes() throws IOException {
        final var module = context.findModule("definition-test").orElseThrow();
        final var schemas = DefinitionGenerator.convertToSchemas(module, context, new DefinitionNames(), true);
        assertNotNull(schemas);

        final var properties = schemas.get("definition-test_union-container").properties();
        assertEquals("5", properties.get("testUnion1").defaultValue());
        assertEquals("false", properties.get("testUnion2").defaultValue());
    }

    @Test
    public void testBinaryType() throws IOException {
        final var module = context.findModule("definition-test").orElseThrow();
        final var schemas = DefinitionGenerator.convertToSchemas(module, context, new DefinitionNames(), true);
        assertNotNull(schemas);

        final var properties = schemas.get("definition-test_binary-container").properties();
        assertEquals("SGVsbG8gdGVzdCE=", properties.get("binary-data").defaultValue());
    }

    @Test
    public void testBooleanType() throws IOException {
        final var module = context.findModule("definition-test").orElseThrow();
        final var schemas = DefinitionGenerator.convertToSchemas(module, context, new DefinitionNames(), true);
        assertNotNull(schemas);

        final var properties = schemas.get("definition-test_union-container").properties();
        assertEquals(true, properties.get("testBoolean").defaultValue());
        assertEquals(true, properties.get("testBoolean").example());
    }

    @Test
    public void testNumberType() throws IOException {
        final var module = context.findModule("definition-test").orElseThrow();
        final var schemas = DefinitionGenerator.convertToSchemas(module, context, new DefinitionNames(), true);
        assertNotNull(schemas);

        final var properties = schemas.get("definition-test_number-container").properties();
        assertEquals(42L, properties.get("testInteger").defaultValue());
        assertEquals(42L, properties.get("testInt64").defaultValue());
        assertEquals(BigDecimal.valueOf(42), properties.get("testUint64").defaultValue());
        assertEquals(100L, properties.get("testUnsignedInteger").defaultValue());
        assertEquals(BigDecimal.valueOf(3.14), properties.get("testDecimal").defaultValue());
        assertEquals(BigDecimal.valueOf(3.14159265359), properties.get("testDouble").defaultValue());
    }

    @Test
    public void testInstanceIdentifierType() throws IOException {
        final var module = context.findModule("definition-test").orElseThrow();
        final var schemas = DefinitionGenerator.convertToSchemas(module, context, new DefinitionNames(), true);
        assertNotNull(schemas);

        final var properties = schemas.get("definition-test_network-container").properties();
        final var networkRef = properties.get("network-ref");

        assertNotNull(networkRef);
        assertEquals("string", networkRef.type());

        assertEquals("/network/nodes[node-id='node1']", networkRef.defaultValue());
        assertEquals("/sample:binary-container", networkRef.example());
    }

    @Test
    public void testStringFromRegex() throws IOException {
        final var module = context.findModule("strings-from-regex").orElseThrow();
        final var jsonObject = DefinitionGenerator.convertToSchemas(module, context, new DefinitionNames(), true);
        assertNotNull(jsonObject);

        final var properties = jsonObject.get("strings-from-regex_test").properties();
        assertEquals("00:00:00:00:00:00", properties.get("mac-address").example().toString());
        assertEquals("0000-00-00T00:00:00Z", properties.get("login-date-time").example().toString());
        assertEquals("0.0.0.0", properties.get("ipv4-address").example().toString());
    }
}
