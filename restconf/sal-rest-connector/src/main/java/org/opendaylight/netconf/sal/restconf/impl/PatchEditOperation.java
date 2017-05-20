/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.restconf.impl;

/**
 * Each YANG patch edit specifies one edit operation on the target data
 * node.  The set of operations is aligned with the NETCONF edit
 * operations, but also includes some new operations.
 *
 */
public enum PatchEditOperation {
    CREATE(true),  // post
    DELETE(false), // delete
    INSERT(true),  // post
    MERGE(true),
    MOVE(false),   // delete+post
    REPLACE(true), // put
    REMOVE(false); // delete

    private final boolean withValue;

    PatchEditOperation(final boolean withValue) {
        this.withValue = withValue;
    }

    /**
     * Not all patch operations support value node. Check if operation requires value or not.
     *
     * @return true if operation requires value, false otherwise
     */
    public boolean isWithValue() {
        return withValue;
    }
}
