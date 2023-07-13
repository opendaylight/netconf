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

import java.util.Map;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.openapi.model.Schema;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

/**
 * Test if "xml" nodes are added with correct namespace.
 *
 * <p>
 * We only want to see namespace when it's different from parent's namespace. XML namespace is also in every
 * container and list node. The container and list nodes have namespaces because they refer to other schema objects
 * that can be invoked independently.
 */
public class OpenApiXmlNamespaceTest {
    private static final String TEST_AUGMENTATION_NAMESPACE = "urn:ietf:params:xml:ns:yang:test:augmentation";
    private static final String TEST_MODULE_NAMESPACE = "urn:ietf:params:xml:ns:yang:test:module";
    private static final String MODULE = """
        module module {
          yang-version 1.1;
          namespace "urn:ietf:params:xml:ns:yang:test:module";
          prefix "mod";
          container root {
            container simple-root {
              leaf leaf-a {
                type string;
              }
              leaf leaf-b {
                type string;
              }
            }
            list top-list {
              key "key-1 key-2";

              leaf key-1 {
                type string;
              }
              leaf key-2 {
                type string;
              }
            }
          }
        }
        """;
    private static final String AUG_MODULE = """
        module augmentation {
          yang-version 1.1;
          namespace "urn:ietf:params:xml:ns:yang:test:augmentation";
          prefix "aug";
          import module {
            prefix mod;
          }

          grouping data-1 {
            list list-1 {
              key "leaf-x";
              leaf leaf-x {
                type string;
              }
            }
          }

          grouping data-2 {
            container abc {
              leaf leaf-abc {
                type boolean;
              }
            }
          }

          augment "/mod:root/mod:simple-root" {
            uses data-1;
          }
          augment "/mod:root/mod:simple-root" {
            uses data-2;
          }
          augment "/mod:root/mod:simple-root" {
            leaf leaf-y {
              type string;
            }
          }

          augment "/mod:root/mod:top-list" {
            uses data-1;
          }
          augment "/mod:root/mod:top-list" {
            uses data-2;
          }
          augment "/mod:root/mod:top-list" {
            leaf leaf-y {
              type string;
            }
          }
        }
        """;

    private static Map<String, Schema> schemas;

    @BeforeClass
    public static void startUp() {
        final var context1036 = YangParserTestUtils.parseYang(MODULE, AUG_MODULE);
        final var module = context1036.findModule("module").orElseThrow();
        final var mockSchemaService = mock(DOMSchemaService.class);
        when(mockSchemaService.getGlobalContext()).thenReturn(context1036);
        final var generatorRFC8040 = new OpenApiGeneratorRFC8040(mockSchemaService);
        final var doc = generatorRFC8040.getOpenApiSpec(module, "http", "localhost:8181", "/", "", context1036);
        assertNotNull(doc);
        schemas = doc.components().schemas();
    }

    @Test
    public void testAugmentedListInContainer() {
        final var simpleList1 = schemas.get("module_root_simple-root_list-1");
        assertEquals(TEST_AUGMENTATION_NAMESPACE, simpleList1.xml().path("namespace").asText());
        assertTrue(simpleList1.properties().path("leaf-x").path("xml").isMissingNode());
    }

    @Test
    public void testAugmentedContainerInContainer() {
        final var simpleAbc = schemas.get("module_root_simple-root_abc");
        assertEquals(TEST_AUGMENTATION_NAMESPACE, simpleAbc.xml().path("namespace").asText());
        assertTrue(simpleAbc.properties().path("leaf-abc").path("xml").isMissingNode());
    }

    @Test
    public void testAugmentedLeafInContainer() {
        final var simple = schemas.get("module_root_simple-root");
        assertEquals(TEST_MODULE_NAMESPACE, simple.xml().path("namespace").asText());
        assertEquals(TEST_AUGMENTATION_NAMESPACE, simple.properties().path("leaf-y").path("xml")
            .path("namespace").asText());
        assertTrue(simple.properties().path("leaf-a").path("xml").isMissingNode());
    }

    @Test
    public void testAugmentedListInList() {
        final var topList1 = schemas.get("module_root_top-list_list-1");
        assertEquals(TEST_AUGMENTATION_NAMESPACE, topList1.xml().path("namespace").asText());
        assertTrue(topList1.properties().path("leaf-x").path("xml").isMissingNode());
    }

    @Test
    public void testAugmentedContainerInList() {
        final var topAbc = schemas.get("module_root_top-list_abc");
        assertEquals(TEST_AUGMENTATION_NAMESPACE, topAbc.xml().path("namespace").asText());
        assertTrue(topAbc.properties().path("leaf-abc").path("xml").isMissingNode());
    }

    @Test
    public void testAugmentedLeafInList() {
        final var top = schemas.get("module_root_top-list");
        assertEquals(TEST_MODULE_NAMESPACE, top.xml().path("namespace").asText());
        assertEquals(TEST_AUGMENTATION_NAMESPACE, top.properties().path("leaf-y").path("xml")
            .path("namespace").asText());
        assertTrue(top.properties().path("key-1").path("xml").isMissingNode());
    }
}
