/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import javax.xml.transform.dom.DOMSource;
import org.custommonkey.xmlunit.XMLUnit;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.xml.sax.SAXException;

public class NC881Test {
    @BeforeClass
    public static void classSetUp() {
        XMLUnit.setIgnoreWhitespace(true);
    }

    @Test
    public void testFilterDomNamespaces() throws IOException, SAXException {
        final var source = XmlUtil.readXmlToDocument(
                getClass().getResourceAsStream("/nc881/netconf-state.xml"));
        final var expected = XmlUtil.readXmlToDocument(
                getClass().getResourceAsStream("/nc881/netconf-state-filtered.xml"));

        final var filteredDom = NetconfStateSchemas.ietfMonitoringCopy(new DOMSource(source.getDocumentElement()));
        final var filtered = XmlUtil.newDocument();
        filtered.appendChild(filtered.importNode(filteredDom.getNode(), true));

        final var diff = XMLUnit.compareXML(filtered, expected);
        assertTrue(diff.toString(), diff.similar());
    }
}
