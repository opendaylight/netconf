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
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

public class Netconf822Test {
    private static final Absolute NEW1 = Absolute.of(QName.create("foo", "2021-09-30", "new1"));

    private static EffectiveModelContext SCHEMA;

    @BeforeClass
    public static void beforeClass() {
        SCHEMA = YangParserTestUtils.parseYangResourceDirectory("/nc822");
    }

    @Test
    public void testOperationsContentJSON() {
        assertEquals("""
            {
              "ietf-restconf:operations" : {
                "foo:new": [null],
                "foo:new1": [null]
              }
            }""",
            MdsalRestconfServer.operationsGET(OperationsContent.JSON, SchemaInferenceStack.of(SCHEMA).toInference()));
    }

    @Test
    public void testOperationsContentByIdentifierJSON() {
        assertEquals("""
            {
              "ietf-restconf:operations" : {
                "foo:new1": [null]
              }
            }""",
            MdsalRestconfServer.operationsGET(OperationsContent.JSON,
                SchemaInferenceStack.of(SCHEMA, NEW1).toInference()));
    }

    @Test
    public void testOperationsContentXML() {
        assertEquals("""
            <?xml version="1.0" encoding="UTF-8"?>
            <operations xmlns="urn:ietf:params:xml:ns:yang:ietf-restconf"
                        xmlns:ns0="foo" >
              <ns0:new/>
              <ns0:new1/>
            </operations>""",
            MdsalRestconfServer.operationsGET(OperationsContent.XML, SchemaInferenceStack.of(SCHEMA).toInference()));
    }

    @Test
    public void testOperationsContentByIdentifierXML() {
        assertEquals("""
            <?xml version="1.0" encoding="UTF-8"?>
            <operations xmlns="urn:ietf:params:xml:ns:yang:ietf-restconf"
                        xmlns:ns0="foo" >
              <ns0:new1/>
            </operations>""",
            MdsalRestconfServer.operationsGET(OperationsContent.XML,
                SchemaInferenceStack.of(SCHEMA, NEW1).toInference()));
    }
}
