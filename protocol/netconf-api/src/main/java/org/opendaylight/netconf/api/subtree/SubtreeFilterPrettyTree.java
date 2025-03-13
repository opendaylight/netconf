/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api.subtree;

import static java.util.Objects.requireNonNull;

import org.opendaylight.yangtools.concepts.PrettyTree;

/**
 * A {@link PrettyTree} of a {@link SubtreeFilter}.
 */
final class SubtreeFilterPrettyTree extends PrettyTree {
    private final SubtreeFilter filter;

    SubtreeFilterPrettyTree(final SubtreeFilter filter) {
        this.filter = requireNonNull(filter);
    }

    @Override
    public void appendTo(final StringBuilder sb, final int depth) {
        final var prefixes = Prefixes.of(filter);

        appendIndent(sb, depth);
        sb.append("<filter type=\"subtree\">");
        appendIndent(sb.append('\n'), depth);
        appendSiblingSet(sb, depth + 1, prefixes, filter);
        appendIndent(sb, depth);
        sb.append("</filter>");
    }

    private static void appendContainment(final StringBuilder sb, final int depth, final Prefixes prefixes,
            final ContainmentNode node) {
        startSibling(sb, depth, prefixes, node);
        sb.append(">\n");
        appendSiblingSet(sb, depth + 1, prefixes, node);
        endSibling(sb, depth, prefixes, node);
    }

    private static void appendContentMatch(final StringBuilder sb, final int depth, final Prefixes prefixes,
            final ContentMatchNode node) {
        startSibling(sb, depth, prefixes, node);
        appendIndent(sb.append(">\n"), depth + 1);
        sb.append(node.value()).append('\n');
        endSibling(sb, depth, prefixes, node);
    }

    private static void appendSelection(final StringBuilder sb, final int depth, final Prefixes prefixes,
            final SelectionNode node) {
        startSibling(sb, depth, prefixes, node);

        for (var am : node.attributeMatches()) {
            final var selection = am.selection();
            appendPrefix(sb.append(' '), prefixes, selection.namespace());
            appendAttributeData(sb.append(selection.name()).append('='), am.value());
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
        // adding namespaces and prefixes at the top/first filter node
        if (depth == 1) {
            final var prefixesMap = prefixes.getPrefixToNs();
            if (!prefixesMap.isEmpty()) {
                final var prefixesIt = prefixesMap.entrySet().iterator();
                // this never throws error because map not empty
                var entry = prefixesIt.next();
                // add first namespaces and prefix
                appendAttributeData(sb.append(" xmlns:").append(entry.getValue()).append('='), entry.getKey());
                // add other namespaces if there are some with indentation
                while (prefixesIt.hasNext()) {
                    entry = prefixesIt.next();
                    appendIndent(sb.append('\n'), depth);
                    appendAttributeData(sb.append(" xmlns:").append(entry.getValue()).append('='), entry.getKey());
                }
            }
        }
    }

    private static void endSibling(final StringBuilder sb, final int depth, final Prefixes prefixes,
            final Sibling node) {
        appendIndent(sb, depth);
        appendNamespaceSelection(sb.append("</"), prefixes, node);
        sb.append(">\n");
    }

    private static void appendNamespaceSelection(final StringBuilder sb, final Prefixes prefixes, final Sibling node) {
        switch (node.selection()) {
            case NamespaceSelection.Exact(var namespace, var name) -> {
                appendPrefix(sb, prefixes, namespace);
                sb.append(name);
            }
            case NamespaceSelection.Wildcard(var name) -> sb.append(name);
        }
    }

    private static void appendPrefix(final StringBuilder sb, final Prefixes prefixes, final String namespace) {
        sb.append(prefixes.getPrefix(namespace)).append(':');
    }

    // Append xml attribute data surrounded with double quotes
    private static void appendAttributeData(final StringBuilder sb, final String str) {
        // replace double quotes inside string with correct escaping to avoid conflicts
        sb.append('"').append(escapeDoubleQuotes(str)).append('"');
    }

    private static String escapeDoubleQuotes(final String str) {
        return str.replaceAll("\"", "&quot;");
    }
}
