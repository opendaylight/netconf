/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.databind.subtree;

import static java.util.Objects.requireNonNull;

import org.apache.commons.text.StringEscapeUtils;
import org.opendaylight.yangtools.concepts.PrettyTree;

/**
 * A PrettyTree representation for a SubtreeFilter.
 */
final class SubtreeFilterPrettyTree extends PrettyTree {
    private final SubtreeFilter filter;
    private final Prefixes prefixes;

    SubtreeFilterPrettyTree(final SubtreeFilter filter) {
        this.filter = requireNonNull(filter);
        this.prefixes = Prefixes.of(filter);
    }

    @Override
    public void appendTo(final StringBuilder sb, final int depth) {
        appendIndent(sb, depth);
        sb.append("<filter type=\"subtree\">").append('\n');
        appendSiblingSet(sb, depth + 1, prefixes, filter);
        appendIndent(sb, depth);
        sb.append("</filter>");
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

    private static void appendContentMatch(final StringBuilder sb, final int depth, final Prefixes prefixes,
            final ContentMatchNode node) {
        startSibling(sb, depth, prefixes, node);
        sb.append(">\n");
        appendIndent(sb, depth + 1);
        sb.append(node.value()).append('\n');
        endSibling(sb, depth, prefixes, node);
    }

    private static void appendSelection(final StringBuilder sb, final int depth, final Prefixes prefixes,
            final SelectionNode node) {
        startSibling(sb, depth, prefixes, node);
        for (var am : node.attributeMatches()) {
            final var sel = am.selection();
            sb.append(" ");
            appendPrefix(sb, prefixes, sel.qname().getNamespace().toString());
            sb.append(sel.qname().getLocalName());
            sb.append("=");
            appendAttributeData(sb, String.valueOf(am.value()));
        }
        sb.append("/>\n");
    }

    private static void appendContainment(final StringBuilder sb, final int depth, final Prefixes prefixes,
            final ContainmentNode node) {
        startSibling(sb, depth, prefixes, node);
        sb.append(">\n");
        appendSiblingSet(sb, depth + 1, prefixes, node);
        endSibling(sb, depth, prefixes, node);
    }

    private static void startSibling(final StringBuilder sb, final int depth, final Prefixes prefixes,
            final Sibling node) {
        appendIndent(sb, depth);
        sb.append('<');
        appendNamespaceSelection(sb, prefixes, node);
        // For the first sibling (depth == 1), add namespace declarations.
        if (depth == 1) {
            for (var entry : prefixes.getNamespaceToPrefixMap().entrySet()) {
                sb.append(" xmlns:").append(entry.getValue()).append("=\"").append(entry.getKey()).append("\"");
            }
        }
    }

    private static void endSibling(final StringBuilder sb, final int depth, final Prefixes prefixes,
            final Sibling node) {
        appendIndent(sb, depth);
        sb.append("</");
        appendNamespaceSelection(sb, prefixes, node);
        sb.append(">\n");
    }

    private static void appendNamespaceSelection(final StringBuilder sb, final Prefixes prefixes, final Sibling node) {
        switch (node.selection()) {
            case NamespaceSelection.Exact exact -> {
                String ns = exact.qname().getNamespace().toString();
                appendPrefix(sb, prefixes, ns);
                sb.append(exact.qname().getLocalName());
            }
            case NamespaceSelection.Wildcard wildcard -> {
                // For Wildcard, output only the unqualified local name.
                sb.append(wildcard.name().getLocalName());
            }
        }
    }

    private static void appendPrefix(final StringBuilder sb, final Prefixes prefixes, final String namespace) {
        sb.append(prefixes.lookUpPrefix(namespace)).append(':');
    }

    private static void appendAttributeData(final StringBuilder sb, final String str) {
        sb.append('"').append(StringEscapeUtils.escapeXml10(str)).append('"');
    }
}
