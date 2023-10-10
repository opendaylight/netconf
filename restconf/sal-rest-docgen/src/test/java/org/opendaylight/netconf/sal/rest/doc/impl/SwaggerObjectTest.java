/*
 * Copyright (c) 2014, 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.rest.doc.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import org.junit.Test;
import org.opendaylight.yangtools.yang.common.Revision;

public final class SwaggerObjectTest extends AbstractApiDocTest {
    @Test
    public void testConvertToJsonSchema() throws IOException {
        final var module = CONTEXT.findModule("opflex", Revision.of("2014-05-28")).orElseThrow();
        final DefinitionGenerator generator = new DefinitionGenerator();
        final ObjectNode jsonObject = generator.convertToJsonSchema(module, CONTEXT, new DefinitionNames(),
            ApiDocServiceImpl.OAversion.V2_0, true);
        assertNotNull(jsonObject);
    }

    @Test
    public void testActionTypes() throws IOException {
        final var module = CONTEXT.findModule("action-types").orElseThrow();
        final DefinitionGenerator generator = new DefinitionGenerator();
        final ObjectNode jsonObject = generator.convertToJsonSchema(module, CONTEXT, new DefinitionNames(),
            ApiDocServiceImpl.OAversion.V2_0, true);
        assertNotNull(jsonObject);
    }

    @Test
    public void testStringTypes() throws IOException {
        final var module = CONTEXT.findModule("string-types").orElseThrow();
        final DefinitionGenerator generator = new DefinitionGenerator();
        final ObjectNode jsonObject = generator.convertToJsonSchema(module, CONTEXT, new DefinitionNames(),
            ApiDocServiceImpl.OAversion.V2_0, true);
        assertNotNull(jsonObject);
    }

    /**
     * Test that checks if namespace for rpc is present.
     */
    @Test
    public void testRpcNamespace() throws Exception {
        final var module = CONTEXT.findModule("toaster", Revision.of("2009-11-20")).orElseThrow();
        final var generator = new DefinitionGenerator();
        final var jsonObject = generator.convertToJsonSchema(module, CONTEXT, new DefinitionNames(),
            ApiDocServiceImpl.OAversion.V2_0, true);
        assertNotNull(jsonObject);
        final var schema = jsonObject.get("toaster_make-toast_input");
        assertNotNull(schema);
        final var xml = schema.get("xml");
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
        final var module = CONTEXT.findModule("action-types").orElseThrow();
        final var generator = new DefinitionGenerator();
        final var jsonObject = generator.convertToJsonSchema(module, CONTEXT, new DefinitionNames(),
            ApiDocServiceImpl.OAversion.V2_0, true);
        assertNotNull(jsonObject);
        final var schema = jsonObject.get("action-types_container-action_input");
        assertNotNull(schema);
        final var xml = schema.get("xml");
        assertNotNull(xml);
        final var namespace = xml.get("namespace");
        assertNotNull(namespace);
        assertEquals("urn:ietf:params:xml:ns:yang:test:action:types", namespace.asText());
    }
}
