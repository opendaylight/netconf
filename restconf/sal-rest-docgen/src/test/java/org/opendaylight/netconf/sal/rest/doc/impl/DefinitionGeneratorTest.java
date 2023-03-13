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
import org.junit.BeforeClass;
import org.junit.Test;
import org.opendaylight.netconf.sal.rest.doc.AbstractApiDocTest;
import org.opendaylight.yangtools.yang.common.Revision;

public final class DefinitionGeneratorTest extends AbstractApiDocTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    public void testConvertToJsonSchema() {
        final var module = CONTEXT.findModule("opflex", Revision.of("2014-05-28")).orElseThrow();
        final var generatorClass = new MapperGeneratorRecord(module, CONTEXT, new DefinitionNames(), true);
        final ObjectNode jsonObject = MAPPER.convertValue(generatorClass, ObjectNode.class);
        assertNotNull(jsonObject);
    }

    @Test
    public void testActionTypes() {
        final var module = CONTEXT.findModule("action-types").orElseThrow();
        final var generatorClass = new MapperGeneratorRecord(module, CONTEXT, new DefinitionNames(), true);
        final ObjectNode jsonObject = MAPPER.convertValue(generatorClass, ObjectNode.class);
        assertNotNull(jsonObject);
    }

    @Test
    public void testStringTypes() {
        final var module = CONTEXT.findModule("string-types").orElseThrow();
        final var generatorClass = new MapperGeneratorRecord(module, CONTEXT, new DefinitionNames(), true);
        final ObjectNode jsonObject = MAPPER.convertValue(generatorClass, ObjectNode.class);
        assertNotNull(jsonObject);
    }
}
