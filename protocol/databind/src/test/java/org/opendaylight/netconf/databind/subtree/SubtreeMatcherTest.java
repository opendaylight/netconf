/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.databind.subtree;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.opendaylight.netconf.databind.DatabindContext;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;

class SubtreeMatcherTest {
    private static final String NAMESPACE = "http://example.com/schema/1.2/config";
    private static final QName NAME_QNAME = QName.create(NAMESPACE, "name");
    private static final QName USER_QNAME = QName.create(NAMESPACE, "user");
    private static final QName USERS_QNAME = QName.create(NAMESPACE, "users");
    private static final QName INTERFACE_QNAME = QName.create(NAMESPACE, "interface");
    private static final QName INTERFACES_QNAME = QName.create(NAMESPACE, "interfaces");
    private static final QName TOP_QNAME = QName.create(NAMESPACE, "top");
    @Mock
    private DatabindContext databindContext;

    //Test that a simple selection filter(a top-level element) matches a pure ContainerNode.
    @Test
    void testSimpleSelectionMatch() {
        // Filter: <top xmlns="http://example.com/schema/1.2/config"/>
        final var filter = SubtreeFilter.builder(databindContext)
            .add(SelectionNode.builder(new NamespaceSelection.Exact(TOP_QNAME)).build())
            .build();

        // Build a ContainerNode representing <top xmlns="...">
        final var top = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(YangInstanceIdentifier.NodeIdentifier.create(TOP_QNAME))
            .build();

        final var matcher = new SubtreeMatcher(filter, top);
        assertTrue(matcher.matches(), "Simple selection match should succeed");
    }

        @Test
    void testNestedContainmentMatch() {
        SubtreeFilter filter = SubtreeFilter.builder(databindContext)
            .add(ContainmentNode.builder(new NamespaceSelection.Exact(QName.create(NAMESPACE, "top")))
                .add(ContainmentNode.builder(new NamespaceSelection.Exact(NAMESPACE, "interfaces"))
                    .add(SelectionNode.builder(new NamespaceSelection.Exact(NAMESPACE, "interface"))
                        .add(new AttributeMatch(new NamespaceSelection.Exact(NAMESPACE, "ifName"), "eth0"))
                        .build())
                    .build())
                .build())
            .build();

        // Build data tree using pure ContainerNode instances.

        // Create the "interface" node with attribute ifName="eth0".
        ContainerNode interfaceNode = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(INTERFACE_QNAME)
            .withAttribute("ifName", "eth0")  // Assumed fluent API for attribute assignment.
            .build();

        // Create the "interfaces" node with the "interface" node as child.
        ContainerNode interfacesNode = ImmutableNodes.newContainerBuilder(INTERFACES_QNAME)
            .withQName(INTERFACES_QNAME)
            .addChild(interfaceNode)
            .build();
        // Create the "top" node with the "interfaces" node as child.
        ContainerNode top = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(TOP_QNAME)
            .addChild(interfacesNode)
            .build();

        SubtreeMatcher matcher = new SubtreeMatcher(filter, top);
        assertTrue(matcher.matches(), "Nested containment match should succeed");
    }

    @Test
    void testContentMatch() {
        SubtreeFilter filter = SubtreeFilter.builder()
            .add(ContainmentNode.builder(new NamespaceSelection.Exact(TOP_QNAME))
            .add(ContainmentNode.builder(new NamespaceSelection.Exact(NAMESPACE, "users"))
                .add(ContainmentNode.builder(new NamespaceSelection.Exact(NAMESPACE, "user"))
                    .add(new ContentMatchNode(new NamespaceSelection.Exact(NAMESPACE, "name"), "fred"))
                    .build())
                .build())
            .build())
            .build();

        // Create the leaf node <name>fred</name>
        LeafNode<String> nameNode = ImmutableNodes.leafNode(NAME_QNAME, "fred");

        // Build the hierarchy: user → users → top
        ContainerNode user = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(YangInstanceIdentifier.NodeIdentifier.create(USER_QNAME))
            .addChild(nameNode)
            .build();

        ContainerNode users = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(YangInstanceIdentifier.NodeIdentifier.create(USERS_QNAME))
            .addChild(user)
            .build();

        ContainerNode top = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(YangInstanceIdentifier.NodeIdentifier.create(TOP_QNAME))
            .addChild(users)
            .build();

        SubtreeMatcher matcher = new SubtreeMatcher(filter, top);
        assertTrue(matcher.matches(), "Content match should succeed");
    }


    /* Test that an attribute mismatch causes the match to fail.
     * <pre>
     * Filter:
     *   &lt;item xmlns="http://example.com/schema/1.2/config" attr="value"/&gt;
     *
     * Data:
     *   item node with attribute attr="wrong"
     * </pre>
     */
    @Test
    void testAttributeMismatch() {
        SubtreeFilter filter = SubtreeFilter.builder()
            .add(SelectionNode.builder(new NamespaceSelection.Exact(NAMESPACE, "item"))
                .add(new AttributeMatch(new NamespaceSelection.Exact(NAMESPACE, "attr"), "value"))
                .build())
            .build();

        QName itemQName = QName.create(NAMESPACE, "item");

        ContainerNode item = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(itemQName)
            .withAttribute("attr", "wrong")  // Attribute value differs.
            .build();

        SubtreeMatcher matcher = new SubtreeMatcher(filter, item);
        assertFalse(matcher.matches(), "Attribute mismatch should fail");
    }

    /**
     * Test that non-matching content causes the match to fail.
     * <pre>
     * Filter:
     *   &lt;top xmlns="http://example.com/schema/1.2/config"&gt;
     *     &lt;users&gt;
     *       &lt;user&gt;
     *         &lt;name&gt;fred&lt;/name&gt;
     *       &lt;/user&gt;
     *     &lt;/users&gt;
     *   &lt;/top&gt;
     *
     * Data:
     *   top → users → user → name (leaf node with value "john" instead of "fred")
     * </pre>
     */
    @Test
    void testNonMatchingContent() {
        SubtreeFilter filter = SubtreeFilter.builder()
            .add(ContainmentNode.builder(new NamespaceSelection.Exact(NAMESPACE, "top"))
                .add(ContainmentNode.builder(new NamespaceSelection.Exact(NAMESPACE, "users"))
                    .add(ContainmentNode.builder(new NamespaceSelection.Exact(NAMESPACE, "user"))
                        .add(new ContentMatchNode(new NamespaceSelection.Exact(NAMESPACE, "name"), "fred"))
                        .build())
                    .build())
                .build())
            .build();

        QName topQName = QName.create(NAMESPACE, "top");
        QName usersQName = QName.create(NAMESPACE, "users");
        QName userQName = QName.create(NAMESPACE, "user");
        QName nameQName = QName.create(NAMESPACE, "name");

        // Create the leaf node <name>john</name>
        LeafNode<String> nameNode = ImmutableNodes.leafNode(nameQName, "john");

        ContainerNode user = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(userQName)
            .addChild(nameNode)
            .build();

        ContainerNode users = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(usersQName)
            .addChild(user)
            .build();

        ContainerNode top = ImmutableNodes.newContainerBuilder()
            .withNodeIdentifier(topQName)
            .addChild(users)
            .build();

        SubtreeMatcher matcher = new SubtreeMatcher(filter, top);
        assertFalse(matcher.matches(), "Non-matching content should fail");
    }
}

