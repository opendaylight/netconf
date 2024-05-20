/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.opendaylight.netconf.api.xml.XmlElement;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.w3c.dom.Document;
import org.xmlunit.builder.DiffBuilder;

class SubtreeFilterNotificationTest {

    @MethodSource("directoryIndex")
    @ParameterizedTest
    void testFilterNotification(final int directoryIndex) throws Exception {
        final var filter = XmlElement.fromDomDocument(getDocument(directoryIndex, "filter.xml"));
        final var preFilter = getDocument(directoryIndex, "pre-filter.xml");
        final var expectedPostFilter = getDocument(directoryIndex, "post-filter.xml");
        final var postFilterOpt = SubtreeFilter.applySubtreeNotificationFilter(filter, preFilter);
        if (postFilterOpt.isPresent()) {
            final var diff = DiffBuilder.compare(postFilterOpt.orElseThrow())
                .withTest(expectedPostFilter)
                .ignoreWhitespace()
                .checkForIdentical()
                .build();
            assertFalse(diff.hasDifferences(), diff.toString());
        } else {
            assertEquals("empty", XmlElement.fromDomDocument(expectedPostFilter).getName());
        }
    }

    private Document getDocument(final int directoryIndex, final String fileName) throws Exception {
        return XmlUtil.readXmlToDocument(SubtreeFilterNotificationTest.class.getResourceAsStream(
                "/subtree/notification/" + directoryIndex + "/" + fileName));
    }

    static Stream<Integer> directoryIndex() {
        return Stream.of(0, 1, 2, 3, 4);
    }
}
