/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import static java.util.Objects.requireNonNull;

import java.security.Principal;
import java.util.UUID;
import java.util.function.Function;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.api.FormattableBody;
import org.opendaylight.restconf.api.QueryParameters;
import org.opendaylight.yangtools.yang.common.ErrorTag;

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
    public UUID uuid() {
        return delegate.uuid();
    }

    @Override
    public @Nullable Principal principal() {
        return delegate.principal();
    }

    @Override
    public @Nullable TransportSession session() {
        return delegate.session();
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

    @Override
    public void completeWith(final YangErrorsBody errors) {
        delegate.completeWith(errors);
    }

    @Override
    public void completeWith(final ErrorTag errorTag,  final FormattableBody body) {
        delegate.completeWith(errorTag, body);
    }
}
