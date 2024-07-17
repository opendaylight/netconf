/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import java.util.function.Function;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.api.QueryParameters;

/**
 * A request to {@link RestconfServer}. It contains state and binding established by whoever is performing binding to
 * HTTP transport layer. This includes:
 * <ul>
 *   <li>HTTP request {@link #queryParameters() query parameters},</li>
 * </ul>
 * It notably does <b>not</b> hold the HTTP request path, nor the request body. Those are passed as separate arguments
 * to server methods as implementations of those methods are expected to act on them.
 *
 * @param <T> type of reported result
 */
@NonNullByDefault
public sealed interface ServerRequest<T> permits AbstractServerRequest, TransformedServerRequest {

    QueryParameters queryParameters();

    void completeWith(T result);

    void completeWith(ServerException failure);

    default <O> ServerRequest<O> transform(final Function<O, T> function) {
        return new TransformedServerRequest<>(this, function);
    }
}
