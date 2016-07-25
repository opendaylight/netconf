/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.restconf.impl;

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
    REMOVE   //delete
}
