/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.databind.subtree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringWriter;
import java.util.List;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;
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
    private static final List<QName> Q_NAMES = List.of(TOP_QNAME, INTERFACES_QNAME, INTERFACE_QNAME, IFNAME_QNAME,
        USERS_QNAME, USER_QNAME, NAME_QNAME);

    @BeforeEach
    void setUp() {
        XMLUnit.setIgnoreWhitespace(true);
        XMLUnit.setIgnoreAttributeOrder(true);
    }

    @ParameterizedTest
    @MethodSource
    void testExamples(final String expectedString, final SubtreeFilter filter) throws Exception {
/*        final var element = XmlUtil.newDocument().createElementNS(null, XmlNetconfConstants.FILTER);
        element.setAttribute("type", "subtree");
        filter.writeTo(element);
        final var elementString = XmlUtil.toString(element);*/

        XMLOutputFactory factory = XMLOutputFactory.newFactory();
        final var stringWriter = new StringWriter();
        XMLStreamWriter xmlStreamWriter = factory.createXMLStreamWriter(stringWriter);
        SubtreeFilterWriter.writeSubtreeFilter(xmlStreamWriter, filter);
        xmlStreamWriter.close();
        String actualString = xmlStreamWriter.toString();
        // comparing xml strings
        final Diff diff = XMLUnit.compareXML(expectedString, actualString);
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
                                "fred"))
                            .build())
                        .build())
                    .build())
                .build()),
            // custom example to test wildcard namespace xml
            Arguments.of("""
                <filter type="subtree">
                  <a:top xmlns:a="http://example.com/schema/1.2/config">
                    <users xmlns=""/>
                  </a:top>
                </filter>""", SubtreeFilter.builder(databindContext)
                .add(ContainmentNode.builder(new NamespaceSelection.Exact(TOP_QNAME))
                    .add(SelectionNode.builder(new NamespaceSelection.Wildcard(UnresolvedQName.Unqualified.of("users"),
                            Q_NAMES))
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
                .build()));
    }
}
