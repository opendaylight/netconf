/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.subtree;

import java.util.Collection;
import java.util.List;
import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * A container of {@link Sibling}s, representing a single level in a {@link SubtreeFilter} -- be it the filter itself,
 * or a {@link ContainmentNode}.
 */
@NonNullByDefault
public sealed interface SiblingSet permits ContainmentNode, SubtreeFilter {
    /**
     * Returns the content match of this sibling set. Content is matched IFF all {@link ContentMatchNode}s match.
     *
     * @return the content match of this sibling set
     */
    List<ContentMatchNode> contentMatches();

    /**
     * Returns the containment nodes of this sibling set.
     *
     * @return the containment nodes of this sibling set.
     */
    List<ContainmentNode> containments();

    /**
     * Returns the selection nodes of this sibling set.
     *
     * @return the selection nodes of this sibling set.
     */
    List<SelectionNode> selections();

    /**
     * Returns a collection of all siblings.
     *
     * @return a collection of all siblings
     */
    default Collection<Sibling> siblings() {
        return new SiblingCollection(this);
    }

//    @NonNull Map<QName, Object> getAnnotations();
//
//    /**
//     * Returns child nodes. Default implementation returns an empty immutable map.
//     *
//     * @return Child metadata nodes.
//     */
//    default @NonNull Map<PathArgument, NormalizedMetadata> getChildren() {
}
