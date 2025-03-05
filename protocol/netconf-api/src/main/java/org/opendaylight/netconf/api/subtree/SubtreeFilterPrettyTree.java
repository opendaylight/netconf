/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api.subtree;

import static java.util.Objects.requireNonNull;

import java.util.LinkedHashMap;
import java.util.TreeSet;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.yangtools.concepts.PrettyTree;

/**
 * A {@link PrettyTree} of a {@link SubtreeFilter}.
 */
final class SubtreeFilterPrettyTree extends PrettyTree {
    // FIXME: reuse already defined in this package record Prefixes
    private static final class Prefixes {
        final LinkedHashMap<String, String> prefixToNs;

        Prefixes(final LinkedHashMap<String, String> prefixToNs) {
            this.prefixToNs = requireNonNull(prefixToNs);
        }

        @NonNullByDefault
        static Prefixes of(final SubtreeFilter filter) {
            final var namespaces = new TreeSet<String>();
            // FIXME: traverse the filter and fill namespaces

            // LinkedHashMap to preserve ordering
            final var map = LinkedHashMap.<String, String>newLinkedHashMap(namespaces.size());
            int counter = 0;
            for (var ns : namespaces) {
                // FIXME: smarter assignment:
                //        - a, b, c, d, ..., y, z, aa, ab, ..., zz, aaa, aab, ...
                //        - but NOT "xml" or "xmlns", as those are reserved
                map.put(ns, "p" + counter++);
            }

            return new Prefixes(map);
        }

        @NonNullByDefault
        String getPrefix(final String namespace) {
            final var prefix = prefixToNs.get(requireNonNull(namespace));
            if (prefix == null) {
                throw new IllegalStateException("No prefix assigned to namespace " + namespace);
            }
            return prefix;
        }
    }

    private final SubtreeFilter filter;

    SubtreeFilterPrettyTree(final SubtreeFilter filter) {
        this.filter = requireNonNull(filter);
    }

    @Override
    public void appendTo(final StringBuilder sb, final int depth) {
        final var prefixes = Prefixes.of(filter);

        appendIndent(sb, depth);
        sb.append("<filter type=\"subtree\"");
        for (var entry : prefixes.prefixToNs.entrySet()) {
            appendIndent(sb.append('\n'), depth);
            appendQuoted(sb.append("        xmlns:").append(entry.getValue()).append('='), entry.getKey());
        }
        sb.append(">\n");

        appendSiblingSet(sb, depth + 1, prefixes, filter);
        appendIndent(sb, depth);
        sb.append("</filter>");
    }

    private static void appendContainment(final StringBuilder sb, final int depth, final Prefixes prefixes,
            final ContainmentNode node) {
        startSibling(sb, depth, prefixes, node);
        appendSiblingSet(sb, depth + 1, prefixes, node);
        endSibling(sb, depth, prefixes, node);
    }

    private static void appendContentMatch(final StringBuilder sb, final int depth, final Prefixes prefixes,
            final ContentMatchNode node) {
        // FIXME: implement this
        throw new UnsupportedOperationException();
    }

    private static void appendSelection(final StringBuilder sb, final int depth, final Prefixes prefixes,
            final SelectionNode node) {
        startSibling(sb, depth, prefixes, node);

        for (var am : node.attributeMatches()) {
            final var selection = am.selection();
            appendPrefix(sb.append(' '), prefixes, selection.namespace());
            appendQuoted(sb.append(selection.name()).append('='), am.value());
        }

        sb.append("/>\n");
    }

    private static void appendSiblingSet(final StringBuilder sb, final int depth, final Prefixes prefixes,
            final SiblingSet siblings) {
        for (var contentMatch : siblings.contentMatches()) {
            appendContentMatch(sb, depth, prefixes, contentMatch);
        }
        for (var selection : siblings.selections()) {
            appendSelection(sb, depth, prefixes, selection);
        }
        for (var containment : siblings.containments()) {
            appendContainment(sb, depth, prefixes, containment);
        }
    }

    private static void startSibling(final StringBuilder sb, final int depth, final Prefixes prefixes,
            final Sibling node) {
        appendIndent(sb, depth);
        appendNamespaceSelection(sb.append('<'), prefixes, node);
    }

    private static void endSibling(final StringBuilder sb, final int depth, final Prefixes prefixes,
            final Sibling node) {
        appendIndent(sb, depth);
        appendNamespaceSelection(sb.append("</"), prefixes, node);
        sb.append(">\n");
    }

    private static void appendNamespaceSelection(final StringBuilder sb, final Prefixes prefixes, final Sibling node) {
        switch (node.selection()) {
            case NamespaceSelection.Exact(var name, var namespace) -> {
                appendPrefix(sb, prefixes, namespace);
                sb.append(name);
            }
            case NamespaceSelection.Wildcard(var name) -> sb.append(name);
        }
    }

    private static void appendPrefix(final StringBuilder sb, final Prefixes prefixes, final String namespace) {
        sb.append(prefixes.getPrefix(namespace)).append(':');
    }

    private static void appendQuoted(final StringBuilder sb, final String str) {
        // FIXME: correct XML escaping here
        sb.append('"').append(str).append('"');
    }
}
