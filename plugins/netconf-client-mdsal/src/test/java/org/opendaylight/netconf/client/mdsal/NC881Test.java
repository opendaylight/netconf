/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal;

import static org.junit.jupiter.api.Assertions.assertFalse;

import javax.xml.transform.dom.DOMSource;
import org.junit.jupiter.api.Test;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.xmlunit.builder.DiffBuilder;

class NC881Test {
    @Test
    void testFilterDomNamespaces() throws Exception {
        final var source = XmlUtil.readXmlToDocument(NC881Test.class.getResourceAsStream("/nc881/netconf-state.xml"));
        final var expected = XmlUtil.readXmlToDocument(
            NC881Test.class.getResourceAsStream("/nc881/netconf-state-filtered.xml"));

        final var filteredDom = NetconfStateSchemas.ietfMonitoringCopy(new DOMSource(source.getDocumentElement()));
        final var filtered = XmlUtil.newDocument();
        filtered.appendChild(filtered.importNode(filteredDom.getNode(), true));

        final var diff = DiffBuilder.compare(expected)
            .withTest(filtered)
            .ignoreWhitespace()
            .checkForIdentical()
            .build();
        assertFalse(diff.hasDifferences(), diff.toString());
    }
}
