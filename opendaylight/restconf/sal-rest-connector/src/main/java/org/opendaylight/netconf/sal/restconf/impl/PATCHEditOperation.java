/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.restconf.impl;

import javax.annotation.Nonnull;

/**
 *
 * Each YANG patch edit specifies one edit operation on the target data
 * node.  The set of operations is aligned with the NETCONF edit
 * operations, but also includes some new operations.
 *
 */
public enum PATCHEditOperation {
    CREATE,  //post
    DELETE,  //delete
    INSERT,  //post
    MERGE,
    MOVE,    //delete+post
    REPLACE, //put
    REMOVE;  //delete

    /**
     * Not all edit operations support value node. Check if operation requires value or not.
     * @param operation Name of the operation to be checked
     * @return true if operation requires value, false otherwise
     */
    public static boolean isPatchOperationWithValue(@Nonnull final String operation) {
        switch (PATCHEditOperation.valueOf(operation.toUpperCase())) {
            case CREATE:
            case MERGE:
            case REPLACE:
            case INSERT:
                return true;
            default:
                return false;
        }
    }
}