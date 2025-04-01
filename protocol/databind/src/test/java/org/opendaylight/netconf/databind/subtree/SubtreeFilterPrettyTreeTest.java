/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.databind.subtree;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opendaylight.netconf.databind.subtree.SubtreeFilterTestUtils.COMPANY_INFO_QNAME;
import static org.opendaylight.netconf.databind.subtree.SubtreeFilterTestUtils.IFNAME_QNAME;
import static org.opendaylight.netconf.databind.subtree.SubtreeFilterTestUtils.INTERFACES_QNAME;
import static org.opendaylight.netconf.databind.subtree.SubtreeFilterTestUtils.INTERFACE_QNAME;
import static org.opendaylight.netconf.databind.subtree.SubtreeFilterTestUtils.NAMESPACE;
import static org.opendaylight.netconf.databind.subtree.SubtreeFilterTestUtils.NAMESPACE2;
import static org.opendaylight.netconf.databind.subtree.SubtreeFilterTestUtils.NAME_QNAME;
import static org.opendaylight.netconf.databind.subtree.SubtreeFilterTestUtils.TOP_QNAME;
import static org.opendaylight.netconf.databind.subtree.SubtreeFilterTestUtils.TYPE_QNAME;
import static org.opendaylight.netconf.databind.subtree.SubtreeFilterTestUtils.USERS_QNAME;
import static org.opendaylight.netconf.databind.subtree.SubtreeFilterTestUtils.USER_QNAME;

import java.util.List;
import java.util.Map;
import org.custommonkey.xmlunit.Diff;
import org.custommonkey.xmlunit.XMLUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.opendaylight.netconf.databind.DatabindContext;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.UnresolvedQName;

