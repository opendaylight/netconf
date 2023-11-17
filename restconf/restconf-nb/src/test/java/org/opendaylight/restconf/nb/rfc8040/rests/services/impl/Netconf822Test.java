/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.eclipse.jdt.annotation.NonNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

@ExtendWith(MockitoExtension.class)
class Netconf822Test extends AbstractRestconfTest {
    private static final @NonNull EffectiveModelContext MODEL_CONTEXT =
        YangParserTestUtils.parseYangResourceDirectory("/nc822");

    @Override
    @NonNull EffectiveModelContext modelContext() {
        return MODEL_CONTEXT;
    }

    @Test
    void testOperationsContent() {
        final var content = server.operationsGET();
        assertEquals("""
            {
              "ietf-restconf:operations" : {
                "foo:new" : [null],
                "foo:new1" : [null]
              }
            }""", content.toJSON());
        assertEquals("""
            <operations xmlns="urn:ietf:params:xml:ns:yang:ietf-restconf">
              <new xmlns="foo"/>
              <new1 xmlns="foo"/>
            </operations>""", content.toXML());
    }

    @Test
    void testOperationsContentByIdentifier() {
        final var content = server.operationsGET("foo:new1");
        assertNotNull(content);
        assertEquals("""
            { "foo:new1" : [null] }""", content.toJSON());
        assertEquals("""
            <new1 xmlns="foo"/>""", content.toXML());
    }
}
