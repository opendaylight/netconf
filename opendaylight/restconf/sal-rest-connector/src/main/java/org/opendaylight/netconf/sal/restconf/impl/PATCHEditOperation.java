/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.restconf.impl;

import javax.annotation.Nonnull;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;

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
     * Not all patch operations support value node. Check if operation requires value or not.
     * @param operation Name of the operation to be checked
     * @return true if operation requires value, false otherwise
     */
    public static final boolean isPatchOperationWithValue(@Nonnull final String operation) {
        switch (PATCHEditOperation.valueOf(operation.toUpperCase())) {
            case CREATE:
                // fall through
            case MERGE:
                // fall through
            case REPLACE:
                // fall through
            case INSERT:
                return true;
            default:
                return false;
        }
    }

    /**
     * Move parent node depending on operation.
     * @param target Original target node
     * @param operation Name of the operation
     * @return {@link YangInstanceIdentifier} moved depending on patch operation
     */
    public static final YangInstanceIdentifier moveTargetNode(@Nonnull final YangInstanceIdentifier target,
                                                              @Nonnull final String operation) {
        switch (PATCHEditOperation.valueOf(operation.toUpperCase())) {
            case REPLACE:
                // fall through
            case DELETE:
                return target.getParent();
            default:
                return target;
        }
    }
}