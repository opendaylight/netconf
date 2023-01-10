/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import static org.junit.Assert.assertEquals;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class Netconf822Test {
    private static EffectiveModelContext CONTEXT;

    @BeforeClass
    public static void beforeClass() {
        CONTEXT = YangParserTestUtils.parseYangResourceDirectory("/nc822");
    }

    @Test
    public void testOperationsContentJSON() {
        assertEquals("{\n"
            + "  \"ietf-restconf:operations\" : {\n"
            + "    \"foo:new\": [null],\n"
            + "    \"foo:new1\": [null]\n"
            + "  }\n"
            + "}", OperationsContent.JSON.bodyFor(CONTEXT));
    }

    @Test
    public void testOperationsContentXML() {
        assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<operations xmlns=\"urn:ietf:params:xml:ns:yang:ietf-restconf\"\n"
            + "            xmlns:ns0=\"foo\" >\n"
            + "  <ns0:new/>\n"
            + "  <ns0:new1/>\n"
            + "</operations>", OperationsContent.XML.bodyFor(CONTEXT));
    }

    // TODO add test
    @Test
    public void testOperationsContentByIdentifierJSON() {
        final var stack = SchemaInferenceStack.of(CONTEXT);
        stack.enterSchemaTree(QName.create("foo", "new1", Revision.of("2021-09-30")));
        InstanceIdentifierContext instanceIdentifierContext = InstanceIdentifierContext.ofStack(stack);
        assertEquals("{\n"
                + "  \"ietf-restconf:operations\" : {\n"
                + "    \"foo:new1\": [null]\n"
                + "  }\n"
                + "}", OperationsContent.JSON.bodyFor(instanceIdentifierContext));
    }
}
