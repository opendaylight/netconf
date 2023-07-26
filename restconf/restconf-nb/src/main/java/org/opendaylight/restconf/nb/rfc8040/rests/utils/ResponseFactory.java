/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.utils;

import java.net.URI;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.common.api.CommitInfo;

final class ResponseFactory {
    private final ResponseBuilder responseBuilder;

    private CommitInfo result = null;
    private boolean statusFail = false;

    ResponseFactory(final Status status) {
        responseBuilder = Response.status(status);
    }

    ResponseFactory location(final URI location) {
        responseBuilder.location(location);
        return this;
    }

    void setResult(final CommitInfo result) {
        this.result = result;
    }

    void setFailureStatus() {
        statusFail = true;
    }

    public @NonNull Response build() {
        if (statusFail) {
            responseBuilder.status(Status.INTERNAL_SERVER_ERROR);
        }
        return responseBuilder.build();
    }
}
