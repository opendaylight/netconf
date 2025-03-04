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
                .add(SelectionNode.builder(new Exact("http://example.com/schema/1.2/config", "top")).build())
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
                .add(ContainmentNode.builder(new Exact("http://example.com/schema/1.2/config", "top"))
                    .add(ContainmentNode.builder(new Exact("http://example.com/schema/1.2/config", "interfaces"))
                            .add(SelectionNode.builder(new Exact("http://example.com/schema/1.2/config", "interface"))
                            .add(new AttributeMatch(new Exact("http://example.com/schema/1.2/config", "ifName"),
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
                .add(ContainmentNode.builder(new Exact("http://example.com/schema/1.2/config", "top"))
                    .add(SelectionNode.builder(new Exact("http://example.com/schema/1.2/config", "users"))
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
                .add(ContainmentNode.builder(new Exact("http://example.com/schema/1.2/config", "top"))
                    .add(ContainmentNode.builder(new Exact("http://example.com/schema/1.2/config", "users"))
                        .add(ContainmentNode.builder(new Exact("http://example.com/schema/1.2/config", "user"))
                            .add(new ContentMatchNode(new Exact("http://example.com/schema/1.2/config", "name"),
                                "fred"))
                            .build())
                        .build())
                    .build())
                .build()));
    }
}
