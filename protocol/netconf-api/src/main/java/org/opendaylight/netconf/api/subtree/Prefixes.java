/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.api.subtree;

import static java.util.Objects.requireNonNull;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.TreeSet;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;

final class Prefixes {
    private final LinkedHashMap<String, String> namespaceToPrefixMap;

    private Prefixes(@NonNull final LinkedHashMap<String, String> namespaceToPrefix) {
        this.namespaceToPrefixMap = requireNonNull(namespaceToPrefix);
    }

    static Prefixes of(final SubtreeFilter filter) {
        final var namespaces = new TreeSet<String>();
        // traversing the filter to fill namespaces set
        createPrefixes(filter.siblings(), namespaces);

        // LinkedHashMap to preserve the order - it increases readability when dealing with filter PrettyTree
        final var prefixes = new LinkedHashMap<String, String>(namespaces.size());
        int counter = 0;
        for (var ns : namespaces) {
            prefixes.put(ns, generatePrefix(counter));
            counter++;
        }

        return new Prefixes(prefixes);
    }

    private static void createPrefixes(final Collection<Sibling> siblings, final TreeSet<String> namespaces) {
        for (final var sibling : siblings) {
            if (sibling.selection() instanceof NamespaceSelection.Exact exact) {
                namespaces.add(exact.namespace());
                if (sibling instanceof ContainmentNode containment) {
                    createPrefixes(containment.siblings(), namespaces);
                }
            }
        }
    }

    static String generatePrefix(int index) {
        var prefix = prefixOf(index);
        // Skipping "xml" or "xmlns", as those are reserved
        if (prefix.equals("xml") || prefix.equals("xmlns")) {
            index++;
            prefix = prefixOf(index);
        }
        return prefix;
    }

    // Used for prefix assignment by following pattern: a, b, c, d, ..., y, z, aa, ab, ..., zz, aaa, aab, ...
    private static String prefixOf(final int index) {
        final var prefix = index < 0 ? "" : prefixOf((index / 26) - 1) + (char)(65 + index % 26);
        return prefix.toLowerCase(Locale.ROOT);
    }

    @NonNullByDefault
    String getPrefix(final String namespace) {
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
