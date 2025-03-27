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
import java.util.Objects;
import java.util.regex.Pattern;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.yangtools.concepts.Immutable;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;

/**
 * A matcher of a {@link SubtreeFilter} with a {@link ContainerNode}.
 */
@NonNullByDefault
public final class SubtreeMatcher implements Immutable {
    private final SubtreeFilter filter;
    private final ContainerNode data;

    SubtreeMatcher(final SubtreeFilter filter, final ContainerNode data) {
        this.filter = requireNonNull(filter);
        this.data = requireNonNull(data);
    }

    public boolean matches() {
        // Check containment nodes recursively
        for (final var containment : filter.containments()) {
            if (!matchContainment(containment, data)) {
                return false;
            }
        }

        // Check content matches at the top level
        for (final var contentMatch : filter.contentMatches()) {
            if (!matchContent(contentMatch, data)) {
                return false;
            }
        }

        // Check selection nodes (which may enforce attribute matches)
        for (final var selection : filter.selections()) {
            if (!matchSelection(selection, data)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Recursively checks that a containment filter is satisfied in the given parent data node.
     */
    private boolean matchContainment(final ContainmentNode containment, final ContainerNode parent) {
        // Find a child of the parent whose name matches the containment's selection.
        final var child = findChild(parent, containment.selection());
        if (child == null) {
            return false;
        }
        // First, check any content match nodes defined in this containment.
        for (final var cm : containment.contentMatches()) {
            if (!matchContent(cm, child)) {
                return false;
            }
        }
        // Then check any nested containment nodes recursively.
        for (final var nested : containment.containments()) {
            if (!matchContainment(nested, child)) {
                return false;
            }
        }
        // Finally, check any selection nodes (attribute matches) defined in this containment.
        for (final var selection : containment.selections()) {
            if (!matchSelection(selection, child)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks that a content match node is satisfied in the given data node.
     * If the expected value is a String, it is interpreted as a regular expression.
     */
    private boolean matchContent(final ContentMatchNode contentMatch, final ContainerNode node) {
        final var leaf = findLeaf(node, contentMatch.selection());
        if (leaf == null) {
            return false;
        }
        final var actualValue = leaf.body();
        final var expectedValue = contentMatch.value();
        if (expectedValue instanceof String && actualValue != null) {
            final var pattern = Pattern.compile((String) expectedValue);
            return pattern.matcher(actualValue.toString()).matches();
        } else {
            return Objects.equals(expectedValue, actualValue);
        }
    }

    /**
     * Checks that a selection node (attribute match) is satisfied in the given data node.
     * In this implementation attributes are simulated as child leaf nodes.
     */
    private boolean matchSelection(final SelectionNode selection, final ContainerNode node) {
        // If no attribute matches are defined, the existence of the node suffices.
        if (selection.attributeMatches().isEmpty()) {
            return true;
        }
        for (AttributeMatch am : selection.attributeMatches()) {
            final var actualAttr = getAttributeValue(node, am.selection());
            final var expectedAttr = am.value();
            if (expectedAttr instanceof String && actualAttr != null) {
                final var pattern = Pattern.compile((String) expectedAttr);
                if (!pattern.matcher(actualAttr.toString()).matches()) {
                    return false;
                }
            } else if (!Objects.equals(expectedAttr, actualAttr)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Searches for a child of the given parent node whose name matches the provided NamespaceSelection.
     * Here we assume that the selection is of type Exact.
     */
    private @Nullable ContainerNode findChild(final ContainerNode parent, final NamespaceSelection selection) {
        if (!(selection instanceof NamespaceSelection.Exact exact)) {
            return null;
        }
        final var expectedQName = exact.qname();
        final YangInstanceIdentifier.NodeIdentifier expectedNodeId = YangInstanceIdentifier.NodeIdentifier
            .create(expectedQName);
        // First, check the parent's own identifier.
        if (parent.name().equals(expectedNodeId)) {
            return parent;
        }
        // Otherwise, check among the children.
        for (var child : parent.body()) {
            if (child instanceof ContainerNode cn && cn.name().equals(expectedNodeId)) {
                return cn;
            }
            // Optionally, you might check for leaf nodes if the filter expects them.
        }
        return null;
    }

    /**
     * Retrieves an attribute value from the given node.
     * Attributes are simulated as leaf children with matching QName.
     */
    private @Nullable Object getAttributeValue(ContainerNode node, NamespaceSelection key) {
        if (!(key instanceof NamespaceSelection.Exact exact)) {
            return null;
        }
        final var attrQName = exact.qname();
        final YangInstanceIdentifier.NodeIdentifier expectedId = YangInstanceIdentifier.NodeIdentifier
            .create(attrQName);
        for (var child : node.body()) {
            if (child instanceof LeafNode<?> leaf && leaf.name().equals(expectedId)) {
                return leaf.body();
            }
        }
        return null;
    }

    private @Nullable LeafNode<?> findLeaf(final ContainerNode parent, final NamespaceSelection selection) {
        if (!(selection instanceof NamespaceSelection.Exact exact)) {
            return null;
        }
        final var expectedQName = exact.qname();
        final YangInstanceIdentifier.NodeIdentifier expectedId = YangInstanceIdentifier.NodeIdentifier
            .create(expectedQName);
        for (var child : parent.body()) {
            if (child instanceof LeafNode<?> leaf && leaf.name().equals(expectedId)) {
                return leaf;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("filter", filter.prettyTree())
            .add("data", data.prettyTree())
            .toString();
    }
}
