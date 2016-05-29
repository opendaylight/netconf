/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.restful.utils;

import java.net.URI;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;
import org.apache.commons.lang3.builder.Builder;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

final class ResponseFactory extends FutureDataFactory<Void> implements Builder<Response> {

    private final NormalizedNode<?, ?> readData;
    private final URI location;

    ResponseFactory(final NormalizedNode<?, ?> readData) {
        this.readData = readData;
        this.location = null;
    }

    ResponseFactory(final NormalizedNode<?, ?> readData, final URI location) {
        this.readData = readData;
        this.location = location;
    }

    @Override
    public Response build() {
        final Status status = this.readData != null ? Status.OK : Status.CREATED;
        final ResponseBuilder responseBuilder = Response.status(status);
        if (this.location != null) {
            responseBuilder.location(this.location);
        }
        return responseBuilder.build();
    }
}
