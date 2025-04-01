/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.databind.subtree;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.common.UnresolvedQName.Unqualified;

/**
 * Abstract base class for {@link ContainmentNode.Builder} and {@link ContainmentNode.Builder}.
 *
 * @param <T> result type
 */
@NonNullByDefault
abstract sealed class SiblingSetBuilder permits ContainmentNode.Builder, SubtreeFilter.Builder {
    // Utility for comparing NamespaceSelections
    private record NamespaceName(Unqualified name, @Nullable QNameModule namespace)
        implements Comparable<NamespaceName> {
        NamespaceName {
            requireNonNull(name);
        }

        static NamespaceName of(final Sibling sibling) {
            return switch (sibling.selection()) {
                case NamespaceSelection.Exact(var qname) -> new NamespaceName(qname.unbind(),
                    qname.getModule());
                case NamespaceSelection.Wildcard w -> new NamespaceName(w.name(), null);
            };
        }

        @Override
        public int compareTo(final NamespaceName other) {
            final var cmp = name.compareTo(other.name);
            if (cmp != 0) {
                return cmp;
            }

            // siblings without a namespace come first
            final var tns = namespace;
            final var ons = other.namespace;
            if (tns == null) {
                return ons == null ? 0 : -1;
            }
            return ons == null ? 1 : tns.compareTo(ons);
        }
    }

    private static final Comparator<Sibling> COMPARATOR = Comparator.comparing(NamespaceName::of);

    private final ArrayList<Sibling> siblings = new ArrayList<>();

    final void addSibling(final Sibling sibling) {
        siblings.add(requireNonNull(sibling));
    }

    final <S extends Sibling> List<S> siblings(final Class<S> filter) {
        return siblings.stream()
            .filter(filter::isInstance).map(filter::cast)
            .distinct()
            .sorted(COMPARATOR)
            .collect(Collectors.toUnmodifiableList());
    }
}
