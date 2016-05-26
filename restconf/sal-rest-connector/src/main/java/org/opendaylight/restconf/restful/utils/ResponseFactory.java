/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.restful.utils;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import org.apache.commons.lang3.builder.Builder;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

final class ResponseFactory extends FutureDataFactory<Void> implements Builder<Response> {

    private final NormalizedNode<?, ?> readData;

    ResponseFactory(final NormalizedNode<?, ?> readData) {
        this.readData = readData;
    }

    @Override
    public Response build() {
        final Status status = this.readData != null ? Status.OK : Status.CREATED;
        return Response.status(status).build();
    }
}
