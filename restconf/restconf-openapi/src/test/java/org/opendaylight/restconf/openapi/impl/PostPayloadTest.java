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

import org.junit.BeforeClass;
import org.junit.Test;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.openapi.model.OpenApiObject;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

public class PostPayloadTest {
    private static OpenApiObject doc;

    @BeforeClass
    public static void startUp() {
        final var context = YangParserTestUtils.parseYang("""
            module test {
              namespace "urn:opendaylight:params:xml:ns:yang:netconf:monitoring:test";
              prefix "test";
               
              revision 2023-07-31 {
                description "Test model.";
              }
               
              container cont {
                container cont1 {
                  list list4 {
                    key "key4";
               
                  leaf key4 {
                    type string;
                  }
               
                   leaf value4 {
                     type string;
                   }
               
                   list list5 {
                     key "key5";
               
                     leaf key5 {
                       type string;
                     }
               
                     leaf value5 {
                       type string;
                     }
                   }
               
                   container cont2 {
                     list list6 {
                       key "key6";
               
                     leaf key6 {
                       type string;
                     }
               
                     leaf value6 {
                       type string;
                     }
                   }
                }
              }
            }
               
                   list list1 {
                     key "key1";
               
                     leaf key1 {
                       type string;
                     }
               
                     leaf value1 {
                       type string;
                     }
                   }
               
                   list list2 {
                     key "key2";
               
                     leaf key2 {
                       type string;
                     }
               
                     leaf value2 {
                       type string;
                     }
                   }
               
                   list list3 {
                     key "key3";
               
                     leaf key3 {
                       type string;
                     }
               
                     leaf value3 {
                       type string;
                     }
                   }
                 }
               }""");
        final var schemaService = mock(DOMSchemaService.class);
        when(schemaService.getGlobalContext()).thenReturn(context);
        final var generator = new OpenApiGeneratorRFC8040(schemaService);
        final var module = context.findModule("test", Revision.of("2023-07-31")).orElseThrow();
        doc = generator.getOpenApiSpec(module, "http", "localhost:8181", "/", "", context);
        assertNotNull(doc);
    }

    /**
     * This test is designed to verifies that the specified path contains corresponding references in post requests.
     *
     * <p>
     * The purpose of this test is to ensure that the specified path in the document contains the necessary references in
     * its post requests for both JSON and XML content types. It verifies that the expected structure is in place, and the
     * application can successfully retrieve the corresponding references for further processing.
     */
    @Test
    public void testKeysMapping() {
        final var pathToMultipleKeyList4 = "/rests/data/test:cont";
        assertTrue(doc.paths().containsKey(pathToMultipleKeyList4));
        final var jsonRef = doc.paths().get(pathToMultipleKeyList4).post().requestBody().get("content").get(
            "application/json").get("schema").get("$ref").asText();
        assertEquals("#/components/schemas/test_cont_config_cont1", jsonRef);
        final var xmlRef = doc.paths().get(pathToMultipleKeyList4).post().requestBody().get("content").get(
            "application/xml").get("schema").get("$ref").asText();
        assertEquals("#/components/schemas/test_cont_config_cont1", xmlRef);
    }
}
