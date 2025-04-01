/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.databind.subtree;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringWriter;
import java.util.List;
import javax.xml.stream.XMLOutputFactory;
import org.custommonkey.xmlunit.XMLUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.opendaylight.netconf.databind.DatabindContext;
import org.opendaylight.netconf.databind.subtree.NamespaceSelection.Exact;
import org.opendaylight.netconf.databind.subtree.NamespaceSelection.Wildcard;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.UnresolvedQName;

class SubtreeFilterWriterTest {
    private static final String NAMESPACE = "http://example.com/schema/1.2/config";
    private static final String NAMESPACE2 = "http://example.com/schema/1.2/config2";
    private static final QName TOP_QNAME = QName.create(NAMESPACE, "top");
    private static final QName INTERFACES_QNAME = QName.create(NAMESPACE, "interfaces");
    private static final QName INTERFACE_QNAME = QName.create(NAMESPACE, "interface");
    private static final QName IFNAME_QNAME = QName.create(NAMESPACE, "ifName");
    private static final QName USERS_QNAME = QName.create(NAMESPACE, "users");
    private static final QName USER_QNAME = QName.create(NAMESPACE, "user");
    private static final QName NAME_QNAME = QName.create(NAMESPACE, "name");
    private static final QName COMPANY_INFO_QNAME = QName.create(NAMESPACE, "company-info");

    @BeforeEach
    void setUp() {
        XMLUnit.setIgnoreWhitespace(true);
        XMLUnit.setIgnoreAttributeOrder(true);
    }

    @ParameterizedTest
    @MethodSource
    void testExamples(final String expectedString, final SubtreeFilter filter) throws Exception {
        final var factory = XMLOutputFactory.newFactory();
        final var stringWriter = new StringWriter();
        final var xmlStreamWriter = factory.createXMLStreamWriter(stringWriter);
        SubtreeFilterWriter.writeSubtreeFilter(xmlStreamWriter, filter);
        xmlStreamWriter.close();
        final var actualString = stringWriter.toString();
        final var diff = XMLUnit.compareXML(expectedString, actualString);
        assertTrue(diff.similar(), "Expected:\n" + expectedString + "\nActual:\n" + actualString);
    }

    private static List<Arguments> testExamples() {
        final var databindContext = Mockito.mock(DatabindContext.class);
        return List.of(
            // https://www.rfc-editor.org/rfc/rfc6241#section-6.2.1
            Arguments.of("""
                <filter type="subtree">
                  <a:top xmlns:a="http://example.com/schema/1.2/config"/>
                </filter>""", SubtreeFilter.builder(databindContext)
                .add(SelectionNode.builder(new Exact(TOP_QNAME)).build())
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
                .add(ContainmentNode.builder(new Exact(TOP_QNAME))
                    .add(ContainmentNode.builder(new Exact(INTERFACES_QNAME))
                        .add(SelectionNode.builder(new Exact(INTERFACE_QNAME))
                            .add(new AttributeMatch(new Exact(IFNAME_QNAME), "eth0")).build())
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
                .add(ContainmentNode.builder(new Exact(TOP_QNAME))
                    .add(SelectionNode.builder(new Exact(USERS_QNAME))
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
                .add(ContainmentNode.builder(new Exact(TOP_QNAME))
                    .add(ContainmentNode.builder(new Exact(USERS_QNAME))
                        .add(ContainmentNode.builder(new Exact(USER_QNAME))
                            .add(new ContentMatchNode(new Exact(NAME_QNAME), "fred"))
                            .build())
                        .build())
                    .build())
                .build()),
            // test wildcard namespace xml
            Arguments.of("""
                <filter type="subtree">
                  <a:top xmlns:a="http://example.com/schema/1.2/config">
                    <users xmlns=""/>
                  </a:top>
                </filter>""", SubtreeFilter.builder(databindContext)
                .add(ContainmentNode.builder(new Exact(TOP_QNAME))
                    .add(SelectionNode.builder(new Wildcard(UnresolvedQName.Unqualified.of("users"),
                            List.of(USERS_QNAME, QName.create(NAMESPACE2, "users"),
                                QName.create("", "users"))))
                        .build())
                    .build())
                .build()),
            // test multiple namespaces xml
            Arguments.of("""
                <filter type="subtree">
                  <a:top xmlns:a="http://example.com/schema/1.2/config"
                         xmlns:b="http://example.com/schema/1.2/config2">
                    <b:users/>
                  </a:top>
                </filter>""", SubtreeFilter.builder(databindContext)
                .add(ContainmentNode.builder(new Exact(TOP_QNAME))
                    .add(SelectionNode.builder(new Exact(QName.create(NAMESPACE2, "users")))
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
                .add(ContainmentNode.builder(new Exact(TOP_QNAME))
                    .add(ContainmentNode.builder(new Exact(USERS_QNAME))
                        .add(ContainmentNode.builder(new Exact(USER_QNAME))
                            .add(new ContentMatchNode(new Exact(NAME_QNAME), "root"))
                            .add(SelectionNode.builder(new Exact(COMPANY_INFO_QNAME))
                                .build())
                            .build())
                        .add(ContainmentNode.builder(new Exact(USER_QNAME))
                            .add(new ContentMatchNode(new Exact(NAME_QNAME), "fred"))
                            .add(ContainmentNode.builder(new Exact(COMPANY_INFO_QNAME))
                                .add(SelectionNode.builder(
                                        new Exact(QName.create(NAMESPACE, "id")))
                                    .build())
                                .build())
                            .build())
                        .add(ContainmentNode.builder(new Exact(USER_QNAME))
                            .add(new ContentMatchNode(new Exact(NAME_QNAME), "barney"))
                            .add(new ContentMatchNode(new Exact(QName.create(NAMESPACE, "type")),
                                "superuser"))
                            .add(ContainmentNode.builder(new Exact(COMPANY_INFO_QNAME))
                                .add(
                                    SelectionNode.builder(
                                            new Exact(QName.create(NAMESPACE, "dept")))
                                        .build())
                                .build())
                            .build())
                        .build())
                    .build())
                .build()));
    }
}
