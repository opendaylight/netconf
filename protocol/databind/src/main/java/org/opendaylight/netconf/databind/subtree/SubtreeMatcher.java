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
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;

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
        // to identify data name and namespace
        final var dataIdentifier = data.name();

        // to select which data to show
        final var top = filter.containments();

        // to creat a result
        final var result = ImmutableNodes.newContainerBuilder();

        // iterate over top (all containments in filter) --> we can have multiple subtrees filtered

        // ** first containment
        // apply selection nodes == empty XML tags --> add the whole subtree to result
        // apply content match nodes == XML tags with element or attribute values --> use all siblings of element which pass validation
        // --> its like list selected by name --> show all its elements
        // --> when there are additional selection nodes - show only selected elements!

        // |RECURSIVE?| if ANY match - continue down to its containments
        // apply selection nodes ...
        // apply content match nodes ...
        // |STOP| if NO match stop procedure

        // ** second containment
        // the same

        // ** N containment
        // the same


        // what to do with result - this logic belongs to somewhere else - from where we will examine filter in smaller pieces
        return false;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("filter", filter.prettyTree())
            .add("data", data.prettyTree())
            .toString();
    }
}
