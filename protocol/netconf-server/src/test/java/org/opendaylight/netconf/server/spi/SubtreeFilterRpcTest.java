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

    static Stream<Integer> data() {
        return Stream.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
    }

    @ParameterizedTest
    @MethodSource("data")
    public void test(final int directoryIndex) throws Exception {
        final var diff = DiffBuilder
            .compare(SubtreeFilter.applyRpcSubtreeFilter(getDocument("request.xml", directoryIndex),
                    getDocument("pre-filter.xml", directoryIndex)))
            .withTest(getDocument("post-filter.xml", directoryIndex))
            .ignoreWhitespace()
            .checkForSimilar()
            .build();
        assertFalse(diff.hasDifferences(), diff.toString());
    }

    private Document getDocument(final String fileName, final int directoryIndex) throws Exception {
        return XmlUtil.readXmlToDocument(
            SubtreeFilterRpcTest.class.getResourceAsStream("/subtree/rpc/" + directoryIndex + "/" + fileName));
    }
}
