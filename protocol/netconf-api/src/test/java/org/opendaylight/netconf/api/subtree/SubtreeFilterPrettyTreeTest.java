/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api.subtree;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.custommonkey.xmlunit.Diff;
import org.custommonkey.xmlunit.XMLUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SubtreeFilterPrettyTreeTest {
    private static final String NAMESPACE = "http://example.com/schema/1.2/config";
    private static final String NAMESPACE2 = "http://example.com/schema/1.2/config2";

    @BeforeEach
    void setUp() {
        XMLUnit.setIgnoreWhitespace(true);
        XMLUnit.setIgnoreAttributeOrder(true);
    }

    @ParameterizedTest
    @MethodSource
    void testExamples(final String expectedString, final SubtreeFilter filter) throws Exception {
        final var actual = filter.prettyTree().toString();
        final Diff diff = XMLUnit.compareXML(expectedString, actual);
        assertTrue(diff.similar());
    }

    private static List<Arguments> testExamples() {
        return List.of(
            // https://www.rfc-editor.org/rfc/rfc6241#section-6.2.1
            Arguments.of("""
                <filter type="subtree">
                  <a:top xmlns:a="http://example.com/schema/1.2/config"/>
                </filter>""", SubtreeFilter.builder()
                    .add(SelectionNode.builder(new NamespaceSelection.Exact(NAMESPACE, "top")).build())
                    .build()),
            // https://www.rfc-editor.org/rfc/rfc6241#section-6.2.2
            Arguments.of("""
                <filter type="subtree">
                  <a:top xmlns:a="http://example.com/schema/1.2/config">
                    <a:interfaces>
                      <a:interface a:ifName="eth0"/>
                    </a:interfaces>
                  </a:top>
                </filter>""", SubtreeFilter.builder()
                    .add(ContainmentNode.builder(new NamespaceSelection.Exact(NAMESPACE, "top"))
                        .add(ContainmentNode.builder(new NamespaceSelection.Exact(NAMESPACE, "interfaces"))
                            .add(SelectionNode.builder(new NamespaceSelection.Exact(NAMESPACE, "interface"))
                                .add(new AttributeMatch(new NamespaceSelection.Exact(NAMESPACE, "ifName"),
                                    "eth0")).build())
                            .build())
                        .build())
                    .build()),
            // https://www.rfc-editor.org/rfc/rfc6241#section-6.2.3
            Arguments.of("""
                <filter type="subtree">
                  <a:top xmlns:a="http://example.com/schema/1.2/config">
                    <a:users/>
                  </a:top>
                </filter>""", SubtreeFilter.builder()
                    .add(ContainmentNode.builder(new NamespaceSelection.Exact(NAMESPACE, "top"))
                        .add(SelectionNode.builder(new NamespaceSelection.Exact(NAMESPACE, "users"))
                            .build())
                        .build())
                    .build()),
            // https://www.rfc-editor.org/rfc/rfc6241#section-6.2.5
            Arguments.of("""
                <filter type="subtree">
                  <a:top xmlns:a="http://example.com/schema/1.2/config">
                    <a:users>
                      <a:user>
                        <a:name>fred</a:name>
                      </a:user>
                    </a:users>
                  </a:top>
                </filter>""", SubtreeFilter.builder()
                    .add(ContainmentNode.builder(new NamespaceSelection.Exact(NAMESPACE, "top"))
                        .add(ContainmentNode.builder(new NamespaceSelection.Exact(NAMESPACE, "users"))
                            .add(ContainmentNode.builder(new NamespaceSelection.Exact(NAMESPACE, "user"))
                                .add(new ContentMatchNode(new NamespaceSelection.Exact(NAMESPACE, "name"),
                                    "fred"))
                                .build())
                            .build())
                        .build())
                    .build()),
            // custom example to test no namespace xml
            Arguments.of("""
                <filter type="subtree">
                  <top>
                    <users/>
                  </top>
                </filter>""", SubtreeFilter.builder()
                    .add(ContainmentNode.builder(new NamespaceSelection.Wildcard("top"))
                        .add(SelectionNode.builder(new NamespaceSelection.Wildcard("users"))
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
                </filter>""", SubtreeFilter.builder()
                    .add(ContainmentNode.builder(new NamespaceSelection.Exact(NAMESPACE, "top"))
                        .add(SelectionNode.builder(new NamespaceSelection.Exact(NAMESPACE2, "users"))
                            .build())
                        .build())
                    .build()),
            // Characters escaping
            Arguments.of("""
                <filter type="subtree">
                  <a:top xmlns:a="http://example.com/schema/1.2/config">
                    <a:interfaces>
                      <a:interface a:ifName="eth0&apos;&lt;&gt;&quot;&amp;abc"/>
                    </a:interfaces>
                  </a:top>
                </filter>""", SubtreeFilter.builder()
                    .add(ContainmentNode.builder(new NamespaceSelection.Exact(NAMESPACE, "top"))
                        .add(ContainmentNode.builder(new NamespaceSelection.Exact(NAMESPACE, "interfaces"))
                            .add(SelectionNode.builder(new NamespaceSelection.Exact(NAMESPACE, "interface"))
                                .add(new AttributeMatch(new NamespaceSelection.Exact(NAMESPACE, "ifName"),
                                    "eth0'<>\"&abc")).build())
                            .build())
                        .build())
                    .build()));
    }
}
