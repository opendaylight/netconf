/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.restconf.impl;

import com.google.common.util.concurrent.FluentFuture;
import javax.ws.rs.core.Response.Status;
import org.opendaylight.mdsal.common.api.CommitInfo;

/**
 * Wrapper for status and future of PUT operation.
 */
public class PutResult {
    private final Status status;
    private final FluentFuture<? extends CommitInfo> future;

    /**
     * Wrap status and future by constructor - make this immutable.
     *
     * @param status
     *            status of operations
     * @param future
     *            result of submit of PUT operation
     */
    public PutResult(final Status status, final FluentFuture<? extends CommitInfo> future) {
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
     * @return {@link FluentFuture} result
     */
    public FluentFuture<? extends CommitInfo> getFutureOfPutData() {
        return this.future;
    }
}
