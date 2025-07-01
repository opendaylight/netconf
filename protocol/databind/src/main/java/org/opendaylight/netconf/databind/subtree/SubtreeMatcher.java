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
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
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
    private final YangInstanceIdentifier contextPath;

    public SubtreeMatcher(final SubtreeFilter filter, final ContainerNode data) {
        this.filter = requireNonNull(filter);
        this.data = requireNonNull(data);
        this.contextPath = YangInstanceIdentifier.of();
    }

    public SubtreeMatcher(final SubtreeFilter filter, final ContainerNode data,
            final YangInstanceIdentifier contextPath) {
        this.filter = requireNonNull(filter);
        this.data = requireNonNull(data);
        this.contextPath = requireNonNull(contextPath);
    }

    /**
     * Evaluate this matcher against the provided data, honoring an optional context path.
     *
     * <p>If {@code contextPath} is empty, validation starts at the filter root.
     * Otherwise, the method first resolves the deepest {@link SiblingSet}
     * reachable by walking the filter’s containment hierarchy along
     * {@code contextPath} (ancestor path of an instance notification). If any
     * path segment has no matching containment, the method returns {@code false}.
     * If the path is accepted, the notification body is then checked against
     * that {@link SiblingSet} via {@link #matchesFrom(SiblingSet, ContainerNode)}.
     *
     * @return {@code true} if the ancestor path is permitted and the body
     *         satisfies the filter; {@code false} otherwise
     */
    public boolean matches() {
        final var siblingSet = contextPath.isEmpty() ? filter : tailForPath(filter, contextPath);
        if (siblingSet == null) {
            return false;
        }
        return matchesFrom(siblingSet, data);
    }

    /**
     * Return {@code true} when every QName which appears in {@code path} is accepted by this filter’s containment
     * hierarchy.
     *
     * <p>The method walks the path top-down and descends into exactly one containment node on each level;
     * it stops as soon as a segment has no matching containment.
     *
     * <p><b>Note:</b> This validates only the ancestor path of an instance notification (RFC7950). Content inside the
     * notification body is still evaluated later by {@link SubtreeMatcher}.
     */
    public static boolean permitsPath(final SubtreeFilter filter, final YangInstanceIdentifier path) {
        SiblingSet current = filter;
        for (final var arg : path.getPathArguments()) {
            ContainmentNode next = null;
            // look for a containment that accepts this QName
            for (final var cont : current.containments()) {
                if (cont.selection().matches(arg.getNodeType())) {
                    next = cont;
                    break;
                }
            }
            if (next == null) {
                // no match on this level
                return false;
            }
            // continue one level deeper
            current = next;
        }
        return true;
    }

    /**
     * Walk the filter's containment hierarchy following {@code path} and return
     * the deepest {@link SiblingSet} reached. If any segment has no matching
     * containment on the current level, returns {@code null}.
     */
    private static @Nullable SiblingSet tailForPath(final SiblingSet root, final YangInstanceIdentifier path) {
        var current = root;
        for (final var arg : path.getPathArguments()) {
            ContainmentNode next = null;
            for (final var containment : current.containments()) {
                if (containment.selection().matches(arg.getNodeType())) {
                    next = containment;
                    break;
                }
            }
            if (next == null) {
                return null;
            }
            current = next;
        }
        return current;
    }

    /**
     * Check that {@code containerNode} satisfies all siblings in {@code filterSet}
     * at the current level. Returns {@code false} on the first failed check,
     * otherwise {@code true}.
     */
    private boolean matchesFrom(final SiblingSet filterSet, final ContainerNode containerNode) {
        for (final var containment : filterSet.containments()) {
            if (!matchContainment(containment, containerNode)) {
                return false;
            }
        }
        for (final var contentMatch : filterSet.contentMatches()) {
            if (!matchContent(contentMatch, containerNode)) {
                return false;
            }
        }
        for (final var selection : filterSet.selections()) {
            if (!matchSelectionNode(selection, containerNode)) {
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
        boolean matched = false;
        if (checkSelection(filter.selection(), parent.name().getNodeType())) {
            if (!matchContainerInstance(filter, parent)) {
                return false;
            }
            matched = true;
        }
        // Otherwise iterate over direct children with matching QName.
        for (final var child : parent.body()) {
            if (!checkSelection(filter.selection(), child.name().getNodeType())) {
                continue;
            }
            if (switch (child) {
                    case MapNode map -> matchMapNode(filter, map);
                    case DataContainerNode dc -> matchContainerInstance(filter, dc);
                    default -> false;
                }) {
                matched = true;
            } else {
                return false;
            }
        }
        return matched;
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
        if (!(filter.contentMatches().isEmpty() && filter.selections().isEmpty() && filter.containments().isEmpty())) {
            for (final var ch : data.body()) {
                if (!isCovered(ch, filter)) {
                    // extra leaf / container found
                    return false;
                }
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
            if (!filter.contentMatches().isEmpty() || !filter.selections().isEmpty()) {
                for (var ch : entry.body()) {
                    if (!isCovered(ch, filter)) {
                        return false;
                    }
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

    private static boolean isCovered(final DataContainerChild child, final ContainmentNode filter) {
        final var qn = child.name().getNodeType();
        // leaf covered by ContentMatch?
        if (child instanceof LeafNode<?>) {
            for (var cm : filter.contentMatches()) {
                if (cm.qnameValueMap().containsKey(qn)) {
                    return true;
                }
            }
        }
        // leaf or container selected?
        for (var sel : filter.selections()) {
            if (checkSelection(sel.selection(), qn)) {
                return true;
            }
        }
        // container / map / choice matched by nested Containment?
        for (var c : filter.containments()) {
            if (checkSelection(c.selection(), qn)) {
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
