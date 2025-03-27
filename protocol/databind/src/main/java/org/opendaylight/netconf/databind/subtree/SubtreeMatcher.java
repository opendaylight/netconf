/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.databind.subtree;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.yangtools.concepts.Immutable;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;

/**
 * A matcher of a {@link SubtreeFilter} with a {@link ContainerNode}.
 */
public final class SubtreeMatcher implements Immutable {
    private final SubtreeFilter filter;
    private final ContainerNode data;

    SubtreeMatcher(final SubtreeFilter filter, final ContainerNode data) {
        this.filter = requireNonNull(filter);
        this.data = requireNonNull(data);
    }

    public boolean matches() {
        for (final var containment : filter.containments()) {
            if (!matchContainment(containment, data)) {
                return false;
            }
        }
        for (final var contentMatch : filter.contentMatches()) {
            if (!matchContent(contentMatch, data)) {
                return false;
            }
        }
        for (final var selection : filter.selections()) {
            if (!matchSelection(selection, data)) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchContainment(final ContainmentNode filterNode, final ContainerNode node) {
        final var qName = node.name().getNodeType();
        final var selection = filterNode.selection();
        if (selection instanceof NamespaceSelection.Exact exact) {
            if (!qName.equals(exact.qname())) {
                return false;
            }
        } else {
            final NamespaceSelection.Wildcard wc = (NamespaceSelection.Wildcard) selection;
            if (!wc.qnames().contains(qName)) {
                return false;
            }
        }

        for (final ContentMatchNode cm : filterNode.contentMatches()) {
            if (!matchContent(cm, node)) {
                return false;
            }
        }
        for (final ContainmentNode nested : filterNode.containments()) {
            boolean found = false;
            for (final DataContainerChild child : node.body()) {
                if (processChild(nested, child)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        for (final SelectionNode selNode : filterNode.selections()) {
            if (!matchSelection(selNode, node)) {
                return false;
            }
        }
        return true;
    }

    private static boolean processChild(final ContainmentNode filterNode, final DataContainerChild child) {
        final QName childName = child.name().getNodeType();
        final NamespaceSelection sel = filterNode.selection();
        if (sel instanceof NamespaceSelection.Exact ex) {
            if (!childName.equals(ex.qname())) {
                return false;
            }
        } else {
            final NamespaceSelection.Wildcard wc = (NamespaceSelection.Wildcard) sel;
            if (!wc.qnames().contains(childName)) {
                return false;
            }
        }

        if (child instanceof ContainerNode cn) {
            return matchContainment(filterNode, cn);
        }
        if (child instanceof MapNode map) {
            List<ContainmentNode> containmentNodes = new ArrayList<>();
            collectContainments(containmentNodes, filterNode);
            for (final MapEntryNode entry : map.body()) {
                for (final DataContainerChild inner : entry.body()) {
                    final var childQName = inner.name().getNodeType();
                    final var contNode = containmentNodes.stream()
                        .filter(containmentNode -> matchesSelection(containmentNode.selection(), childQName))
                        .findFirst();
                    if (contNode.isPresent()) {
                        if (inner instanceof ContainerNode innerCn) {
                            if (matchContainment(contNode.orElseThrow(), innerCn)) {
                                return true;
                            }
                        } else if (inner instanceof LeafNode<?> leaf) {
                            if (matchesSelection(contNode.orElseThrow().selection(), leaf.name().getNodeType())
                                && evaluateLeaf(contNode.orElseThrow(), leaf)) {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    private static List<ContainmentNode> collectContainments(final List<ContainmentNode> nodes,
        final ContainmentNode node) {
        List<ContainmentNode> containments = node.containments();
        if (!containments.isEmpty()) {
            nodes.addAll(containments);
            for (final var cont : containments) {
                collectContainments(nodes, cont);
            }
        }
        return nodes;
    }

    private static boolean matchesSelection(final NamespaceSelection sel, final QName name) {
        if (sel instanceof NamespaceSelection.Exact ex) {
            return name.equals(ex.qname());
        } else {
            return ((NamespaceSelection.Wildcard) sel).qnames().contains(name);
        }
    }

    private static boolean matchContent(final ContentMatchNode contentMatch, final ContainerNode node) {
        final var expectedMap = contentMatch.qnameValueMap();
        final var sel = contentMatch.selection();
        if (sel instanceof NamespaceSelection.Exact ex) {
            final LeafNode<?> leaf = findLeaf(ex, node);
            if (leaf == null) {
                return false;
            }
            final var actual = leaf.body();
            final var expected = expectedMap.get(ex.qname());
            return matchesValue(expected, actual);
        } else {
            final var wc = (NamespaceSelection.Wildcard) sel;
            for (final QName candidate : wc.qnames()) {
                final LeafNode<?> leaf = findLeaf(new NamespaceSelection.Exact(candidate), node);
                if (leaf != null) {
                    final var actual = leaf.body();
                    final var expected = expectedMap.get(candidate);
                    if (matchesValue(expected, actual)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }


    /**
     * Checks that a selection node (attribute match) is satisfied in the given data node.
     * In this implementation attributes are simulated as child leaf nodes.
     */
    private static boolean matchSelection(final SelectionNode selection, final ContainerNode node) {
        if (selection.attributeMatches().isEmpty()) {
            return true;
        }
        for (final AttributeMatch am : selection.attributeMatches()) {
            final Object actual = getAttributeValue(node, am.selection());
            final Object expected = am.value();
            if (!matchesValue(expected, actual)) {
                return false;
            }
        }
        return true;
    }

    private static @Nullable LeafNode<?> findLeaf(final NamespaceSelection sel, final ContainerNode parent) {
        if (!(sel instanceof NamespaceSelection.Exact ex)) {
            return null;
        }
        final var qname = ex.qname();
        for (final DataContainerChild child : parent.body()) {
            if (child instanceof LeafNode<?> leaf
                && leaf.name().getNodeType().equals(qname)) {
                return leaf;
            }
        }
        return null;
    }

    /**
     * Retrieves an attribute value from the given node.
     * Attributes are simulated as leaf children with matching QName.
     */
    private static @Nullable Object getAttributeValue(ContainerNode node, NamespaceSelection key) {
        if (!(key instanceof NamespaceSelection.Exact exact)) {
            return null;
        }
        final var qname = exact.qname();
        for (final DataContainerChild child : node.body()) {
            if (child instanceof LeafNode<?> leaf && leaf.name().getNodeType().equals(qname)) {
                return leaf.body();
            }
        }
        return null;
    }

    private static boolean evaluateLeaf(final ContainmentNode filterNode, final LeafNode<?> leaf) {
        for (final ContentMatchNode cm : filterNode.contentMatches()) {
            final Object expected = cm.qnameValueMap().get(leaf.name().getNodeType());
            if (!matchesValue(expected, leaf.body())) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesValue(final Object expected, final Object actual) {
        if (expected instanceof String str && actual != null) {
            return str.equals(actual.toString());
        }
        return Objects.equals(expected, actual);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("filter", filter.prettyTree())
            .add("data", data.prettyTree())
            .toString();
    }
}
