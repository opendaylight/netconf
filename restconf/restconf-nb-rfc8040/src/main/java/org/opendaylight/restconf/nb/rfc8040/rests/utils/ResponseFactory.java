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
import org.apache.commons.lang3.builder.Builder;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

final class ResponseFactory extends FutureDataFactory<Void> implements Builder<Response> {

    private ResponseBuilder responseBuilder;

    ResponseFactory(final NormalizedNode<?, ?> readData) {
        final Status status = prepareStatus(readData);
        this.responseBuilder = Response.status(status);
    }

    ResponseFactory(final NormalizedNode<?, ?> readData, final URI location) {
        final Status status = prepareStatus(readData);
        this.responseBuilder = Response.status(status);
        this.responseBuilder.location(location);
    }

    ResponseFactory() {
        this.responseBuilder = Response.status(Status.OK);
    }

    @Override
    public Response build() {
        if (getFailureStatus()) {
            responseBuilder = responseBuilder.status(Response.Status.INTERNAL_SERVER_ERROR);
        }
        return this.responseBuilder.build();
    }

    private static Status prepareStatus(final NormalizedNode<?, ?> readData) {
        return readData != null ? Status.OK : Status.CREATED;
    }
}
