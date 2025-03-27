/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.databind.subtree;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.yangtools.concepts.Immutable;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;

/**
 * A matcher of a {@link SubtreeFilter} with a {@link ContainerNode}.
 */
@NonNullByDefault
public final class SubtreeMatcher implements Immutable {
    private final SubtreeFilter filter;
    private final ContainerNode data;

    SubtreeMatcher(final SubtreeFilter filter, final ContainerNode data) {
        this.filter = requireNonNull(filter);
        this.data = requireNonNull(data);
    }

    public boolean matches() {
        // Check containment nodes recursively
        for (final var containment : filter.containments()) {
            if (child == null || !matchContainment(containment, child)) {
                return false;
            }
        }

        // Check content matches at the top level
        for (final var contentMatch : filter.contentMatches()) {
            if (child == null || !matchContent(contentMatch, child)) {
                return false;
            }
        }


        // Check selection nodes (which may enforce attribute matches)
        for (final var selection : filter.selections()) {
            if (child == null || !matchSelection(selection, child)) {
                return false;
            }
        }

        return true;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("filter", filter.prettyTree())
            .add("data", data.prettyTree())
            .toString();
    }
}
