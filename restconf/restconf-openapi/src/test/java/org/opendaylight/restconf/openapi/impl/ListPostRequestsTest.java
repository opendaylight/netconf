/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.impl;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.BeforeClass;
import org.junit.Test;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.openapi.model.OpenApiObject;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

public class ListPostRequestsTest {
    private static OpenApiObject doc;

    @BeforeClass
    public static void startUp() {
        final var context = YangParserTestUtils.parseYang("""
            module list-post {
              namespace "list-post";
              prefix lp;
                container container {
                  list list {
                    key "name address";
                    leaf name {
                      type string;
                    }
                    leaf address {
                      type string;
                    }
                  }
                }
              }""");
        final var schemaService = mock(DOMSchemaService.class);
        when(schemaService.getGlobalContext()).thenReturn(context);
        final var generator = new OpenApiGeneratorRFC8040(schemaService);
        final var module = context.findModule("list-post").orElseThrow();
        doc = generator.getOpenApiSpec(module, "http", "localhost:8181", "/", "", context);
        assertNotNull(doc);
    }

    @Test
    public void testListPostRequest() {
        final var pathToListWithTwoKeys = "/rests/data/list-post:container/list={name},{address}";
        assertNull(doc.paths().get(pathToListWithTwoKeys).post());
        assertNotNull(doc.paths().get(pathToListWithTwoKeys).put());
    }
}