/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.util.messages;

import static org.junit.Assert.assertFalse;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.diff.Diff;

@RunWith(value = Parameterized.class)
public class SubtreeFilterRpcTest {
    private static final Logger LOG = LoggerFactory.getLogger(SubtreeFilterRpcTest.class);

    private final int directoryIndex;

    @Parameters
    public static Collection<Object[]> data() {
        List<Object[]> result = new ArrayList<>();
        for (int i = 0; i <= 10; i++) {
            result.add(new Object[]{i});
        }
        return result;
    }

    public SubtreeFilterRpcTest(final int directoryIndex) {
        this.directoryIndex = directoryIndex;
    }

    @Test
    public void test() throws Exception {
        final Document requestDocument = getDocument("request.xml");
        final Document preFilterDocument = getDocument("pre-filter.xml");
        final Document postFilterDocument = getDocument("post-filter.xml");
        final Document actualPostFilterDocument =
                SubtreeFilter.applyRpcSubtreeFilter(requestDocument, preFilterDocument);
        LOG.info("Actual document: {}", XmlUtil.toString(actualPostFilterDocument));

        final Diff diff = DiffBuilder.compare(postFilterDocument)
                .withTest(actualPostFilterDocument)
                .ignoreWhitespace()
                .checkForSimilar()
                .build();

        assertFalse(diff.toString(), diff.hasDifferences());

    }

    private Document getDocument(final String fileName) throws Exception {
        return XmlUtil.readXmlToDocument(
                getClass().getResourceAsStream("/subtree/rpc/" + directoryIndex + "/" + fileName));
    }
}
