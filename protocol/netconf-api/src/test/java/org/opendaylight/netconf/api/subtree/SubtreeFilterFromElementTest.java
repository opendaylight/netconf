/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api.subtree;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opendaylight.netconf.api.subtree.NamespaceSelection.Exact;
import org.w3c.dom.Element;

class SubtreeFilterFromElementTest {
    @Disabled
    @ParameterizedTest
    @MethodSource
    void testExamples(final String xml, final SubtreeFilter expected) {
        // FIXME: NETCONF-1445 parse XML into a Document and this should be its document element
        final Element element = null;
        assertEquals(expected, SubtreeFilter.readFrom(element));
    }

    private static List<Arguments> testExamples() {
        // FIXME: NETCONF-1445: expand to cover all examples in RFC6241
        return List.of(
            // https://www.rfc-editor.org/rfc/rfc6241#section-6.2.1
            Arguments.of("""
                <filter type="subtree">
                  <top xmlns="http://example.com/schema/1.2/config"/>
                </filter>""", SubtreeFilter.builder()
                .add(SelectionNode.builder(new Exact("xmlns=\"http://example.com/schema/1.2/config", "top")).build())
                .build()));
    }
}
