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

public class PutResult {
    private final Status status;
    private final CheckedFuture<Void, TransactionCommitFailedException> future;

    public PutResult(final Status status,
            final CheckedFuture<Void, TransactionCommitFailedException> future) {
        this.status = status;
        this.future = future;
    }

    public Status getStatus() {
        return this.status;
    }

    public CheckedFuture<Void, TransactionCommitFailedException> getFutureOfPutData() {
        return this.future;
    }
}
