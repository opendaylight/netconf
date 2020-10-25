/*
 * Copyright © 2020 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.util;

import com.google.common.collect.ImmutableList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Representation of the tree node with possible multiple children tree nodes. Children nodes are identified uniquely
 * by node elements.
 *
 * @param <T> type of the node element
 */
public final class TreeNode<T> {
    private final T element;
    private final Map<T, TreeNode<T>> childrenNodes = new LinkedHashMap<>();

    /**
     * Creation of tree node using element.
     *
     * @param element node element
     */
    public TreeNode(final T element) {
        this.element = element;
    }

    /**
     * Creation of tree using existing {@link TreeNode}. List of children nods is copied to a new node.
     *
     * @param treeNode existing tree node
     */
    public TreeNode(final TreeNode<T> treeNode) {
        this.element = treeNode.element;
        this.childrenNodes.putAll(treeNode.childrenNodes);
    }

    /**
     * Get node element.
     *
     * @return element
     */
    public T getElement() {
        return element;
    }

    /**
     * Get immutable list of children nodes. Each child is placed only once in the output list.
     *
     * @return {@link List} of children nodes
     */
    public List<TreeNode<T>> getChildrenNodes() {
        return ImmutableList.copyOf(childrenNodes.values());
    }

    /**
     * Check if this tree node has some children nodes.
     *
     * @return {@code true}, if there are some children nodes.
     */
    public boolean hasChildrenNodes() {
        return !childrenNodes.isEmpty();
    }

    /**
     * Add single child tree node. Child tree node with the same element will be replaced.
     *
     * @param child tree node
     */
    public void addChildNode(final TreeNode<T> child) {
        childrenNodes.put(child.getElement(), child);
    }

    /**
     * Get an existing tree node identified by provided element or create a new child tree node using provided function.
     *
     * @param childElement     child element
     * @param treeNodeFunction function used for creation of new child tree node
     * @return existing or created child tree node
     */
    public TreeNode<T> computeIfAbsent(final T childElement, final Function<T, TreeNode<T>> treeNodeFunction) {
        return childrenNodes.computeIfAbsent(childElement, treeNodeFunction);
    }
}