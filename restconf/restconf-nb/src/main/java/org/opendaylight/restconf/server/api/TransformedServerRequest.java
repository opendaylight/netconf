/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import static java.util.Objects.requireNonNull;

import java.util.function.Function;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.api.QueryParameters;

/**
 * A {@link ServerRequest} transformed through a {@link Function}.
 *
 * @param <T> type of reported result
 */
@NonNullByDefault
record TransformedServerRequest<O, T>(ServerRequest<T> delegate, Function<O, T> function) implements ServerRequest<O> {
    TransformedServerRequest {
        requireNonNull(delegate);
        requireNonNull(function);
    }

    @Override
    public QueryParameters queryParameters() {
        return delegate.queryParameters();
    }

    @Override
    public void completeWith(final O result) {
        delegate.completeWith(function.apply(result));
    }

    @Override
    public void completeWith(final ServerException failure) {
        delegate.completeWith(failure);
    }
}
