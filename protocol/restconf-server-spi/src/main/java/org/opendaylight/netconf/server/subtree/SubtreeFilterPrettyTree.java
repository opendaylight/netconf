/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.subtree;

import static java.util.Objects.requireNonNull;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;
import javax.xml.stream.XMLOutputFactory;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.yangtools.concepts.PrettyTree;
import org.opendaylight.yangtools.yang.common.XMLNamespace;

/**
 * A {@link PrettyTree} of a {@link SubtreeFilter}.
 */
final class SubtreeFilterPrettyTree extends PrettyTree {
    private static final class Prefixes {
        final Map<XMLNamespace, String> map;

        Prefixes(final Map<XMLNamespace, String> map) {
            this.map = requireNonNull(map);
        }

        @NonNullByDefault
        static Prefixes of(final SubtreeFilter filter) {
            final var namespaces = new TreeSet<XMLNamespace>();
            // FIXME: traverse the filter and fill namespaces

            // LinkedHashMap to preserve ordering
            final var map = LinkedHashMap.<XMLNamespace, String>newLinkedHashMap(namespaces.size());
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
        String getPrefix(final XMLNamespace namespace) {
            final var prefix = map.get(requireNonNull(namespace));
            if (prefix == null) {
                throw new IllegalStateException("No prefix assigned to namespace " + namespace);
            }
            return prefix;
        }
    }

    private static final XMLOutputFactory XML_FACTORY = XMLOutputFactory.newFactory();

    private final SubtreeFilter filter;

    SubtreeFilterPrettyTree(final SubtreeFilter filter) {
        this.filter = requireNonNull(filter);
    }

    @Override
    public void appendTo(final StringBuilder sb, final int depth) {
        final var prefixes = Prefixes.of(filter);

        appendIndent(sb, depth);
        sb.append("<filter type=\"subtree\"");
        for (var entry : prefixes.map.entrySet()) {
            appendIndent(sb.append('\n'), depth);
            appendQuoted(sb.append("        xmlns:").append(entry.getValue()).append('='), entry.getKey().toString());
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
    }

    private static void appendSelection(final StringBuilder sb, final int depth, final Prefixes prefixes,
            final SelectionNode node) {
        startSibling(sb, depth, prefixes, node);

        for (var am : node.attributeMatches()) {
            appendPrefix(sb.append(' '), prefixes, am.namespace());
            appendQuoted(sb.append(am.value()).append('='), am.value());
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
        appendName(sb.append('<'), prefixes, node);
    }

    private static void endSibling(final StringBuilder sb, final int depth, final Prefixes prefixes,
            final Sibling node) {
        appendIndent(sb, depth);
        appendName(sb.append("</"), prefixes, node);
        sb.append(">\n");
    }

    private static void appendName(final StringBuilder sb, final Prefixes prefixes, final Sibling node) {
        final var ns = node.namespace();
        if (ns != null) {
            appendPrefix(sb, prefixes, ns);
        }
        sb.append(node.name());
    }

    private static void appendPrefix(final StringBuilder sb, final Prefixes prefixes, final XMLNamespace namespace) {
        sb.append(prefixes.getPrefix(namespace)).append(':');
    }

    private static void appendQuoted(final StringBuilder sb, final String str) {
        // FIXME: correct XML escaping here
        sb.append('"').append(str).append('"');
    }
}
