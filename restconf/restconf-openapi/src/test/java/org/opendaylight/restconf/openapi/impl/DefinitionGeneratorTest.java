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
    public void testStringFromRegex() throws IOException {
        final var module = context.findModule("strings-from-regex").orElseThrow();
        final var jsonObject = DefinitionGenerator.convertToSchemas(module, context, new DefinitionNames(), true);
        assertNotNull(jsonObject);

        final var properties = jsonObject.get("strings-from-regex_test").properties();
        assertEquals("00:00:00:00:00:00", properties.get("mac-address").get("example").asText());
        assertEquals("0000-00-00T00:00:00Z", properties.get("login-date-time").get("example").asText());
        assertEquals("0.0.0.0", properties.get("ipv4-address").get("example").asText());
    }

    @Test
    public void testIdentity() throws IOException {
        final var module = context.findModule("toaster", Revision.of("2009-11-20")).orElseThrow();
        final var schemas = DefinitionGenerator.convertToSchemas(module, context, new DefinitionNames(), true);
        assertNotNull(schemas);

        // correct number of schemas generated
        assertEquals(3, schemas.size());
        final var makeToast = schemas.get("toaster_make-toast_input").properties().get("toasterToastType");

        assertEquals("wheat-bread", makeToast.get("default").asText());
        assertEquals("toast-type", makeToast.get("example").asText());
        assertEquals("string", makeToast.get("type").asText());
        assertEquals("""
                This variable informs the toaster of the type of
                      material that is being toasted. The toaster
                      uses this information, combined with
                      toasterDoneness, to compute for how
                      long the material must be toasted to achieve
                      the required doneness.""", makeToast.get("description").asText());
    }
}
