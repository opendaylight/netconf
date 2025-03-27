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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opendaylight.netconf.api.subtree.NamespaceSelection.Exact;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.w3c.dom.Element;

class SubtreeFilterFromElementTest {
    private static final String NAMESPACE = "http://example.com/schema/1.2/config";
    private static final String NAMESPACE2 = "http://example.com/schema/1.2/config2";

    @ParameterizedTest
    @MethodSource
    void testExamples(final String xml, final SubtreeFilter expected) throws Exception {
        final Element element = XmlUtil.readXmlToElement(xml);
        final var actual = SubtreeFilter.readFrom(element);
        assertEquals(expected, actual);
    }

    private static List<Arguments> testExamples() {
        return List.of(
            // https://www.rfc-editor.org/rfc/rfc6241#section-6.2.1
            Arguments.of("""
                <filter type="subtree">
                  <top xmlns="http://example.com/schema/1.2/config"/>
                </filter>""", SubtreeFilter.builder()
                .add(SelectionNode.builder(new Exact(NAMESPACE, "top")).build())
                .build()),
            // https://www.rfc-editor.org/rfc/rfc6241#section-6.2.2
            Arguments.of("""
                <filter type="subtree">
                  <t:top xmlns:t="http://example.com/schema/1.2/config">
                    <t:interfaces>
                      <t:interface t:ifName="eth0"/>
                    </t:interfaces>
                  </t:top>
                </filter>""", SubtreeFilter.builder()
                .add(ContainmentNode.builder(new Exact(NAMESPACE, "top"))
                    .add(ContainmentNode.builder(new Exact(NAMESPACE, "interfaces"))
                            .add(SelectionNode.builder(new Exact(NAMESPACE, "interface"))
                            .add(new AttributeMatch(new Exact(NAMESPACE, "ifName"),
                                "eth0")).build())
                        .build())
                    .build())
                .build()),
            // https://www.rfc-editor.org/rfc/rfc6241#section-6.2.3
            Arguments.of("""
                <filter type="subtree">
                  <top xmlns="http://example.com/schema/1.2/config">
                    <users/>
                  </top>
                </filter>""", SubtreeFilter.builder()
                .add(ContainmentNode.builder(new Exact(NAMESPACE, "top"))
                    .add(SelectionNode.builder(new Exact(NAMESPACE, "users"))
                        .build())
                    .build())
                .build()),
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
                </filter>""", SubtreeFilter.builder()
                .add(ContainmentNode.builder(new Exact(NAMESPACE, "top"))
                    .add(ContainmentNode.builder(new Exact(NAMESPACE, "users"))
                        .add(ContainmentNode.builder(new Exact(NAMESPACE, "user"))
                            .add(new ContentMatchNode(new Exact(NAMESPACE, "name"),
                                "fred"))
                            .build())
                        .build())
                    .build())
                .build()),
            // custom example to test no namespace xml
            Arguments.of("""
                <filter type="subtree">
                  <top xmlns="http://example.com/schema/1.2/config">
                    <users xmlns=""/>
                  </top>
                </filter>""", SubtreeFilter.builder()
                .add(ContainmentNode.builder(new Exact(NAMESPACE, "top"))
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
                .add(ContainmentNode.builder(new Exact(NAMESPACE, "top"))
                    .add(SelectionNode.builder(new Exact(NAMESPACE2, "users"))
                        .build())
                    .build())
                .build())
        );
    }
}
