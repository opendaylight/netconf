/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.mdsal.spi;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.time.Instant;
import javax.xml.stream.XMLInputFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.netconf.common.mdsal.DOMNotificationEvent.Rfc7950;
import org.opendaylight.netconf.databind.subtree.SubtreeFilter;
import org.opendaylight.yangtools.databind.DatabindContext;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

/**
 * Unit-test for {@link NotificationSubtreeEventFilter}.
 *
 * <p>The filter is built for the following YANG 1.1 model fragment
 * (see RFC 7950 §7.16.3 “instance notification” example):
 *
 * <pre>
 * container interfaces {
 *   list interface {
 *     key "name";
 *     leaf name { type string; }
 *
 *     notification interface-enabled {
 *       leaf by-user { type string; }
 *     }
 *   }
 * }
 * </pre>
 * The test checks that a notification whose context-path is inside <code>/interfaces/interface[name='eth1']</code>
 * passes the filter, while a path containing an unrelated “foo” element does not.
 */
class NotificationSubtreeEventFilterTest {
    private static final String NS = "urn:example:interface-module";
    private static final String YANG_MODULE = """
            module interface-module {
              yang-version 1.1;
              namespace "%s";
              prefix ifm;

              container interfaces {
                list interface {
                  key "name";
                  leaf name {
                    type string;
                  }

                  notification interface-enabled {
                    leaf by-user {
                      type string;
                    }
                  }
                }
              }
            }
            """.formatted(NS);
    private static final QName INTERFACES_QNAME = QName.create(NS, "interfaces");
    private static final QName INTERFACE_QNAME = QName.create(NS, "interface");
    private static final QName NAME_QNAME = QName.create(NS, "name");
    private static final QName INTERFACE_ENABLED_QNAME = QName.create(NS, "interface-enabled");
    private static final QName BY_USER_QNAME = QName.create(NS, "by-user");

    private static EffectiveModelContext MODEL_CONTEXT = YangParserTestUtils.parseYang(YANG_MODULE);
    private static SubtreeFilter FILTER;

    private final NotificationSubtreeEventFilter eventFilter = new NotificationSubtreeEventFilter(FILTER);

    @BeforeAll
    static void beforeAll() throws Exception {
        MODEL_CONTEXT = YangParserTestUtils.parseYang(YANG_MODULE);
        FILTER = SubtreeFilter.readFrom(DatabindContext.ofModel(MODEL_CONTEXT),
            XMLInputFactory.newFactory().createXMLStreamReader(new StringReader("""
                <filter type="subtree">
                  <ifm:interfaces xmlns:ifm="urn:example:interface-module">
                    <ifm:interface>
                      <ifm:interface-enabled>
                        <ifm:by-user/>
                      </ifm:interface-enabled>
                    </ifm:interface>
                  </ifm:interfaces>
                </filter>""")));
    }

    @Test
    void testPassesWithPermittedPath() {
        assertTrue(eventFilter.test(MODEL_CONTEXT, notification(YangInstanceIdentifier.of(
            new NodeIdentifier(INTERFACES_QNAME),
            NodeIdentifierWithPredicates.of(INTERFACE_QNAME, NAME_QNAME, "eth1")))));
    }

    @Test
    void testFailsWithUnpermittedPath() {
        assertFalse(eventFilter.test(MODEL_CONTEXT, notification(YangInstanceIdentifier.of(
            INTERFACES_QNAME, QName.create(NS, "foo")))));
    }

    private static DOMNotification notification(final YangInstanceIdentifier path) {
        return new Rfc7950(Absolute.of(INTERFACE_ENABLED_QNAME), path, ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(NodeIdentifier.create(INTERFACE_ENABLED_QNAME))
            .withChild(ImmutableNodes.leafNode(BY_USER_QNAME, "fred"))
            .build(), Instant.now());
    }
}
