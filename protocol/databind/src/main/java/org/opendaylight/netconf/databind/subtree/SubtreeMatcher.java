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
import java.util.List;
import java.util.Objects;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.yangtools.concepts.Immutable;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;

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
            if (!matchSelectionNode(selection, data)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Try to satisfy {@code filter} against {@code parent} or one of its direct
     * children. Returns {@code true} as soon as one candidate matches.
     */
    private static boolean matchContainment(final ContainmentNode filter, final DataContainerNode parent) {
        // First attempt to match the parent node itself.
        if (checkSelection(filter.selection(), parent.name().getNodeType())
                && matchContainerInstance(filter, parent)) {
            return true;
        }
        // Otherwise iterate over direct children with matching QName.
        for (final var child : parent.body()) {
            if (!checkSelection(filter.selection(), child.name().getNodeType())) {
                continue;
            }
            if (child instanceof MapNode map) {
                if (matchMapNode(filter, map)) {
                    return true;
                }
            } else if (child instanceof DataContainerNode dc) {
                if (matchContainerInstance(filter, dc)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Evaluate a filter against a single <em>non-list</em> container (ordinary
     * container, choice, map-entry).
     */
    private static boolean matchContainerInstance(final ContainmentNode filter, final DataContainerNode data) {
        for (final var content : filter.contentMatches()) {
            if (!matchContent(content, data)) {
                return false;
            }
        }
        for (final var containment : filter.containments()) {
            if (!matchContainment(containment, data)) {
                return false;
            }
        }
        for (final var selection : filter.selections()) {
            if (!matchSelectionNode(selection, data)) {
                return false;
            }
        }
        return true;
    }

    /**
     * List handling: every list entry must be validated by <em>one</em> nested
     * containment in {@code filter}.  If any entry remains unmatched, the data
     * contains “extra” information → fail.
     */
    private static boolean matchMapNode(final ContainmentNode filter, final MapNode map) {
        for (final var entry : map.body()) {
            // validate list-level constraints against the entry
            for (final var contentMatch : filter.contentMatches()) {
                if (!matchContent(contentMatch, entry)) {
                    return false;
                }
            }
            for (final var selection  : filter.selections()) {
                if (!matchSelectionNode(selection, entry)) {
                    return false;
                }
            }

            // entry must match containment, if containments present
            if (!filter.containments().isEmpty()) {
                var matched = false;
                for (final var containment : filter.containments()) {
                    if (matchContainment(containment, entry)) {
                        matched = true;
                        break;
                    }
                }
                if (!matched) {
                    // extra list entry
                    return false;
                }
            }
        }
        return true;
    }

    /** Selection succeeds on the node itself or a direct child, plus attributes. */
    private static boolean matchSelectionNode(final SelectionNode sel, final DataContainerNode data) {
        final var selfMatch = checkSelection(sel.selection(), data.name().getNodeType());
        // If the node itself matches and there are no attribute constraints we are done
        if (selfMatch && sel.attributeMatches().isEmpty()) {
            return true;
        }
        for (final var attribute : sel.attributeMatches()) {
            if (!matchAttributeMatch(attribute, data)) {
                return false;
            }
        }
        for (final var child : data.body()) {
            if (!checkSelection(sel.selection(), child.name().getNodeType())) {
                continue;
            }
            if (sel.attributeMatches().isEmpty()) {
                return true;
            }
            if (child instanceof DataContainerNode containerNode && matchAttributeSet(sel.attributeMatches(),
                containerNode)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchContent(final ContentMatchNode contentMatch, final DataContainerNode data) {
        for (final var e : contentMatch.qnameValueMap().entrySet()) {
            final var leaf = findLeaf(data, e.getKey());
            if (leaf == null || !Objects.equals(leaf.body(), e.getValue())) {
                return false;
            }
        }
        return true;
    }

    /** QName test covering both Exact and Wildcard selections. */
    private static boolean checkSelection(final NamespaceSelection sel, final QName qname) {
        return sel instanceof NamespaceSelection.Exact(final var q)
            ? q.equals(qname)
            : sel instanceof NamespaceSelection.Wildcard wc && wc.qnames().contains(qname);
    }

    private static boolean matchAttributeSet(final List<AttributeMatch> attrs, final DataContainerNode data) {
        for (final var am : attrs) {
            if (!matchAttributeMatch(am, data)) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchAttributeMatch(final AttributeMatch attributeMatch, final DataContainerNode data) {
        final var qname = attributeMatch.selection().qname();
        final var leaf = findLeaf(data, qname);
        return leaf != null && Objects.equals(leaf.body(), attributeMatch.value());
    }

    private static @Nullable LeafNode<?> findLeaf(final DataContainerNode node, final QName qname) {
        for (final var child : node.body()) {
            if (child instanceof LeafNode<?> leaf && leaf.name().getNodeType().equals(qname)) {
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
