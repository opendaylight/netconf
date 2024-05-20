/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.spi;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.w3c.dom.Document;
import org.xmlunit.builder.DiffBuilder;

class SubtreeFilterRpcTest {

    @MethodSource
    @ParameterizedTest
    void testFilterRpc(final int directoryIndex) throws Exception {
        final var diff = DiffBuilder
            .compare(SubtreeFilter.applyRpcSubtreeFilter(
                getDocument(directoryIndex, "request.xml"), getDocument(directoryIndex, "pre-filter.xml")))
            .withTest(getDocument(directoryIndex, "post-filter.xml"))
            .ignoreWhitespace()
            .checkForSimilar()
            .build();
        assertFalse(diff.hasDifferences(), diff.toString());
    }

    private static Stream<Integer> testFilterRpc() {
        return Stream.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
    }

    private Document getDocument(final int directoryIndex, final String fileName) throws Exception {
        return XmlUtil.readXmlToDocument(
            SubtreeFilterRpcTest.class.getResourceAsStream("/subtree/rpc/" + directoryIndex + "/" + fileName));
    }
}
