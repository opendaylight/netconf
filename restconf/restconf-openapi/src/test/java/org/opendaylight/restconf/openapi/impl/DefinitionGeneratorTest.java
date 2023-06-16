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
        final var generator = new DefinitionGenerator();
        final var schemas = generator.convertToSchemas(module, context, new DefinitionNames(), true);
        assertNotNull(schemas);
    }

    @Test
    public void testActionTypes() throws IOException {
        final var module = context.findModule("action-types").orElseThrow();
        final var generator = new DefinitionGenerator();
        final var schemas = generator.convertToSchemas(module, context, new DefinitionNames(), true);
        assertNotNull(schemas);
    }

    @Test
    public void testStringTypes() throws IOException {
        final var module = context.findModule("string-types").orElseThrow();
        final var generator = new DefinitionGenerator();
        final var schemas = generator.convertToSchemas(module, context, new DefinitionNames(), true);
        assertNotNull(schemas);
    }

    @Test
    public void testEnumType() throws IOException {
        final var module = context.findModule("definition-test").orElseThrow();
        final var generator = new DefinitionGenerator();
        final var jsonObject = generator.convertToSchemas(module, context, new DefinitionNames(), true);
        assertNotNull(jsonObject);

        var properties = jsonObject.get("definition-test_config_enum-container").properties();
        assertEquals("up", properties.get("status").get("default").asText());
    }

    @Test
    public void testUnionTypes() throws IOException {
        final var module = context.findModule("definition-test").orElseThrow();
        final var generator = new DefinitionGenerator();
        final var jsonObject = generator.convertToSchemas(module, context, new DefinitionNames(), true);
        assertNotNull(jsonObject);

        var properties = jsonObject.get("definition-test_config_union-container").properties();
        assertEquals("5", properties.get("testUnion1").get("default").asText());
        assertEquals("string", properties.get("testUnion1").get("type").asText());
        assertEquals("Some testUnion1", properties.get("testUnion1").get("example").asText());
        assertEquals("false", properties.get("testUnion2").get("default").asText());
        assertEquals("string", properties.get("testUnion2").get("type").asText());
        assertEquals("Some testUnion2", properties.get("testUnion2").get("example").asText());
    }

    @Test
    public void testBinaryType() throws IOException {
        final var module = context.findModule("definition-test").orElseThrow();
        final var generator = new DefinitionGenerator();
        final var jsonObject = generator.convertToSchemas(module, context, new DefinitionNames(), true);
        assertNotNull(jsonObject);

        var properties = jsonObject.get("definition-test_config_binary-container").properties();
        assertEquals("SGVsbG8gdGVzdCE=", properties.get("binary-data").get("default").asText());
    }

    @Test
    public void testBooleanType() throws IOException {
        final var module = context.findModule("definition-test").orElseThrow();
        final var generator = new DefinitionGenerator();
        final var jsonObject = generator.convertToSchemas(module, context, new DefinitionNames(), true);
        assertNotNull(jsonObject);

        var properties = jsonObject.get("definition-test_config_union-container").properties();
        assertEquals("true", properties.get("testBoolean").get("default").asText());
        assertEquals("true", properties.get("testBoolean").get("example").asText());
    }

    @Test
    public void testNumberType() throws IOException {
        final var module = context.findModule("definition-test").orElseThrow();
        final var generator = new DefinitionGenerator();
        final var jsonObject = generator.convertToSchemas(module, context, new DefinitionNames(), true);
        assertNotNull(jsonObject);

        var properties = jsonObject.get("definition-test_config_number-container").properties();
        assertEquals("42", properties.get("testInteger").get("default").asText());
        assertEquals("42", properties.get("testInt64").get("default").asText());
        assertEquals("42", properties.get("testUint64").get("default").asText());
        assertEquals("100", properties.get("testUnsignedInteger").get("default").asText());
        assertEquals("3.14", properties.get("testDecimal").get("default").asText());
        assertEquals("3.14159265359", properties.get("testDouble").get("default").asText());
    }

    @Test
    public void testInstanceIdentifierType() throws IOException {
        final var module = context.findModule("definition-test").orElseThrow();
        final var generator = new DefinitionGenerator();
        final var jsonObject = generator.convertToSchemas(module, context, new DefinitionNames(), true);
        assertNotNull(jsonObject);

        var properties = jsonObject.get("definition-test_config_network-container").properties();
        var networkRef = properties.get("network-ref");

        assertNotNull(networkRef);
        assertEquals("string", networkRef.get("type").asText());

        assertEquals("/network/nodes[node-id='node1']", networkRef.get("default").asText());
        assertEquals("/sample:definition-test", networkRef.get("example").asText());
    }

    @Test
    public void testStringFromRegex() throws IOException {
        final var module = context.findModule("strings-from-regex").orElseThrow();
        final var generator = new DefinitionGenerator();
        final var jsonObject = generator.convertToSchemas(module, context, new DefinitionNames(), true);
        assertNotNull(jsonObject);

        final var properties = jsonObject.get("strings-from-regex_config_test").properties();
        assertEquals("00:00:00:00:00:00", properties.get("mac-address").get("example").asText());
        assertEquals("0000-00-00T00:00:00Z", properties.get("login-date-time").get("example").asText());
        assertEquals("0.0.0.0", properties.get("ipv4-address").get("example").asText());

        assertEquals("ab:cd:ef:12:34:56", properties.get("mac-address").get("default").asText());
    }
}
