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
        final DefinitionGenerator generator = new DefinitionGenerator();
        final var schemas = generator.convertToSchemas(module, context, new DefinitionNames(), true);
        assertNotNull(schemas);
    }

    @Test
    public void testActionTypes() throws IOException {
        final var module = context.findModule("action-types").orElseThrow();
        final DefinitionGenerator generator = new DefinitionGenerator();
        final var schemas = generator.convertToSchemas(module, context, new DefinitionNames(), true);
        assertNotNull(schemas);
    }

    @Test
    public void testStringTypes() throws IOException {
        final var module = context.findModule("string-types").orElseThrow();
        final DefinitionGenerator generator = new DefinitionGenerator();
        final var schemas = generator.convertToSchemas(module, context, new DefinitionNames(), true);
        assertNotNull(schemas);
    }

    @Test
    public void testStringFromRegex() throws IOException {
        final var module = context.findModule("strings-from-regex").orElseThrow();
        final var generator = new DefinitionGenerator();
        final var jsonObject = generator.convertToSchemas(module, context, new DefinitionNames(), true);
        assertNotNull(jsonObject);

        var properties = jsonObject.get("strings-from-regex_test").properties();
        assertEquals("00:00:00:00:00:00", properties.get("mac-address").get("example").asText());
        assertEquals("0000-00-00T00:00:00Z", properties.get("login-date-time").get("example").asText());
        assertEquals("0.0.0.0", properties.get("ipv4-address").get("example").asText());
    }

    /**
     * Test that checks if namespace for rpc is present.
     */
    @Test
    public void testRpcNamespace() throws Exception {
        final var module = context.findModule("toaster", Revision.of("2009-11-20")).orElseThrow();
        final var generator = new DefinitionGenerator();
        final var jsonObject = generator.convertToSchemas(module, context, new DefinitionNames(), true);
        assertNotNull(jsonObject);
        final var schema = jsonObject.get("toaster_make-toast_input");
        assertNotNull(schema);
        final var xml = schema.xml();
        assertNotNull(xml);
        final var namespace = xml.get("namespace");
        assertNotNull(namespace);
        assertEquals("http://netconfcentral.org/ns/toaster", namespace.asText());
    }

    /**
     * Test that checks if namespace for actions is present.
     */
    @Test
    public void testActionsNamespace() throws IOException {
        final var module = context.findModule("action-types").orElseThrow();
        final var generator = new DefinitionGenerator();
        final var jsonObject = generator.convertToSchemas(module, context, new DefinitionNames(), true);
        assertNotNull(jsonObject);
        final var schema = jsonObject.get("action-types_container-action_input");
        assertNotNull(schema);
        final var xml = schema.xml();
        assertNotNull(xml);
        final var namespace = xml.get("namespace");
        assertNotNull(namespace);
<<<<<<< HEAD   (74e35c Remove synchronization locking)
        assertEquals("urn:ietf:params:xml:ns:yang:test:action:types", namespace.asText());
=======
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
        assertEquals(2, schemas.size());
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
>>>>>>> CHANGE (1f6754 Fix module's root POST request payload)
    }
}
