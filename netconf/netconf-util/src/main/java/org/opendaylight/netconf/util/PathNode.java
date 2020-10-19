/*
 * Copyright © 2020 FRINX s.r.o. All rights reserved.
 * Copyright © 2021 PANTHEON.tech, s.r.o.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.util;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableMap;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.yangtools.concepts.Mutable;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;

/**
 * Representation of the tree node with possible multiple child nodes. Child nodes are identified uniquely by path
 * argument.
 */
@NonNullByDefault
final class PathNode implements Mutable {
    private final PathArgument argument;

    private Map<PathArgument, PathNode> children = ImmutableMap.of();

    /**
     * Creation of tree node using a path argument.
     *
     * @param argument Path argument
     */
    PathNode(final PathArgument argument) {
        this.argument = requireNonNull(argument);
    }

    /**
     * Get path argument.
     *
     * @return path argument
     */
    PathArgument element() {
        return argument;
    }

    /**
     * Return current child nodes
     *
     * @return Current child nodes
     */
    Collection<PathNode> children() {
        return children.values();
    }

    /**
     * Ensure a node for specified argument exists.
     *
     * @param childArgument Child argument
     * @return A child {@link PathNode}
     * @throws NullPointerException if {@code childArgument} is null
     */
    PathNode ensureChild(final PathArgument childArgument) {
        return mutableChildren().computeIfAbsent(requireNonNull(childArgument), PathNode::new);
    }

    private Map<PathArgument, PathNode> mutableChildren() {
        if (children instanceof ImmutableMap) {
            // TODO: LinkedHashMap is rather heavy
            //       - do we need to retain insertion order?
            //       - can we use a different structure, perhaps trading off search speed for memory by using
            //         an ArrayList instead?
            children = new LinkedHashMap<>(4);
        }
        return children;
    }
}
