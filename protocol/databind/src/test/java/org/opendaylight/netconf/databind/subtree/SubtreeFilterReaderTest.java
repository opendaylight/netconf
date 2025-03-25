/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.databind.subtree;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.util.List;
import javax.xml.stream.XMLInputFactory;
import org.eclipse.jdt.annotation.NonNull;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opendaylight.netconf.databind.DatabindContext;
import org.opendaylight.netconf.databind.subtree.NamespaceSelection.Exact;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

class SubtreeFilterReaderTest {
    public static final @NonNull DatabindContext DATABIND = DatabindContext.ofModel(
        YangParserTestUtils.parseYangResources(SubtreeFilterReaderTest.class,
            "/test-model-aug.yang",
            "/test-model.yang"));

    @ParameterizedTest
    @MethodSource
    void testExamples(final String xml, final SubtreeFilter expected) throws Exception {
        final var inputStream = new ByteArrayInputStream(xml.getBytes());
        final var reader = XMLInputFactory.newFactory().createXMLStreamReader(inputStream);
        final var actual = SubtreeFilter.readFrom(DATABIND, reader);
        assertEquals(expected, actual);
    }

    private static List<Arguments> testExamples() {
        return List.of(
            // https://www.rfc-editor.org/rfc/rfc6241#section-6.2.1
            Arguments.of("""
                <filter type="subtree">
                  <top xmlns="http://example.com/schema/1.2/config"/>
                </filter>""", SubtreeFilter.builder(DATABIND)
                    .add(SelectionNode.builder(new Exact(QName.create("http://example.com/schema/1.2/config", "top")))
                        .build()).build()));
    }
}
