/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.databind.subtree;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.netconf.databind.DatabindContext;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.UnresolvedQName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;

@ExtendWith(MockitoExtension.class)
class SubtreeMatcherTest {
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
    private static final QName ANOTHER_NAME_QNAME = QName.create(NAMESPACE2, "name");

    @Mock
    private DatabindContext mockedContext;


    @Test
    void testSimpleSelectionMatch() {
        final var filter = SubtreeFilter.builder(mockedContext)
            .add(SelectionNode.builder(new NamespaceSelection.Exact(TOP_QNAME))
                .build())
            .build();

        final var top = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(NodeIdentifier.create(TOP_QNAME))
            .build();

        final var matcher = new SubtreeMatcher(filter, top);
        assertTrue(matcher.matches(), "Simple selection match should succeed");
    }

    @Test
    void testNestedContainmentMatch() {
        /* Filter structure:
         * <top xmlns="http://example.com/schema/1.2/config">
         *   <interfaces>
         *     <interface>
         *       <ifName>eth0</ifName>
         *     </interface>
         *   </interfaces>
         * </top>
         */
        final var filter = SubtreeFilter.builder(mockedContext)
            .add(ContainmentNode.builder(new NamespaceSelection.Exact(TOP_QNAME))
                .add(ContainmentNode.builder(new NamespaceSelection.Exact(INTERFACES_QNAME))
                    .add(ContainmentNode.builder(new NamespaceSelection.Exact(INTERFACE_QNAME))
                        .add(new ContentMatchNode(new NamespaceSelection.Exact(IFNAME_QNAME),
                            Map.of(IFNAME_QNAME, "eth0")))
                        .build())
                    .build())
                .build())
            .build();

        // Build data tree: <top> → <interfaces> → <interface ifName="eth0"/>
        final var ifNameLeaf = ImmutableNodes.leafNode(IFNAME_QNAME, "eth0");
        final var interfaceNode = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(NodeIdentifier.create(INTERFACE_QNAME))
            .addChild(ifNameLeaf)
            .build();
        final var interfacesNode = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(NodeIdentifier.create(INTERFACES_QNAME))
            .addChild(interfaceNode)
            .build();
        final var top = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(NodeIdentifier.create(TOP_QNAME))
            .addChild(interfacesNode)
            .build();

        final var matcher = new SubtreeMatcher(filter, top);
        assertTrue(matcher.matches(), "Nested containment match should succeed");
    }

    @Test
    void testContentMatch() {
        /* Filter structure:
         * <top xmlns="http://example.com/schema/1.2/config">
         *   <users>
         *     <user>
         *       <name>fred</name>
         *     </user>
         *   </users>
         * </top>
         */
        final var filter = SubtreeFilter.builder(mockedContext)
            .add(ContainmentNode.builder(new NamespaceSelection.Exact(TOP_QNAME))
                .add(ContainmentNode.builder(new NamespaceSelection.Exact(USERS_QNAME))
                    .add(ContainmentNode.builder(new NamespaceSelection.Exact(USER_QNAME))
                        .add(new ContentMatchNode(new NamespaceSelection.Exact(NAME_QNAME), Map.of(NAME_QNAME, "fred")))
                        .build())
                    .build())
                .build())
            .build();

        // Build data tree:
        // <top>
        //   <users>
        //     <user>
        //       <name>fred</name>
        //     </user>
        //   </users>
        // </top>
        final var nameLeaf = ImmutableNodes.leafNode(NAME_QNAME, "fred");
        final var user = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(NodeIdentifier.create(USER_QNAME))
            .addChild(nameLeaf)
            .build();
        final var users = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(NodeIdentifier.create(USERS_QNAME))
            .addChild(user)
            .build();
        final var top = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(NodeIdentifier.create(TOP_QNAME))
            .addChild(users)
            .build();

        final var matcher = new SubtreeMatcher(filter, top);
        assertTrue(matcher.matches(), "Content match should succeed");
    }

    @Test
    void testAttributeMismatch() {
        /* Filter structure:
         * <item xmlns="http://example.com/schema/1.2/config" company-info="value"/>
         */
        final var filter = SubtreeFilter.builder(mockedContext)
            .add(SelectionNode.builder(new NamespaceSelection.Exact(TOP_QNAME))
                .add(new AttributeMatch(new NamespaceSelection.Exact(COMPANY_INFO_QNAME), "value"))
                .build())
            .build();

        // Build data node <top> with simulated attribute as a leaf node "company-info" with value "wrong"
        final var attrLeaf = ImmutableNodes.leafNode(COMPANY_INFO_QNAME, "wrong");
        final var top = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(NodeIdentifier.create(TOP_QNAME))
            .addChild(attrLeaf)
            .build();

        final var matcher = new SubtreeMatcher(filter, top);
        assertFalse(matcher.matches(), "Attribute mismatch should fail");
    }

    @Test
    void testNonMatchingContent() {
        /* Filter structure:
         * <top xmlns="http://example.com/schema/1.2/config">
         *   <users>
         *     <user>
         *       <name>fred</name>
         *     </user>
         *   </users>
         * </top>
         */
        final var filter = SubtreeFilter.builder(mockedContext)
            .add(ContainmentNode.builder(new NamespaceSelection.Exact(TOP_QNAME))
                .add(ContainmentNode.builder(new NamespaceSelection.Exact(USERS_QNAME))
                    .add(ContainmentNode.builder(new NamespaceSelection.Exact(USER_QNAME))
                        .add(new ContentMatchNode(new NamespaceSelection.Exact(NAME_QNAME), Map.of(NAME_QNAME, "fred")))
                        .build())
                    .build())
                .build())
            .build();

        // Build data tree with non-matching content: <name>john</name> instead of "fred"
        final var nameLeaf = ImmutableNodes.leafNode(NAME_QNAME, "john");
        final var user = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(NodeIdentifier.create(USER_QNAME))
            .addChild(nameLeaf)
            .build();
        final var users = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(NodeIdentifier.create(USERS_QNAME))
            .addChild(user)
            .build();
        final var top = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(NodeIdentifier.create(TOP_QNAME))
            .addChild(users)
            .build();

        final var matcher = new SubtreeMatcher(filter, top);
        assertFalse(matcher.matches(), "Non-matching content should fail");
    }

    @Test
    void testWildcardContentMatch() {
        // Create a wildcard selection for a leaf with local name "name" (from either namespace)
        final var wildcardSelection = new NamespaceSelection.Wildcard(UnresolvedQName.Unqualified.of("name"),
            List.of(NAME_QNAME, ANOTHER_NAME_QNAME)
        );
        // Build a filter with a ContentMatchNode that expects a mapping for one of the candidate QNames.
        final var filter = SubtreeFilter.builder(mockedContext)
            .add(new ContentMatchNode(wildcardSelection, Map.of(NAME_QNAME, "fred")))
            .build();

        // Build a data tree: a container with a child leaf having QName NAME_QNAME and value "fred"
        final var container = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(NodeIdentifier.create(TOP_QNAME))
            .addChild(ImmutableNodes.leafNode(NAME_QNAME, "fred"))
            .build();

        final var matcher = new SubtreeMatcher(filter, container);
        assertTrue(matcher.matches(), "Wildcard content match should succeed");
    }

}

