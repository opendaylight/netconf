/*
 * Copyright (c) 2014, 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        assertEquals("integer", properties.get("testUnion1").type());
        assertEquals(-2147483648, properties.get("testUnion1").example());
        assertEquals("false", properties.get("testUnion2").defaultValue());
        assertEquals("string", properties.get("testUnion2").type());
        assertEquals("Some testUnion2", properties.get("testUnion2").example());
        assertEquals("integer", properties.get("testUnion3").type());
        assertEquals(-2147483648, properties.get("testUnion3").example());
        assertEquals("false", properties.get("testUnion3").defaultValue());
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

    /**
     * Test that checks if namespace for rpc is present.
     */
    @Test
    public void testRpcNamespace() throws Exception {
        final var module = context.findModule("toaster", Revision.of("2009-11-20")).orElseThrow();
        final var jsonObject = DefinitionGenerator.convertToSchemas(module, context, new DefinitionNames(), true);
        assertNotNull(jsonObject);
        final var schema = jsonObject.get("toaster_make-toast_input");
        assertNotNull(schema);
        final var xml = schema.xml();
        assertNotNull(xml);
        final var namespace = xml.namespace();
        assertNotNull(namespace);
        assertEquals("http://netconfcentral.org/ns/toaster", namespace);
    }

    /**
     * Test that checks if namespace for actions is present.
     */
    @Test
    public void testActionsNamespace() throws IOException {
        final var module = context.findModule("action-types").orElseThrow();
        final var jsonObject = DefinitionGenerator.convertToSchemas(module, context, new DefinitionNames(), true);
        assertNotNull(jsonObject);
        final var schema = jsonObject.get("action-types_container-action_input");
        assertNotNull(schema);
        final var xml = schema.xml();
        assertNotNull(xml);
        final var namespace = xml.namespace();
        assertNotNull(namespace);
        assertEquals("urn:ietf:params:xml:ns:yang:test:action:types", namespace);
    }

    /**
     *  This test is designed to verify if yang Identity is used correctly as string value
     *  and no extra schemas are generated.
     */
    @Test
    public void testIdentity() throws IOException {
        final var module = context.findModule("toaster", Revision.of("2009-11-20")).orElseThrow();
        final var schemas = DefinitionGenerator.convertToSchemas(module, context, new DefinitionNames(), true);
        assertNotNull(schemas);

        // correct number of schemas generated
        assertEquals(3, schemas.size());
        final var makeToast = schemas.get("toaster_make-toast_input").properties().get("toasterToastType");

        assertEquals("wheat-bread", makeToast.defaultValue().toString());
        assertEquals("toast-type", makeToast.example().toString());
        assertEquals("string", makeToast.type());
        assertEquals("""
                This variable informs the toaster of the type of
                      material that is being toasted. The toaster
                      uses this information, combined with
                      toasterDoneness, to compute for how
                      long the material must be toasted to achieve
                      the required doneness.""", makeToast.description());
        assertTrue(makeToast.enums().containsAll(Set.of("toast-type","white-bread", "wheat-bread", "frozen-waffle",
            "hash-brown", "frozen-bagel", "wonder-bread")));
    }

    /**
     * Test that checks if list min-elements and max-elements are present.
     * Also checks if number of example elements meets the min-elements condition
     */
    @Test
    public void testListExamples() throws IOException {
        final var module = context.findModule("test-container-childs", Revision.of("2023-09-28")).orElseThrow();
        final var jsonObject = DefinitionGenerator.convertToSchemas(module, context, new DefinitionNames(), true);
        final var component = jsonObject.get("test-container-childs_root-container_nested-container");
        assertNotNull(component);
        assertNotNull(component.properties());
        final var property = component.properties().get("mandatory-list");
        assertNotNull(property);
        assertNotNull(property.minItems());
        assertNotNull(property.maxItems());
        assertEquals(3, (int) property.minItems());
        assertEquals(5, (int) property.maxItems());
        final var example = property.example();
        assertNotNull(example);
        assertEquals(ArrayList.class, example.getClass());
        assertEquals(3, ((List<?>)example).size());
    }

    /**
     * Test that checks if list min-elements and max-elements are present.
     * Also checks if number of example elements meets the min-elements condition
     * and if key defined leaf have unique values.
     */
    @Test
    public void testListExamplesWithNonKeyLeaf() throws IOException {
        final var module = context.findModule("test-container-childs", Revision.of("2023-09-28")).orElseThrow();
        final var jsonObject = DefinitionGenerator.convertToSchemas(module, context, new DefinitionNames(), true);
        final var component = jsonObject.get("test-container-childs_root-container_nested-container");
        assertNotNull(component);
        assertNotNull(component.properties());
        final var property = component.properties().get("mandatory-list");
        assertNotNull(property);
        assertNotNull(property.minItems());
        assertNotNull(property.maxItems());
        assertEquals(3, (int) property.minItems());
        assertEquals(5, (int) property.maxItems());
        final var example = property.example();
        assertNotNull(example);
        assertEquals(3, ((List<?>)example).size());
        assertTrue(checkUniqueExample(example, "id"));
        assertFalse(checkUniqueExample(example, "name"));
        assertFalse(checkUniqueExample(example, "address"));
    }

    /**
     * Test that checks if multiple key leafs have unique values.
     * Also checks if nested container node is ignored.
     */
    @Test
    public void testListExamplesWithTwoKeys() throws IOException {
        final var module = context.findModule("test-container-childs", Revision.of("2023-09-28")).orElseThrow();
        final var jsonObject = DefinitionGenerator.convertToSchemas(module, context, new DefinitionNames(), true);
        final var component = jsonObject.get("test-container-childs_root-container-two-keys_nested-container-two-keys");
        assertNotNull(component);
        assertNotNull(component.properties());
        final var property = component.properties().get("mandatory-list-two-keys");
        assertNotNull(property);
        final var example = property.example();
        assertNotNull(example);
        assertTrue(checkUniqueExample(example, "id"));
        assertTrue(checkUniqueExample(example, "name"));
        assertFalse(checkUniqueExample(example, "address"));
        assertEquals(3, ((ArrayList<Map<?,?>>)example).get(0).size());
    }

    /**
     * Test that checks if sets of unique defined leafs have unique combination of values.
     */
    @Test
    public void testListExamplesWithUnique() throws IOException {
        final var module = context.findModule("test-container-childs", Revision.of("2023-09-28")).orElseThrow();
        final var jsonObject = DefinitionGenerator.convertToSchemas(module, context, new DefinitionNames(), true);
        final var component = jsonObject.get("test-container-childs_root-container-unique_nested-container-unique");
        assertNotNull(component);
        assertNotNull(component.properties());
        final var property = component.properties().get("mandatory-list-unique");
        assertNotNull(property);
        final var example = property.example();
        assertNotNull(example);
        assertTrue(checkUniqueExample(example, "id"));
        assertTrue(checkUniqueExample(example, "name") || checkUniqueExample(example, "address"));
        assertFalse(checkUniqueExample(example, "description"));

    }

    public boolean checkUniqueExample(Object examples, String key) {
        assertEquals(ArrayList.class, examples.getClass());
        final var exampleValues = new ArrayList<>();

        for (Map<String, Object> example : (ArrayList<Map<String, Object>>)examples) {
            exampleValues.add(example.get(key));
        }
        return (exampleValues.size() == new HashSet<>(exampleValues).size());
    }
}
