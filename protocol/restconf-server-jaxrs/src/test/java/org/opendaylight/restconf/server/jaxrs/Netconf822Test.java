/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.jaxrs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

@ExtendWith(MockitoExtension.class)
class Netconf822Test extends AbstractRestconfTest {
    private static final EffectiveModelContext MODEL_CONTEXT =
        YangParserTestUtils.parseYangResourceDirectory("/nc822");

    @Override
    EffectiveModelContext modelContext() {
        return MODEL_CONTEXT;
    }

    @Test
    void testOperationsContent() {
        final var body = assertFormattableBody(200, ar -> restconf.operationsJsonGET(sc, ar));

        assertFormat("""
            {
              "ietf-restconf:operations" : {
                "foo:new" : [null],
                "foo:new1" : [null]
              }
            }""", body::formatToJSON, true);
        assertFormat("""
            <operations xmlns="urn:ietf:params:xml:ns:yang:ietf-restconf">
              <new xmlns="foo"/>
              <new1 xmlns="foo"/>
            </operations>""", body::formatToXML, true);
    }

    @Test
    void testOperationsContentByIdentifier() {
        final var body = assertFormattableBody(200, ar -> restconf.operationsXmlGET(apiPath("foo:new1"), sc, ar));

        assertFormat("""
            { "foo:new1" : [null] }""", body::formatToJSON, false);
        assertFormat("""
            <new1 xmlns="foo"/>""", body::formatToXML, false);
    }
}
