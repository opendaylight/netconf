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
import org.opendaylight.restconf.openapi.DocGenTestHelper;
import org.opendaylight.restconf.openapi.model.OpenApiObject;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

public class PostPayloadTest {
    private static final String CONTAINER_TEST = """
            module container-test {
              namespace "urn:opendaylight:params:xml:ns:yang:netconf:monitoring:cont-test";
              prefix "ctest";
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
              }
            }""";

    private static final String LIST_TEST = """
            module list-test {
              namespace "urn:opendaylight:params:xml:ns:yang:netconf:monitoring:list-test";
              prefix "ltest";
              revision 2023-07-31 {
                description "Test model.";
              }
              container cont {
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
            }""";


    private static OpenApiObject containerDoc;
    private static OpenApiObject listDoc;


    @BeforeClass
    public static void startUp() throws Exception {
        final var context = YangParserTestUtils.parseYang(CONTAINER_TEST, LIST_TEST);
        final var schemaService = mock(DOMSchemaService.class);
        when(schemaService.getGlobalContext()).thenReturn(context);
        final var generator = new OpenApiGeneratorRFC8040(schemaService);
        final var uriInfo = DocGenTestHelper.createMockUriInfo("http://localhost/path");
        containerDoc = generator.getApiDeclaration("container-test", "2023-07-31", uriInfo);
        assertNotNull(containerDoc);
        listDoc = generator.getApiDeclaration("list-test", "2023-07-31", uriInfo);
        assertNotNull(listDoc);
    }

    /**
     * This test is designed to verifies that the specified path contains corresponding references in post requests.
     *
     * <p>
     * The purpose of this test is to ensure that the specified path in the document contains the necessary references
     * in its post requests for both JSON and XML content types. It verifies that the expected structure is in place,
     * and the application can successfully retrieve the corresponding references for further processing.
     */
    @Test
    public void testTopLevelPostPayloads() {
        final var pathToContainerChild = "/rests/data/container-test:cont";
        assertTrue(containerDoc.paths().containsKey(pathToContainerChild));
        final var jsonRef1 = containerDoc.paths().get(pathToContainerChild).post().requestBody().get("content").get(
            "application/json").get("schema").toString();
        assertEquals("{\"properties\":{\"cont1\":{\"$ref\":\"#/components/schemas/container-test_cont_cont1\"}}}",
            jsonRef1);
        final var xmlRef1 = containerDoc.paths().get(pathToContainerChild).post().requestBody().get("content").get(
            "application/xml").get("schema").get("$ref").asText();
        assertEquals("#/components/schemas/container-test_cont_cont1", xmlRef1);

        final var pathToListChild = "/rests/data/list-test:cont";
        assertTrue(listDoc.paths().containsKey(pathToListChild));
        final var jsonRef2 = listDoc.paths().get(pathToListChild).post().requestBody().get("content").get(
            "application/json").get("schema").toString();
        assertEquals("{\"properties\":{\"list1\":{\"type\":\"array\",\"items\":{\"$ref\":\""
                + "#/components/schemas/list-test_cont_list1\"}}}}", jsonRef2);
        final var xmlRef2 = listDoc.paths().get(pathToListChild).post().requestBody().get("content").get(
            "application/xml").get("schema").get("$ref").asText();
        assertEquals("#/components/schemas/list-test_cont_list1", xmlRef2);

    }
}
