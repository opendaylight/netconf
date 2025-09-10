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
import org.opendaylight.netconf.databind.RequestException;
import org.opendaylight.restconf.api.FormattableBody;
import org.opendaylight.restconf.api.QueryParameters;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.QName;

/**
 * A {@link ServerRequest} transformed through a {@link Function}.
 *
 * @param <I> input result type
 * @param <R> output result type
 */
@NonNullByDefault
record TransformedServerRequest<I, R>(ServerRequest<R> delegate, Function<I, R> function) implements ServerRequest<I> {
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
    public @Nullable QName contentEncoding() {
        return delegate.contentEncoding();
    }

    @Override
    public void completeWith(final I result) {
        delegate.completeWith(function.apply(requireNonNull(result)));
    }

    @Override
    public void completeWith(final RequestException failure) {
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
