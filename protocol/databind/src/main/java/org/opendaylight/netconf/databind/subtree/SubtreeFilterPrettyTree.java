/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.databind.subtree;

import static java.util.Objects.requireNonNull;

import com.google.common.xml.XmlEscapers;
import java.io.IOException;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.yangtools.concepts.PrettyTree;

/**
 * A PrettyTree representation for a SubtreeFilter.
 */
@NonNullByDefault
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
        appendable.append("<filter type=\"subtree\">").append('\n');
        appendSiblingSet(appendable, depth + 1, prefixes, filter);
        appendIndent(appendable, depth);
        return appendable.append("</filter>");
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

    private static void appendContentMatch(final Appendable appendable, final int depth, final Prefixes prefixes,
            final ContentMatchNode node) throws IOException {
        startSibling(appendable, depth, prefixes, node);
        appendable.append(">\n");
        appendIndent(appendable, depth + 1);
        appendable.append(node.qnameValueMap().values().stream().findFirst().orElseThrow().toString()).append('\n');
        endSibling(appendable, depth, prefixes, node);
    }

    private static void appendSelection(final Appendable appendable, final int depth, final Prefixes prefixes,
            final SelectionNode node) throws IOException {
        startSibling(appendable, depth, prefixes, node);
        for (var am : node.attributeMatches()) {
            final var sel = am.selection();
            appendable.append(" ");
            appendPrefix(appendable, prefixes, sel.qname().getNamespace().toString());
            appendable.append(sel.qname().getLocalName());
            appendable.append("=");
            appendAttributeData(appendable, String.valueOf(am.value()));
        }
        appendable.append("/>\n");
    }

    private static void appendContainment(final Appendable appendable, final int depth, final Prefixes prefixes,
            final ContainmentNode node) throws IOException {
        startSibling(appendable, depth, prefixes, node);
        appendable.append(">\n");
        appendSiblingSet(appendable, depth + 1, prefixes, node);
        endSibling(appendable, depth, prefixes, node);
    }

    private static void startSibling(final Appendable appendable, final int depth, final Prefixes prefixes,
            final Sibling node) throws IOException {
        appendIndent(appendable, depth);
        appendable.append('<');
        appendNamespaceSelection(appendable, prefixes, node);
        // For the first sibling (depth == 1), add namespace declarations.
        if (depth == 1) {
            for (var entry : prefixes.getNamespaceToPrefixMap().entrySet()) {
                appendable.append(" xmlns:").append(entry.getValue()).append("=\"").append(entry.getKey()).append("\"");
            }
        }
    }

    private static void endSibling(final Appendable appendable, final int depth, final Prefixes prefixes,
            final Sibling node) throws IOException {
        appendIndent(appendable, depth);
        appendable.append("</");
        appendNamespaceSelection(appendable, prefixes, node);
        appendable.append(">\n");
    }

    private static void appendNamespaceSelection(final Appendable appendable, final Prefixes prefixes,
            final Sibling node) throws IOException {
        switch (node.selection()) {
            case NamespaceSelection.Exact exact -> {
                String ns = exact.qname().getNamespace().toString();
                appendPrefix(appendable, prefixes, ns);
                appendable.append(exact.qname().getLocalName());
            }
            case NamespaceSelection.Wildcard wildcard -> {
                // For Wildcard, output only the unqualified local name.
                appendable.append(wildcard.name().getLocalName());
            }
        }
    }

    private static void appendPrefix(final Appendable appendable, final Prefixes prefixes, final String namespace)
            throws IOException {
        appendable.append(prefixes.lookUpPrefix(namespace)).append(':');
    }

    private static void appendAttributeData(final Appendable appendable, final String str) throws IOException {
        appendable.append('"').append(XmlEscapers.xmlAttributeEscaper().escape(str)).append('"');
    }
}
