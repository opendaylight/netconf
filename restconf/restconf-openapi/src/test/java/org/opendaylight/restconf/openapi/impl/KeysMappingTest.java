/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.opendaylight.restconf.openapi.OpenApiTestUtils.getPathParameters;

import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.openapi.model.OpenApiObject;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

public class KeysMappingTest {
    private static OpenApiObject doc;

    @BeforeClass
    public static void startUp() {
        final var context = YangParserTestUtils.parseYang("""
            module keys-mapping {
              namespace "mapping";
              prefix keys-mapping;
                list multiple-key-list {
                  key "name name2";
                  leaf name {
                    type string;
                  }
                  leaf name2 {
                    type string;
                  }
                  list multiple-key-list2 {
                    key "name name3";
                    leaf name {
                      type string;
                    }
                    leaf name3 {
                      type string;
                    }
                    list multiple-key-list3 {
                      key "name3 name";
                      leaf name3 {
                        type string;
                      }
                      leaf name {
                        type string;
                      }
                      list multiple-key-list4 {
                        key name;
                        leaf name {
                          type string;
                        }
                      }
                    }
                  }
                }
              }""");
        final var schemaService = mock(DOMSchemaService.class);
        when(schemaService.getGlobalContext()).thenReturn(context);
        final var generator = new OpenApiGeneratorRFC8040(schemaService);
        final var module = context.findModule("keys-mapping").orElseThrow();
        doc = generator.getOpenApiSpec(module, "http", "localhost:8181", "/", "", context);
        assertNotNull(doc);
    }

    /**
     * This test is designed to verify if the request parameters for nested lists with multiple keys are being
     * enumerated properly.
     *
     * <p>
     * This would mean that we will have name, name1, etc., when the same parameter appears multiple times in the path.
     */
    @Test
    public void testKeysMapping() {
        final var pathToMultipleKeyList4 = "/rests/data/keys-mapping:multiple-key-list={name},{name2}"
            + "/multiple-key-list2={name1},{name3}/multiple-key-list3={name31},{name4}/multiple-key-list4={name5}";
        assertTrue(doc.paths().containsKey(pathToMultipleKeyList4));
        assertEquals(List.of("name","name2", "name1", "name3", "name31", "name4", "name5"),
            getPathParameters(doc.paths(), pathToMultipleKeyList4));
    }
}
