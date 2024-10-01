/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server;

import static java.util.Objects.requireNonNull;

import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.eclipse.jdt.annotation.NonNull;

/**
 * A successful response to a HEAD request. This response implies a {@link HttpResponseStatus#OK} and only carries
 * headers.
 */
record HeadResponse(@NonNull HttpHeaders headers) implements Response {
    HeadResponse {
        requireNonNull(headers);
    }

    @Override
    public HttpResponseStatus status() {
        return HttpResponseStatus.OK;
    }
}
