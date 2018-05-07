/*
 * Copyright (c) 2018 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.common.util;

/**
 * Represents the scope of a data change (addition, replacement, deletion) registration.
 *
 * <p>
 * Note: this enum was originally defined in the AsynDataBroker interface but was removed when DataChangeListener
 * et al were removed.
 */
public enum DataChangeScope {
    /**
     * Represents only a direct change of the node, such as replacement of a node, addition or deletion..
     *
     */
    BASE,

    /**
     * Represent a change (addition, replacement, deletion) of the node or one of its direct
     * children.
     *
     * <p>
     * This scope is superset of {@link #BASE}.
     *
     */
    ONE,

    /**
     * Represents a change of the node or any of or any of its child nodes, direct and nested.
     *
     * <p>
     * This scope is superset of {@link #ONE} and {@link #BASE}.
     *
     */
    SUBTREE
}
