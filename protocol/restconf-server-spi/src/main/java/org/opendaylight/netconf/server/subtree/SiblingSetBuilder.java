/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.subtree;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.server.api.DatabindContext;
import org.opendaylight.yangtools.concepts.Mutable;

public abstract sealed class SiblingSetBuilder<T extends SiblingSet> implements Mutable
        permits ContainmentNodeBuilder, SubtreeFilterBuilder {
    private final ArrayList<Sibling> siblings = new ArrayList<>();
    final @NonNull DatabindContext databind;

    SiblingSetBuilder(final DatabindContext databind) {
        this.databind = requireNonNull(databind);
    }

    @NonNullByDefault
    public abstract T build();

    @NonNullByDefault
    final <S extends Sibling> List<S> siblings(final Class<S> filter) {
        return siblings.stream()
            .filter(filter::isInstance).map(filter::cast)
            .distinct()
            .sorted((o1, o2) -> {
                final int cmp = o1.name().compareTo(o2.name());
                if (cmp != 0) {
                    return cmp;
                }

                // siblings without a namespace come first
                final var ns1 = o1.namespace();
                final var ns2 = o2.namespace();
                if (ns1 == null) {
                    return ns2 == null ? 0 : -1;
                }
                return ns2 == null ? 1 : ns1.compareTo(ns2);
            })
            .collect(Collectors.toUnmodifiableList());
    }
}
