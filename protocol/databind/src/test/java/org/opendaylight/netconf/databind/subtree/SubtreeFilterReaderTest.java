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
import org.opendaylight.netconf.databind.subtree.NamespaceSelection.Wildcard;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.UnresolvedQName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

class SubtreeFilterReaderTest {
    private static final String NAMESPACE = "http://example.com/schema/1.2/config";
    private static final String NAMESPACE2 = "http://example.com/schema/1.2/config2";
    private static final String REVISION = "2025-03-31";
    private static final String REVISION2 = "2025-04-01";
    private static final @NonNull DatabindContext DATABIND = DatabindContext.ofModel(
        YangParserTestUtils.parseYangResources(SubtreeFilterReaderTest.class,
            "/top.yang", "/top-aug.yang"));

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
                    .add(SelectionNode.builder(exactNamespace(NAMESPACE, REVISION, "top"))
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
                    .add(ContainmentNode.builder(exactNamespace(NAMESPACE, REVISION, "top"))
                        .add(ContainmentNode.builder(exactNamespace(NAMESPACE, REVISION, "interfaces"))
                            .add(SelectionNode.builder(exactNamespace(NAMESPACE, REVISION, "interface"))
                                .add(new AttributeMatch(exactNamespace(NAMESPACE, REVISION, "ifName"), "eth0"))
                                .build()).build()).build()).build()),
            // https://www.rfc-editor.org/rfc/rfc6241#section-6.2.3
            Arguments.of("""
                <filter type="subtree">
                  <top xmlns="http://example.com/schema/1.2/config">
                    <users/>
                  </top>
                </filter>""", SubtreeFilter.builder(DATABIND)
                    .add(ContainmentNode.builder(exactNamespace(NAMESPACE, REVISION, "top"))
                        .add(SelectionNode.builder(exactNamespace(NAMESPACE, REVISION, "users"))
                            .build()).build()).build()),
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
                    .add(ContainmentNode.builder(exactNamespace(NAMESPACE, REVISION, "top"))
                        .add(ContainmentNode.builder(exactNamespace(NAMESPACE, REVISION, "users"))
                            .add(ContainmentNode.builder(exactNamespace(NAMESPACE, REVISION, "user"))
                                .add(new ContentMatchNode(exactNamespace(NAMESPACE, REVISION, "name"), "fred"))
                                    .build()).build()).build()).build()),
            // https://www.rfc-editor.org/rfc/rfc6241#section-6.2.5
            Arguments.of("""
                <filter type="subtree">
                  <top xmlns="http://example.com/schema/1.2/config">
                    <users>
                      <user>
                        <name>root</name>
                        <company-info/>
                      </user>
                      <user>
                        <name>fred</name>
                        <company-info>
                          <id/>
                        </company-info>
                      </user>
                      <user>
                        <name>barney</name>
                        <type>superuser</type>
                        <company-info>
                          <dept/>
                        </company-info>
                      </user>
                    </users>
                  </top>
                </filter>""", SubtreeFilter.builder(DATABIND)
                    .add(ContainmentNode.builder(exactNamespace(NAMESPACE, REVISION, "top"))
                        .add(ContainmentNode.builder(exactNamespace(NAMESPACE, REVISION, "users"))
                            .add(ContainmentNode.builder(exactNamespace(NAMESPACE, REVISION, "user"))
                                .add(new ContentMatchNode(exactNamespace(NAMESPACE, REVISION, "name"), "root"))
                                .add(SelectionNode.builder(exactNamespace(NAMESPACE, REVISION, "company-info"))
                                    .build()).build())
                            .add(ContainmentNode.builder(exactNamespace(NAMESPACE, REVISION, "user"))
                                .add(new ContentMatchNode(exactNamespace(NAMESPACE, REVISION, "name"), "fred"))
                                .add(ContainmentNode.builder(exactNamespace(NAMESPACE, REVISION, "company-info"))
                                    .add(SelectionNode.builder(exactNamespace(NAMESPACE, REVISION, "id"))
                                        .build()).build()).build())
                            .add(ContainmentNode.builder(exactNamespace(NAMESPACE, REVISION, "user"))
                                .add(new ContentMatchNode(exactNamespace(NAMESPACE, REVISION, "name"), "barney"))
                                .add(new ContentMatchNode(exactNamespace(NAMESPACE, REVISION, "type"), "superuser"))
                                .add(ContainmentNode.builder(exactNamespace(NAMESPACE, REVISION, "company-info"))
                                    .add(SelectionNode.builder(exactNamespace(NAMESPACE, REVISION, "dept"))
                                        .build()).build()).build()).build()).build())
                    .build()),
            // custom example to test no namespace xml
            Arguments.of("""
                <filter type="subtree">
                  <top xmlns="">
                    <users/>
                  </top>
                </filter>""", SubtreeFilter.builder(DATABIND)
                    .add(ContainmentNode.builder(new Wildcard(UnresolvedQName.Unqualified.of("top"),
                        List.of(NodeIdentifier.create(QName.create(NAMESPACE, REVISION, "top")))))
                            .add(SelectionNode.builder(new Wildcard(UnresolvedQName.Unqualified.of("users"),
                                List.of(NodeIdentifier.create(QName.create(NAMESPACE, REVISION, "users")),
                                    NodeIdentifier.create(QName.create(NAMESPACE2, REVISION2, "users")))))
                                        .build())
                            .build())
                    .build()),
            // custom example to test mixed filter where not all nodes are wildcard
            Arguments.of("""
                <filter type="subtree">
                  <top xmlns="http://example.com/schema/1.2/config">
                    <users xmlns=""/>
                  </top>
                </filter>""", SubtreeFilter.builder(DATABIND)
                    .add(ContainmentNode.builder(exactNamespace(NAMESPACE, REVISION, "top"))
                        .add(SelectionNode.builder(new Wildcard(UnresolvedQName.Unqualified.of("users"),
                            List.of(NodeIdentifier.create(QName.create(NAMESPACE, REVISION, "users")),
                                NodeIdentifier.create(QName.create(NAMESPACE2, REVISION2, "users")))))
                            .build())
                        .build())
                    .build()),
            // custom example to test multiple namespaces xml
            Arguments.of("""
                <filter type="subtree">
                  <a:top xmlns:a="http://example.com/schema/1.2/config"
                         xmlns:b="http://example.com/schema/1.2/config2">
                    <b:users/>
                  </a:top>
                </filter>""", SubtreeFilter.builder(DATABIND)
                    .add(ContainmentNode.builder(exactNamespace(NAMESPACE, REVISION, "top"))
                        .add(SelectionNode.builder(exactNamespace(NAMESPACE2, REVISION2, "users"))
                            .build())
                        .build())
                    .build()),
            // custom example that should test type parsing for ContentMatchNode
            Arguments.of("""
                <filter type="subtree">
                  <top xmlns="http://example.com/schema/1.2/config">
                    <users>
                      <user>
                        <id>123</id>
                      </user>
                    </users>
                  </top>
                </filter>""", SubtreeFilter.builder(DATABIND)
                    .add(ContainmentNode.builder(exactNamespace(NAMESPACE, REVISION, "top"))
                        .add(ContainmentNode.builder(exactNamespace(NAMESPACE, REVISION, "users"))
                            .add(ContainmentNode.builder(exactNamespace(NAMESPACE, REVISION, "user"))
                                // FIXME ideally content value here should be Uint32
                                .add(new ContentMatchNode(exactNamespace(NAMESPACE, REVISION, "id"), "123"))
                                .build())
                            .build())
                        .build())
                    .build()),
            // custom example to test filter with mixed structure of multiple namespaces and wildcard
            Arguments.of("""
                <filter type="subtree">
                  <t:top xmlns:t="http://example.com/schema/1.2/config"
                         xmlns:y="http://example.com/schema/1.2/config2">
                    <y:users>
                      <y:user>
                        <name>fred</name>
                      </y:user>
                    </y:users>
                    <t:interfaces>
                      <t:interface t:ifName="eth0"/>
                    </t:interfaces>
                  </t:top>
                </filter>""", SubtreeFilter.builder(DATABIND)
                    .add(ContainmentNode.builder(exactNamespace(NAMESPACE, REVISION, "top"))
                    .add(ContainmentNode.builder(exactNamespace(NAMESPACE2, REVISION2, "users"))
                        .add(ContainmentNode.builder(exactNamespace(NAMESPACE2, REVISION2, "user"))
                            .add(new ContentMatchNode(new Wildcard(UnresolvedQName.Unqualified.of("name"),
                                List.of(NodeIdentifier.create(QName.create(NAMESPACE2, REVISION2, "name")))), "fred"))
                            .build())
                        .build())
                    .add(ContainmentNode.builder(exactNamespace(NAMESPACE, REVISION, "interfaces"))
                            .add(SelectionNode.builder(exactNamespace(NAMESPACE, REVISION, "interface"))
                                .add(new AttributeMatch(exactNamespace(NAMESPACE, REVISION, "ifName"), "eth0"))
                                .build())
                            .build())
                        .build())
                    .build()));
    }

    private static Exact exactNamespace(final String namespace, final String revision,
            final String name) {
        return new Exact(NodeIdentifier.create(QName.create(namespace, revision, name)));
    }
}
