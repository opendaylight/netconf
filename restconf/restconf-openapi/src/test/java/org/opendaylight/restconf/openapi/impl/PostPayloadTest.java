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

/**
 * These tests are designed to verify that the specified path contains corresponding references in post requests
 * which contains lists and containers.
 *
 * <p>
 * The purpose of this test is to ensure that the specified path in the document contains the necessary references
 * in its post requests for both JSON and XML content types. It verifies that the expected structure is in place,
 * and the application can successfully retrieve the corresponding references for further processing.
 */
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
                    container cont2 {
                      leaf value7 {
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
                    }
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
              }
            }""";
    private static final String CONTENT_KEY = "content";
    private static final String SCHEMA_KEY = "schema";

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

    @Test
    public void testContainersPostPayloads() {
        final var path1 = "/rests/data/container-test:cont";
        assertTrue(containerDoc.paths().containsKey(path1));
        final var jsonRef1 = getJsonRef(containerDoc, path1);
        assertEquals("{\"cont1\":{\"$ref\":\"#/components/schemas/container-test_cont_config_cont1\"}}",
            jsonRef1);
        final var xmlRef1 = getXmlRef(containerDoc, path1);
        assertEquals("#/components/schemas/container-test_cont_config_cont1", xmlRef1);

        final var path2 = "/rests/data/container-test:cont/cont1";
        assertTrue(containerDoc.paths().containsKey(path2));
        final var jsonRef2 = getJsonRef(containerDoc, path2);
        assertEquals("{\"list4\":{\"type\":\"array\",\"items\":{\"$ref\":\""
                + "#/components/schemas/container-test_cont_cont1_config_list4\"}}}", jsonRef2);
        final var xmlRef2 = getXmlRef(containerDoc, path2);
        assertEquals("#/components/schemas/container-test_cont_cont1_config_list4", xmlRef2);

        final var path4 = "/rests/data/container-test:cont/cont1/list4={key4}/cont2";
        assertTrue(containerDoc.paths().containsKey(path4));
        final var jsonRef4 = getJsonRef(containerDoc, path4);
        assertEquals("{\"list5\":{\"type\":\"array\",\"items\":{\"$ref\":\""
                + "#/components/schemas/container-test_cont_cont1_list4_cont2_config_list5\"}}}", jsonRef4);
        final var xmlRef4 = getXmlRef(containerDoc, path4);
        assertEquals("#/components/schemas/container-test_cont_cont1_list4_cont2_config_list5", xmlRef4);
    }

    @Test
    public void testListsPostPayloads() {
        final var path1 = "/rests/data/list-test:cont";
        assertTrue(listDoc.paths().containsKey(path1));
        final var jsonRef1 = getJsonRef(listDoc, path1);
        assertEquals("{\"list1\":{\"type\":\"array\",\"items\":{\"$ref\":\""
            + "#/components/schemas/list-test_cont_config_list1\"}}}", jsonRef1);
        final var xmlRef1 = getXmlRef(listDoc, path1);
        assertEquals("#/components/schemas/list-test_cont_config_list1", xmlRef1);
    }

    private static String getJsonRef(final OpenApiObject openApiObject, final String path) {
        return openApiObject.paths().get(path).post().requestBody().get(CONTENT_KEY).get("application/json")
            .get(SCHEMA_KEY).get("properties").toString();
    }

    private static String getXmlRef(final OpenApiObject openApiObject, final String path) {
        return openApiObject.paths().get(path).post().requestBody().get(CONTENT_KEY).get(
            "application/xml").get(SCHEMA_KEY).get("$ref").asText();
    }
}
