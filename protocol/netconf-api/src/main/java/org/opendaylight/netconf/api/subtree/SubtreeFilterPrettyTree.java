/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api.subtree;

import static java.util.Objects.requireNonNull;

import com.google.common.xml.XmlEscapers;
import java.io.IOException;
import org.opendaylight.yangtools.concepts.PrettyTree;

/**
 * A {@link PrettyTree} of a {@link SubtreeFilter}.
 */
final class SubtreeFilterPrettyTree extends PrettyTree {
    private final SubtreeFilter filter;
    private final Prefixes prefixes;

    SubtreeFilterPrettyTree(final SubtreeFilter filter) {
        this.filter = requireNonNull(filter);
        prefixes = Prefixes.of(filter);
    }

    @Override
    public Appendable appendTo(final Appendable appendable, final int depth) throws IOException {
        appendIndent(appendable, depth);
        appendable.append("<filter type=\"subtree\">");
        appendIndent(appendable.append('\n'), depth);
        appendSiblingSet(appendable, depth + 1, prefixes, filter);
        appendIndent(appendable, depth);
        return appendable.append("</filter>");
    }

    private static void appendContainment(final Appendable appendable, final int depth, final Prefixes prefixes,
            final ContainmentNode node) throws IOException {
        startSibling(appendable, depth, prefixes, node);
        appendable.append(">\n");
        appendSiblingSet(appendable, depth + 1, prefixes, node);
        endSibling(appendable, depth, prefixes, node);
    }

    private static void appendContentMatch(final Appendable appendable, final int depth, final Prefixes prefixes,
            final ContentMatchNode node) throws IOException {
        startSibling(appendable, depth, prefixes, node);
        appendIndent(appendable.append(">\n"), depth + 1);
        appendable.append(XmlEscapers.xmlContentEscaper().escape(node.value())).append('\n');
        endSibling(appendable, depth, prefixes, node);
    }

    private static void appendSelection(final Appendable appendable, final int depth, final Prefixes prefixes,
            final SelectionNode node) throws IOException {
        startSibling(appendable, depth, prefixes, node);

        for (var am : node.attributeMatches()) {
            final var selection = am.selection();
            appendPrefix(appendable.append(' '), prefixes, selection.namespace());
            appendAttributeData(appendable.append(selection.name()).append('='), am.value());
        }

        appendable.append("/>\n");
    }

    private static void appendSiblingSet(final Appendable appendable, final int depth, final Prefixes prefixes,
            final SiblingSet siblings) throws IOException {
        for (var contentMatch : siblings.contentMatches()) {
            appendContentMatch(appendable, depth, prefixes, contentMatch);
        }
        for (var selection : siblings.selections()) {
            appendSelection(appendable, depth, prefixes, selection);
        }
        for (var containment : siblings.containments()) {
            appendContainment(appendable, depth, prefixes, containment);
        }
    }

    private static void startSibling(final Appendable appendable, final int depth, final Prefixes prefixes,
            final Sibling node) throws IOException {
        appendIndent(appendable, depth);
        appendNamespaceSelection(appendable.append('<'), prefixes, node);
        // adding namespaces and prefixes at the top/first filter node
        if (depth == 1) {
            final var prefixesMap = prefixes.getNamespaceToPrefixMap();
            if (!prefixesMap.isEmpty()) {
                final var prefixesIt = prefixesMap.entrySet().iterator();
                // this never throws error because map not empty
                var entry = prefixesIt.next();
                // add first namespaces and prefix
                appendAttributeData(appendable.append(" xmlns:").append(entry.getValue()).append('='), entry.getKey());
                // add other namespaces if there are some with indentation
                while (prefixesIt.hasNext()) {
                    entry = prefixesIt.next();
                    appendIndent(appendable.append('\n'), depth);
                    appendAttributeData(appendable.append(" xmlns:").append(entry.getValue()).append('='),
                        entry.getKey());
                }
            }
        }
    }

    private static void endSibling(final Appendable appendable, final int depth, final Prefixes prefixes,
            final Sibling node) throws IOException {
        appendIndent(appendable, depth);
        appendNamespaceSelection(appendable.append("</"), prefixes, node);
        appendable.append(">\n");
    }

    private static void appendNamespaceSelection(final Appendable appendable, final Prefixes prefixes,
            final Sibling node) throws IOException {
        switch (node.selection()) {
            case NamespaceSelection.Exact(var namespace, var name) -> {
                appendPrefix(appendable, prefixes, namespace);
                appendable.append(name);
            }
            case NamespaceSelection.Wildcard(var name) -> appendable.append(name);
        }
    }

    private static void appendPrefix(final Appendable appendable, final Prefixes prefixes, final String namespace)
            throws IOException {
        appendable.append(prefixes.lookUpPrefix(namespace)).append(':');
    }

    private static void appendAttributeData(final Appendable appendable, final String str) throws IOException {
        // Escape special characters in string and append it
        appendable.append('"').append(XmlEscapers.xmlAttributeEscaper().escape(str)).append('"');
    }
}
