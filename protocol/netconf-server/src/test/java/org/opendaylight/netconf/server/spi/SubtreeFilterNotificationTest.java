/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.spi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import org.custommonkey.xmlunit.Diff;
import org.custommonkey.xmlunit.XMLUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.opendaylight.netconf.api.xml.XmlElement;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

@RunWith(value = Parameterized.class)
public class SubtreeFilterNotificationTest {
    private static final Logger LOG = LoggerFactory.getLogger(SubtreeFilterRpcTest.class);

    private final int directoryIndex;

    @Parameters
    public static Collection<Object[]> data() {
        var result = new ArrayList<Object[]>();
        for (int i = 0; i < 5; i++) {
            result.add(new Object[]{i});
        }
        return result;
    }

    public SubtreeFilterNotificationTest(final int directoryIndex) {
        this.directoryIndex = directoryIndex;
    }

    @Before
    public void setUp() {
        XMLUnit.setIgnoreWhitespace(true);
    }

    @Test
    public void testFilterNotification() throws Exception {
        XmlElement filter = XmlElement.fromDomDocument(getDocument("filter.xml"));
        Document preFilterDocument = getDocument("pre-filter.xml");
        Document postFilterDocument = getDocument("post-filter.xml");
        Optional<Document> actualPostFilterDocumentOpt =
                SubtreeFilter.applySubtreeNotificationFilter(filter, preFilterDocument);
        if (actualPostFilterDocumentOpt.isPresent()) {
            Document actualPostFilterDocument = actualPostFilterDocumentOpt.orElseThrow();
            LOG.info("Actual document: {}", XmlUtil.toString(actualPostFilterDocument));
            Diff diff = XMLUnit.compareXML(postFilterDocument, actualPostFilterDocument);
            assertTrue(diff.toString(), diff.similar());
        } else {
            assertEquals("empty", XmlElement.fromDomDocument(postFilterDocument).getName());
        }
    }

    public Document getDocument(final String fileName) throws SAXException, IOException {
        return XmlUtil.readXmlToDocument(getClass().getResourceAsStream(
                "/subtree/notification/" + directoryIndex + "/" + fileName));
    }
}
