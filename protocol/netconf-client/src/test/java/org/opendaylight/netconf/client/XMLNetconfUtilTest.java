/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client;

import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.opendaylight.netconf.api.xml.XmlUtil;

class XMLNetconfUtilTest {
    @Test
    void testXPath() throws Exception {
        final var correctXPath = XMLNetconfUtil.compileXPath("/top/innerText");
        final var ex = assertThrows(IllegalStateException.class,
            () -> XMLNetconfUtil.compileXPath("!@(*&$!"));
        assertThat(ex.getMessage(), startsWith("Error while compiling xpath expression "));
        final var value = NetconfClientSessionNegotiator.evaluateXPath(correctXPath,
                XmlUtil.readXmlToDocument("<top><innerText>value</innerText></top>"));
        assertEquals("value", value.getTextContent());
    }
}