class SubtreeFilterPrettyTreeTest {

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
        final var databindContext = Mockito.mock(DatabindContext.class);
        return List.of(
            // https://www.rfc-editor.org/rfc/rfc6241#section-6.2.1
            Arguments.of("""
                <filter type="subtree">
                  <a:top xmlns:a="http://example.com/schema/1.2/config"/>
                </filter>""", SubtreeFilter.builder(databindContext)
                .add(SelectionNode.builder(new NamespaceSelection.Exact(TOP_QNAME)).build())
                .build()),
            // https://www.rfc-editor.org/rfc/rfc6241#section-6.2.2
            Arguments.of("""
                <filter type="subtree">
                  <a:top xmlns:a="http://example.com/schema/1.2/config">
                    <a:interfaces>
                      <a:interface a:ifName="eth0"/>
                    </a:interfaces>
                  </a:top>
                </filter>""", SubtreeFilter.builder(databindContext)
                .add(ContainmentNode.builder(new NamespaceSelection.Exact(TOP_QNAME))
                    .add(ContainmentNode.builder(new NamespaceSelection.Exact(INTERFACES_QNAME))
                        .add(SelectionNode.builder(new NamespaceSelection.Exact(INTERFACE_QNAME))
                            .add(new AttributeMatch(new NamespaceSelection.Exact(IFNAME_QNAME),
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
                </filter>""", SubtreeFilter.builder(databindContext)
                .add(ContainmentNode.builder(new NamespaceSelection.Exact(TOP_QNAME))
                    .add(SelectionNode.builder(new NamespaceSelection.Exact(USERS_QNAME))
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
                </filter>""", SubtreeFilter.builder(databindContext)
                .add(ContainmentNode.builder(new NamespaceSelection.Exact(TOP_QNAME))
                    .add(ContainmentNode.builder(new NamespaceSelection.Exact(USERS_QNAME))
                        .add(ContainmentNode.builder(new NamespaceSelection.Exact(USER_QNAME))
                            .add(new ContentMatchNode(new NamespaceSelection.Exact(NAME_QNAME),
                                Map.of(NAME_QNAME,"fred")))
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
                </filter>""", SubtreeFilter.builder(databindContext)
                .add(ContainmentNode.builder(new NamespaceSelection.Wildcard(UnresolvedQName.Unqualified.of("top"),
                    List.of(QName.create("", "top"))))
                    .add(SelectionNode.builder(new NamespaceSelection.Wildcard(UnresolvedQName.Unqualified.of("users"),
                            List.of(QName.create("", "users"))))
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
                </filter>""", SubtreeFilter.builder(databindContext)
                .add(ContainmentNode.builder(new NamespaceSelection.Exact(TOP_QNAME))
                    .add(SelectionNode.builder(new NamespaceSelection.Exact(QName.create(NAMESPACE2, "users")))
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
                </filter>""", SubtreeFilter.builder(databindContext)
                .add(ContainmentNode.builder(new NamespaceSelection.Exact(TOP_QNAME))
                    .add(ContainmentNode.builder(new NamespaceSelection.Exact(INTERFACES_QNAME))
                        .add(SelectionNode.builder(new NamespaceSelection.Exact(INTERFACE_QNAME))
                            .add(new AttributeMatch(new NamespaceSelection.Exact(IFNAME_QNAME),
                                "eth0'<>\"&abc")).build())
                        .build())
                    .build())
                .build()),
            // test multiple subtrees xml
            // https://www.rfc-editor.org/rfc/rfc6241#section-6.4.7
            Arguments.of("""
                <filter type="subtree">
                  <a:top xmlns:a="http://example.com/schema/1.2/config">
                    <a:users>
                      <a:user>
                        <a:name>root</a:name>
                        <a:company-info/>
                      </a:user>
                      <a:user>
                        <a:name>fred</a:name>
                        <a:company-info>
                          <a:id/>
                        </a:company-info>
                      </a:user>
                      <a:user>
                        <a:name>barney</a:name>
                        <a:type>superuser</a:type>
                        <a:company-info>
                          <a:dept/>
                        </a:company-info>
                      </a:user>
                    </a:users>
                  </a:top>
                </filter>""", SubtreeFilter.builder(databindContext)
                .add(ContainmentNode.builder(new NamespaceSelection.Exact(TOP_QNAME))
                    .add(ContainmentNode.builder(new NamespaceSelection.Exact(USERS_QNAME))
                        .add(ContainmentNode.builder(new NamespaceSelection.Exact(USER_QNAME))
                            .add(new ContentMatchNode(new NamespaceSelection.Exact(NAME_QNAME),
                                Map.of(NAME_QNAME,"root")))
                            .add(SelectionNode.builder(new NamespaceSelection.Exact(COMPANY_INFO_QNAME)).build())
                            .build())
                        .add(ContainmentNode.builder(new NamespaceSelection.Exact(USER_QNAME))
                            .add(new ContentMatchNode(new NamespaceSelection.Exact(NAME_QNAME),
                                Map.of(NAME_QNAME,"fred")))
                            .add(ContainmentNode.builder(new NamespaceSelection.Exact(COMPANY_INFO_QNAME))
                                .add(SelectionNode.builder(
                                        new NamespaceSelection.Exact(QName.create(NAMESPACE, "id")))
                                    .build())
                                .build())
                            .build())
                        .add(ContainmentNode.builder(new NamespaceSelection.Exact(USER_QNAME))
                            .add(new ContentMatchNode(new NamespaceSelection.Exact(NAME_QNAME),
                                Map.of(NAME_QNAME,"barney")))
                            .add(new ContentMatchNode(new NamespaceSelection.Exact(TYPE_QNAME),
                                Map.of(TYPE_QNAME,"superuser")))
                            .add(ContainmentNode.builder(new NamespaceSelection.Exact(COMPANY_INFO_QNAME))
                                .add(SelectionNode.builder(
                                        new NamespaceSelection.Exact(QName.create(NAMESPACE, "dept")))
                                    .build())
                                .build())
                            .build())
                        .build())
                    .build())
                .build()));
    }
}

