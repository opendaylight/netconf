/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.databind.subtree;

import static java.util.Objects.requireNonNull;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.TreeSet;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.netconf.databind.subtree.NamespaceSelection.Exact;

public final class Prefixes {
    private final LinkedHashMap<String, String> namespaceToPrefixMap;

    private Prefixes(@NonNull final LinkedHashMap<String, String> namespaceToPrefix) {
        this.namespaceToPrefixMap = requireNonNull(namespaceToPrefix);
    }

    public static Prefixes of(final SubtreeFilter filter) {
        final var namespaces = new TreeSet<String>();
        // traversing the filter to fill namespaces set
        createPrefixes(filter.siblings(), namespaces);

        // LinkedHashMap to preserve the order - it increases readability when dealing with filter PrettyTree
        final var prefixes = new LinkedHashMap<String, String>(namespaces.size());
        int counter = 0;
        for (var ns : namespaces) {
            var prefix = prefixOf(counter);
            // Skipping "xml" or "xmlns", as those are reserved
            if (prefix.equals("xml") || prefix.equals("xmlns")) {
                counter++;
                prefix = prefixOf(counter);
            }
            prefixes.put(ns, prefix);
            counter++;
        }

        return new Prefixes(prefixes);
    }

    private static void createPrefixes(final Collection<Sibling> siblings, final TreeSet<String> namespaces) {
        for (final var sibling : siblings) {
            if (sibling.selection() instanceof Exact exact) {
                namespaces.add(exact.identifier().getNodeType().getNamespace().toString());
                if (sibling instanceof ContainmentNode containment) {
                    createPrefixes(containment.siblings(), namespaces);
                }
            }
        }
    }

    // Used for prefix assignment by following pattern: a, b, c, d, ..., y, z, aa, ab, ..., zz, aaa, aab, ...
    static String prefixOf(final int index) {
        final var prefix = index < 0 ? "" : prefixOf((index / 26) - 1) + (char)(65 + index % 26);
        return prefix.toLowerCase(Locale.ROOT);
    }

    @NonNullByDefault
    public String lookUpPrefix(final String namespace) {
        final var prefix = namespaceToPrefixMap.get(requireNonNull(namespace));
        if (prefix == null) {
            throw new IllegalStateException("No prefix assigned to namespace " + namespace);
        }
        return prefix;
    }

    LinkedHashMap<String, String> getNamespaceToPrefixMap() {
        return namespaceToPrefixMap;
    }
}
