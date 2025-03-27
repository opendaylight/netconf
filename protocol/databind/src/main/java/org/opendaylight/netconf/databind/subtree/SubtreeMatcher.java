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
import org.opendaylight.yangtools.concepts.Immutable;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
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

    private static boolean matchContainment(final ContainmentNode filter, final DataContainerNode parent) {
        if (checkSelection(filter.selection(), parent.name().getNodeType())
            && matchContainerInstance(filter, parent)) {
            return true;
        }
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
            if (!matchSelection(selection, data)) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchMapNode(final ContainmentNode filter, final MapNode map) {
        if (!filter.contentMatches().isEmpty() || !filter.selections().isEmpty()) {
            return false;
        }
        for (final var child : map.body()) {
            var matched = false;
            for (final var ef : filter.containments()) {
                if (matchContainment(ef, child)) {
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchSelection(final SelectionNode sel, final DataContainerNode data) {
        final var selfMatch = checkSelection(sel.selection(), data.name().getNodeType());
        if (selfMatch && sel.attributeMatches().isEmpty()) {
            return true;
        }
        for (final var attribut : sel.attributeMatches()) {
            if (!matchAttributeMatch(attribut, data)) {
                return false;
            }
        }
        if (selfMatch) {
            return true;
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

    private static boolean matchContent(final ContentMatchNode content, final DataContainerNode data) {
        for (final var e : content.qnameValueMap().entrySet()) {
            final var leaf = findLeaf(data, e.getKey());
            if (leaf == null || !Objects.equals(leaf.body(), e.getValue())) {
                return false;
            }
        }
        return true;
    }

    private static boolean checkSelection(final NamespaceSelection sel, final QName qname) {
        return sel instanceof NamespaceSelection.Exact ex
            ? ex.qname().equals(qname)
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

    private static LeafNode<?> findLeaf(final DataContainerNode node, final QName qname) {
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
