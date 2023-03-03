/*
 * Copyright (c) 2014, 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.rest.doc.impl;

import static org.junit.Assert.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import org.junit.Test;
import org.opendaylight.netconf.sal.rest.doc.impl.ApiDocServiceImpl.OAversion;
import org.opendaylight.netconf.sal.rest.doc.impl.BaseYangSwaggerGenerator.MapperGeneratorRecord;
import org.opendaylight.yangtools.yang.common.Revision;

public final class SwaggerObjectTest extends AbstractApiDocTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    static {
        MAPPER.configure(SerializationFeature.INDENT_OUTPUT, true);
        SimpleModule module = new SimpleModule();
        module.addSerializer(MapperGeneratorRecord.class, new DefinitionGenerator());
        MAPPER.registerModule(module);
    }

    @Test
    public void testConvertToJsonSchema() throws IOException {
        final var module = CONTEXT.findModule("opflex", Revision.of("2014-05-28")).orElseThrow();
        MapperGeneratorRecord record = new MapperGeneratorRecord(module, CONTEXT, new DefinitionNames(),
                OAversion.V2_0, true);
        ObjectNode jsonNodes = MAPPER.convertValue(record, ObjectNode.class);
        assertNotNull(jsonNodes);
    }

    @Test
    public void testActionTypes() throws IOException {
        final var module = CONTEXT.findModule("action-types").orElseThrow();
        MapperGeneratorRecord record = new MapperGeneratorRecord(module, CONTEXT, new DefinitionNames(),
                ApiDocServiceImpl.OAversion.V2_0, true);
        ObjectNode jsonNodes = MAPPER.convertValue(record, ObjectNode.class);
        assertNotNull(jsonNodes);
    }

    @Test
    public void testStringTypes() throws IOException {
        final var module = CONTEXT.findModule("string-types").orElseThrow();
        MapperGeneratorRecord record = new MapperGeneratorRecord(module, CONTEXT, new DefinitionNames(),
                ApiDocServiceImpl.OAversion.V2_0, true);
        ObjectNode jsonNodes = MAPPER.convertValue(record, ObjectNode.class);
        assertNotNull(jsonNodes);
    }
}
