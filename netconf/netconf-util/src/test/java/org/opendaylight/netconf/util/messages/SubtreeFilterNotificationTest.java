/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.util.messages;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.google.common.base.Optional;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.diff.Diff;

@RunWith(value = Parameterized.class)
public class SubtreeFilterNotificationTest {
    private static final Logger LOG = LoggerFactory.getLogger(SubtreeFilterRpcTest.class);

    private final int directoryIndex;

    @Parameters
    public static Collection<Object[]> data() {
        final List<Object[]> result = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            result.add(new Object[]{i});
        }
        return result;
    }

    public SubtreeFilterNotificationTest(final int directoryIndex) {
        this.directoryIndex = directoryIndex;
    }

    @Test
    public void testFilterNotification() throws Exception {
        final XmlElement filter = XmlElement.fromDomDocument(getDocument("filter.xml"));
        final Document preFilterDocument = getDocument("pre-filter.xml");
        final Document postFilterDocument = getDocument("post-filter.xml");
        final Optional<Document> actualPostFilterDocumentOpt =
                SubtreeFilter.applySubtreeNotificationFilter(filter, preFilterDocument);
        if (actualPostFilterDocumentOpt.isPresent()) {
            final Document actualPostFilterDocument = actualPostFilterDocumentOpt.get();
            LOG.info("Actual document: {}", XmlUtil.toString(actualPostFilterDocument));
            final Diff diff = DiffBuilder.compare(postFilterDocument)
                    .withTest(actualPostFilterDocument)
                    .ignoreWhitespace()
                    .checkForSimilar()
                    .build();

            assertFalse(diff.toString(), diff.hasDifferences());
        } else {
            assertEquals("empty", XmlElement.fromDomDocument(postFilterDocument).getName());
        }
    }

    public Document getDocument(final String fileName) throws SAXException, IOException {
        return XmlUtil.readXmlToDocument(getClass().getResourceAsStream(
                "/subtree/notification/" + directoryIndex + "/" + fileName));
    }
}
