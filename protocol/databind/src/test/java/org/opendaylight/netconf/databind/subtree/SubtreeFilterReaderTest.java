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
import org.eclipse.jdt.annotation.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opendaylight.netconf.databind.DatabindContext;
import org.opendaylight.netconf.databind.subtree.NamespaceSelection.Exact;
import org.opendaylight.netconf.databind.subtree.NamespaceSelection.Wildcard;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

class SubtreeFilterReaderTest {
    private static final String NAMESPACE = "http://example.com/schema/1.2/config";
    private static final @NonNull DatabindContext DATABIND = DatabindContext.ofModel(
        YangParserTestUtils.parseYangResources(SubtreeFilterReaderTest.class,
            "/top.yang"));

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
                    .add(SelectionNode.builder(exactNamespace(NAMESPACE, "top"))
                        .build()).build()),
            // https://www.rfc-editor.org/rfc/rfc6241#section-6.2.2
            Arguments.of("""
                <filter type="subtree">
                  <t:top xmlns:t="http://example.com/schema/1.2/config">
                    <t:interfaces>
                      <t:interface t:ifName="eth0"/>
                    </t:interfaces>
                  </t:top>
                </filter>""", SubtreeFilter.builder(DATABIND)
                    .add(ContainmentNode.builder(exactNamespace(NAMESPACE, "top"))
                        .add(ContainmentNode.builder(exactNamespace(NAMESPACE, "interfaces"))
                            .add(SelectionNode.builder(exactNamespace(NAMESPACE, "interface"))
                                    // TODO add AttributeMatch
                                .build()).build()).build()).build()),
            // https://www.rfc-editor.org/rfc/rfc6241#section-6.2.3
            Arguments.of("""
                <filter type="subtree">
                  <top xmlns="http://example.com/schema/1.2/config">
                    <users/>
                  </top>
                </filter>""", SubtreeFilter.builder(DATABIND)
                    .add(ContainmentNode.builder(exactNamespace(NAMESPACE, "top"))
                        .add(SelectionNode.builder(exactNamespace(NAMESPACE, "users"))
                            .build())
                        .build()).build()),
            // https://www.rfc-editor.org/rfc/rfc6241#section-6.2.5
            Arguments.of("""
                <filter type="subtree">
                  <top xmlns="http://example.com/schema/1.2/config">
                    <users>
                      <user>
                        <name>fred</name>
                      </user>
                    </users>
                  </top>
                </filter>""", SubtreeFilter.builder(DATABIND)
                    .add(ContainmentNode.builder(exactNamespace(NAMESPACE, "top"))
                        .add(ContainmentNode.builder(exactNamespace(NAMESPACE, "users"))
                            .add(ContainmentNode.builder(exactNamespace(NAMESPACE, "user"))
                                .add(new ContentMatchNode(exactNamespace(NAMESPACE, "name"), "fred"))
                                    .build()).build()).build()).build())
            // custom example to test no namespace xml
//            Arguments.of("""
//                <filter type="subtree">
//                  <top xmlns="http://example.com/schema/1.2/config">
//                    <*:users/>
//                  </top>
//                </filter>""", SubtreeFilter.builder(DATABIND)
//                    .add(ContainmentNode.builder(exactNamespace(NAMESPACE, "top"))
//                        .add(SelectionNode.builder(exactNamespace(NAMESPACE, "users"))
//                            .build()).build()).build())
            // custom example to test multiple namespaces xml
//            Arguments.of("""
//                <filter type="subtree">
//                  <a:top xmlns:a="http://example.com/schema/1.2/config"
//                         xmlns:b="http://example.com/schema/1.2/config2">
//                    <b:users/>
//                  </a:top>
//                </filter>""", SubtreeFilter.builder(DATABIND)
//                    .add(SelectionNode.builder(new Exact(QName.create("http://example.com/schema/1.2/config", "top")))
//                        .build()).build())
        );
    }

    private static NamespaceSelection exactNamespace(final @Nullable String namespace, final String name) {
        return new Exact(NodeIdentifier.create(QName.create(namespace, name)));
    }
}
