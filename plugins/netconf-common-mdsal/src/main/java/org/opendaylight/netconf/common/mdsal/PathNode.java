/*
 * Copyright © 2020 FRINX s.r.o. All rights reserved.
 * Copyright © 2021 PANTHEON.tech, s.r.o.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.common.mdsal;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.Maps;
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

    private Map<PathArgument, PathNode> children;

    private PathNode(final PathArgument argument, final LinkedHashMap<PathArgument, PathNode> children) {
        this.argument = requireNonNull(argument);
        this.children = requireNonNull(children);
    }

    /**
     * Creation of tree node using a path argument.
     *
     * @param argument Path argument
     */
    PathNode(final PathArgument argument) {
        this.argument = requireNonNull(argument);
        children = Map.of();
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
     * Return current child nodes.
     *
     * @return Current child nodes
     */
    Collection<PathNode> children() {
        return children.values();
    }

    /**
     * Return {@code true} if this node has no child nodes.
     *
     * @return {@code true} if this node has no child nodes
     */
    boolean isEmpty() {
        return children.isEmpty();
    }

    /**
     * Create a copy of this node with specified immediate child nodes appended.
     *
     * @param childArguments Child arguments
     * @return A copy of this {@link PathNode}
     * @throws NullPointerException if {@code childArguments} is, or contains, {@code null}
     */
    PathNode copyWith(final Collection<PathArgument> childArguments) {
        final LinkedHashMap<PathArgument, PathNode> copy = children instanceof LinkedHashMap
            ? new LinkedHashMap<>(children) : Maps.newLinkedHashMapWithExpectedSize(childArguments.size());
        for (PathArgument childArgument : childArguments) {
            ensureChild(copy, childArgument);
        }
        return new PathNode(argument, copy);
    }

    /**
     * Ensure a node for specified argument exists.
     *
     * @param childArgument Child argument
     * @return A child {@link PathNode}
     * @throws NullPointerException if {@code childArgument} is null
     */
    PathNode ensureChild(final PathArgument childArgument) {
        return ensureChild(mutableChildren(), childArgument);
    }

    private static PathNode ensureChild(final LinkedHashMap<PathArgument, PathNode> children,
            final PathArgument childArgument) {
        return children.computeIfAbsent(requireNonNull(childArgument), PathNode::new);
    }

    private LinkedHashMap<PathArgument, PathNode> mutableChildren() {
        final Map<PathArgument, PathNode> local = children;
        if (local instanceof LinkedHashMap) {
            return (LinkedHashMap<PathArgument, PathNode>) local;
        }

        // TODO: LinkedHashMap is rather heavy, do we need to retain insertion order?
        final LinkedHashMap<PathArgument, PathNode> ret = new LinkedHashMap<>(4);
        children = ret;
        return ret;
    }
}
