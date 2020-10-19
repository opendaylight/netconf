/*
 * Copyright © 2020 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.util;

import java.util.LinkedHashMap;

/**
 * Representation of the tree node with possible multiple children tree nodes. Children nodes are identified uniquely
 * by node elements.
 *
 * @param <T> type of the node element
 */
final class TreeNode<T> extends LinkedHashMap<T, TreeNode<T>> {
    private static final long serialVersionUID = 1L;
    private final T element;

    /**
     * Creation of tree node using element.
     *
     * @param element node element
     */
    TreeNode(final T element) {
        this.element = element;
    }

    /**
     * Get node element.
     *
     * @return element
     */
    T getElement() {
        return element;
    }
}