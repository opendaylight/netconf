/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api.xml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.xml.sax.SAXParseException;
import org.xmlunit.builder.Input;
import org.xmlunit.builder.Transform;

class XmlUtilTest {
    @Test
    void testXXEFlaw() {
        assertThrows(SAXParseException.class, () -> XmlUtil.readXmlToDocument("""
            <!DOCTYPE foo [\s\s
            <!ELEMENT foo ANY >
            <!ENTITY xxe SYSTEM "file:///etc/passwd" >]>
            <hello xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">
              <capabilities>
                <capability>urn:ietf:params:netconf:base:1.0 &xxe;</capability>
              </capabilities>
              </hello>]]>]]>"""));
    }

    @Test
    void testEmptyLines() throws Exception {
        // Adapted from https://bugs.openjdk.org/secure/attachment/93338/XmlBugExample.java
        final var input = """
            <users>
                <!-- pre-existing entry BEGIN -->
                <user>
                    <!-- a user -->
                    <name>A name</name>
                    <email>An email</email>
                </user>
                <!-- pre-existing entry END -->
            </users>
            """;

        assertEquals(input, XmlUtil.toString(Transform.source(Input.from(input).build()).build().toDocument()));
    }
}
