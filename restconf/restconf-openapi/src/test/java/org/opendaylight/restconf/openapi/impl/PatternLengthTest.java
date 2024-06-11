/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.BeforeClass;
import org.junit.Test;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.openapi.DocGenTestHelper;
import org.opendaylight.restconf.openapi.model.OpenApiObject;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

public class PatternLengthTest {
    private static OpenApiObject doc;

    @BeforeClass
    public static void startUp() throws Exception {
        final var context = YangParserTestUtils.parseYang("""
            module strings-examples-length {
              yang-version 1.1;
              namespace "urn:ietf:params:xml:ns:yang:strings:examples";
              prefix "str-el";

              typedef MyMacAddress {
                type string {
                  length "12..18";
                  pattern '(([0-9A-Fa-f]{2}):)*';
                }
              }

              typedef MyPhysAddress {
                type string {
                  length "5";
                  pattern '([0-9a-fA-F]{2}(:[0-9a-fA-F]{2})*)?';
                }
              }

              typedef myString {
                type string {
                  length "5 | 10";
                  pattern '[0-9a-fA-F]*';
                }
              }

              container test {
                leaf my-mac-address {
                  type MyMacAddress;
                }
                leaf my-phys-address {
                  type MyPhysAddress;
                }
                leaf my-string {
                  type myString;
                }
              }
            }""");
        final var schemaService = mock(DOMSchemaService.class);
        when(schemaService.getGlobalContext()).thenReturn(context);
        final var generator = new OpenApiGeneratorRFC8040(schemaService, "rests");
        final var uriInfo = DocGenTestHelper.createMockUriInfo("http://localhost/path");
        doc = generator.getApiDeclaration("strings-examples-length", null, uriInfo);
        assertNotNull(doc);
    }

    /**
     * This test is designed to verify taht examples are correctly generated for types which are pattern and length
     * restricted.
     */
    @Test
    public void testPatternLength() {
        final var properties = doc.components().schemas().get("strings-examples-length_test").properties();
        assertEquals("00:00:00:00:", properties.get("my-mac-address").get("example").asText());
        assertEquals("00:00", properties.get("my-phys-address").get("example").asText());
        assertEquals("00000", properties.get("my-string").get("example").asText());
    }
}
