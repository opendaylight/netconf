/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.rest.doc.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.BeforeClass;
import org.junit.Test;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.netconf.sal.rest.doc.impl.ApiDocServiceImpl.OAversion;
import org.opendaylight.netconf.sal.rest.doc.swagger.OpenApiObject;
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
        final var generator = new ApiDocGeneratorRFC8040(schemaService);
        final var uriInfo = DocGenTestHelper.createMockUriInfo("http://localhost/path");
        containerDoc = (OpenApiObject) generator.getApiDeclaration("container-test", "2023-07-31", uriInfo,
            OAversion.V3_0);
        assertNotNull(containerDoc);
        listDoc = (OpenApiObject) generator.getApiDeclaration("list-test", "2023-07-31", uriInfo, OAversion.V3_0);
        assertNotNull(listDoc);
    }

    @Test
    public void testContainersPostPayloads() {
        final var path1 = "/rests/data/container-test:cont";
        assertNotNull(containerDoc.getPaths().get(path1));
        final var jsonRef1 = getJsonRef(containerDoc, path1);
        assertEquals("{\"cont1\":{\"$ref\":\"#/components/schemas/container-test_cont_config_cont1\"}}",
            jsonRef1);
        final var xmlRef1 = getXmlRef(containerDoc, path1);
        assertEquals("#/components/schemas/container-test_cont_config_cont1", xmlRef1);

        final var path2 = "/rests/data/container-test:cont/cont1";
        assertNotNull(containerDoc.getPaths().get(path2));
        final var jsonRef2 = getJsonRef(containerDoc, path2);
        assertEquals("{\"list4\":{\"type\":\"array\",\"items\":{\"$ref\":\""
            + "#/components/schemas/container-test_cont_cont1_config_list4\"}}}", jsonRef2);
        final var xmlRef2 = getXmlRef(containerDoc, path2);
        assertEquals("#/components/schemas/container-test_cont_cont1_config_list4", xmlRef2);

        final var path3 = "/rests/data/container-test:cont/cont1/list4={key4}";
        assertNotNull(containerDoc.getPaths().get(path3));
        final var jsonRef3 = getJsonRef(containerDoc, path3);
        assertEquals("{\"cont2\":{\"$ref\":\"#/components/schemas/"
                + "container-test_cont_cont1_list4_config_cont2\"}}", jsonRef3);
        final var xmlRef3 = getXmlRef(containerDoc, path3);
        assertEquals("#/components/schemas/container-test_cont_cont1_list4_config_cont2", xmlRef3);

        final var path4 = "/rests/data/container-test:cont/cont1/list4={key4}/cont2";
        assertNotNull(containerDoc.getPaths().get(path4));
        final var jsonRef4 = getJsonRef(containerDoc, path4);
        assertEquals("{\"list5\":{\"type\":\"array\",\"items\":{\"$ref\":\""
                + "#/components/schemas/container-test_cont_cont1_list4_cont2_config_list5\"}}}", jsonRef4);
        final var xmlRef4 = getXmlRef(containerDoc, path4);
        assertEquals("#/components/schemas/container-test_cont_cont1_list4_cont2_config_list5", xmlRef4);
    }

    @Test
    public void testListsPostPayloads() {
        final var path1 = "/rests/data/list-test:cont";
        assertNotNull(listDoc.getPaths().get(path1));
        final var jsonRef1 = getJsonRef(listDoc, path1);
        assertEquals("{\"list1\":{\"type\":\"array\",\"items\":{\"$ref\":\""
            + "#/components/schemas/list-test_cont_config_list1\"}}}", jsonRef1);
        final var xmlRef1 = getXmlRef(listDoc, path1);
        assertEquals("#/components/schemas/list-test_cont_config_list1", xmlRef1);

        final var path2 = "/rests/data/list-test:cont/list2={key2}";
        assertNotNull(listDoc.getPaths().get(path2));
        final var jsonRef2 = getJsonRef(listDoc, path2);
        assertEquals("{\"list3\":{\"type\":\"array\",\"items\":{\"$ref\":\""
            + "#/components/schemas/list-test_cont_list2_config_list3\"}}}", jsonRef2);
        final var xmlRef2 = getXmlRef(listDoc, path2);
        assertEquals("#/components/schemas/list-test_cont_list2_config_list3", xmlRef2);
    }

    private static String getJsonRef(final OpenApiObject openApiObject, final String path) {
        return openApiObject.getPaths().get(path).get("post").get("requestBody").get(CONTENT_KEY)
            .get("application/json").get(SCHEMA_KEY).get("properties").toString();
    }

    private static String getXmlRef(final OpenApiObject openApiObject, final String path) {
        return openApiObject.getPaths().get(path).get("post").get("requestBody").get(CONTENT_KEY)
            .get("application/xml").get(SCHEMA_KEY).get("$ref").asText();
    }
}
