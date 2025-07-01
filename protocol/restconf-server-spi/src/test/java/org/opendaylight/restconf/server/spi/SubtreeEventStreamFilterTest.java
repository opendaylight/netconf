/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.netconf.databind.DatabindContext;
import org.opendaylight.netconf.databind.subtree.ContainmentNode;
import org.opendaylight.netconf.databind.subtree.NamespaceSelection;
import org.opendaylight.netconf.databind.subtree.SelectionNode;
import org.opendaylight.netconf.databind.subtree.SubtreeFilter;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;

/**
 * Unit-test for {@link SubtreeEventStreamFilter}.
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
 * The test checks that a notification whose context-path is inside
 * <code>/interfaces/interface[name='eth1']</code> passes the filter,
 * while a path containing an unrelated “foo” element does not.
 */
@ExtendWith(MockitoExtension.class)
class SubtreeEventStreamFilterTest {
    private static final String NS = "urn:example:interface-module";
    private static final QName INTERFACES_QNAME = QName.create(NS, "interfaces");
    private static final QName INTERFACE_QNAME = QName.create(NS, "interface");
    private static final QName NAME_QNAME = QName.create(NS, "name");
    private static final QName INTERFACE_ENABLED_QNAME = QName.create(NS, "interface-enabled");
    private static final QName BY_USER_QNAME = QName.create(NS, "by-user");

    private SubtreeFilter subtreeFilter;
    @Mock
    private DatabindContext context;

    @BeforeEach
    void setUp() {
        // Build subtree-filter that permits
        // /interfaces/interface/name and /interfaces/interface/interface-enabled/by-user
        subtreeFilter = SubtreeFilter.builder(context)
            .add(ContainmentNode.builder(new NamespaceSelection.Exact(INTERFACES_QNAME))
                .add(ContainmentNode.builder(new NamespaceSelection.Exact(INTERFACE_QNAME))
                    .add(SelectionNode.builder(new NamespaceSelection.Exact(NAME_QNAME)).build())
                    .add(ContainmentNode.builder(new NamespaceSelection.Exact(INTERFACE_ENABLED_QNAME))
                        .add(SelectionNode.builder(new NamespaceSelection.Exact(BY_USER_QNAME)).build())
                        .build())
                    .build())
                .build())
            .build();
    }

    @Test
    void testPassesWithPermittedPath() {
        final var path = YangInstanceIdentifier.of(NodeIdentifier.create(INTERFACES_QNAME),
            NodeIdentifierWithPredicates.of(INTERFACE_QNAME, NAME_QNAME, "eth1"));
        final var streamFilter = new SubtreeEventStreamFilter(subtreeFilter);
        assertTrue(streamFilter.test(path, notificationBody()));
    }

    @Test
    void testFailsWithUnpermittedPath() {
        final var fooQname = QName.create(NS, "foo");
        final var invalidPath = YangInstanceIdentifier.of(NodeIdentifier.create(INTERFACES_QNAME))
            .node(NodeIdentifier.create(fooQname));
        final var streamFilter = new SubtreeEventStreamFilter(subtreeFilter);
        assertFalse(streamFilter.test(invalidPath, notificationBody()));
    }

    private static ContainerNode notificationBody() {
        // <interfaces>
        //     <interface>
        //         <name>eth1</name>
        //         <interface-enabled>
        //             <by-user>fred</by-user>
        //         </interface-enabled>
        //      </interface>
        // </interfaces>
        return ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(NodeIdentifier.create(INTERFACES_QNAME))
            .withChild(
                ImmutableNodes.newContainerBuilder()
                    .withNodeIdentifier(NodeIdentifier.create(INTERFACE_QNAME))
                    .withChild(ImmutableNodes.leafNode(NAME_QNAME, "eth1"))
                    .withChild(
                        ImmutableNodes.newContainerBuilder()
                            .withNodeIdentifier(NodeIdentifier.create(INTERFACE_ENABLED_QNAME))
                            .withChild(ImmutableNodes.leafNode(BY_USER_QNAME, "fred"))
                            .build())
                    .build())
            .build();
    }
}
