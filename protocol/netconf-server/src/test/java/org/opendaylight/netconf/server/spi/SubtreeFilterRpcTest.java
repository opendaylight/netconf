/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.spi;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Collection;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.w3c.dom.Document;
import org.xmlunit.builder.DiffBuilder;

public class SubtreeFilterRpcTest {
    private final int directoryIndex;

    public static Collection<Object[]> data() {
        return List.of(
            new Object[] { 0  },
            new Object[] { 1  },
            new Object[] { 2  },
            new Object[] { 3  },
            new Object[] { 4  },
            new Object[] { 5  },
            new Object[] { 6  },
            new Object[] { 7  },
            new Object[] { 8  },
            new Object[] { 9  },
            new Object[] { 10 });
    }

    public SubtreeFilterRpcTest(final int directoryIndex) {
        this.directoryIndex = directoryIndex;
    }

    @ParameterizedTest
    @MethodSource("data")
    public void test() throws Exception {
        final var diff = DiffBuilder
            .compare(SubtreeFilter.applyRpcSubtreeFilter(getDocument("request.xml"), getDocument("pre-filter.xml")))
            .withTest(getDocument("post-filter.xml"))
            .ignoreWhitespace()
            .checkForSimilar()
            .build();
        assertFalse(diff.hasDifferences(), diff.toString());
    }

    private Document getDocument(final String fileName) throws Exception {
        return XmlUtil.readXmlToDocument(
            SubtreeFilterRpcTest.class.getResourceAsStream("/subtree/rpc/" + directoryIndex + "/" + fileName));
    }
}
