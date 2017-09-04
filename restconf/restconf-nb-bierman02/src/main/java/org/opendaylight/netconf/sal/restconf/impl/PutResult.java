/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.restconf.impl;

import com.google.common.util.concurrent.CheckedFuture;
import javax.ws.rs.core.Response.Status;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;

/**
 * Wrapper for status and future of PUT operation.
 *
 */
public class PutResult {
    private final Status status;
    private final CheckedFuture<Void, TransactionCommitFailedException> future;

    /**
     * Wrap status and future by constructor - make this immutable.
     *
     * @param status
     *            status of operations
     * @param future
     *            result of submit of PUT operation
     */
    public PutResult(final Status status, final CheckedFuture<Void, TransactionCommitFailedException> future) {
        this.status = status;
        this.future = future;
    }

    /**
     * Get status.
     *
     * @return {@link Status} result
     */
    public Status getStatus() {
        return this.status;
    }

    /**
     * Get future.
     *
     * @return {@link CheckedFuture} result
     */
    public CheckedFuture<Void, TransactionCommitFailedException> getFutureOfPutData() {
        return this.future;
    }
}
