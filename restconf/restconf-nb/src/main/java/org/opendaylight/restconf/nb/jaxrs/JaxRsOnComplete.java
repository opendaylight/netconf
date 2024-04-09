/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.jaxrs;

import static java.util.Objects.requireNonNull;

import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.core.Response;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.restconf.server.api.ServerResponse.Failure;
import org.opendaylight.restconf.server.api.ServerResponse.Success;
import org.opendaylight.restconf.server.api.ServerResponseFuture.OnComplete;
import org.opendaylight.restconf.server.spi.ServerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An {@link OnComplete} forwarding the result to an {@link AsyncResponse}.
 */
abstract class JaxRsOnComplete<T extends Success> implements OnComplete<T> {
    private static final Logger LOG = LoggerFactory.getLogger(JaxRsOnComplete.class);

    private final AsyncResponse ar;

    JaxRsOnComplete(final AsyncResponse ar) {
        this.ar = requireNonNull(ar);
    }

    @Override
    public final void onSuccess(final T success) {
        ar.resume(toResponse(success));
    }

    @Override
    public final void onFailure(final Failure failure) {
        ar.resume(toResponse(failure));
    }

    /**
     * Transform a successful response into a JAX-RS {@link Response}.
     *
     * @param success a server success
     * @return A {@link Response}
     * @throws ServerException if an error occurs
     */
    abstract @NonNull Response transform(@NonNull T success) throws ServerException;

    private @NonNull Response toResponse(final @NonNull T success) {
        try {
            return transform(success);
        } catch (ServerException e) {
            LOG.debug("Failed to transform {}", success, e);
            return toResponse(e.error());
        }
    }

    @NonNullByDefault
    private static Response toResponse(final RestconfError error) {
        return toResponse(new Failure(error.getErrorTag(), null));
    }

    @NonNullByDefault
    private static Response toResponse(final Failure failure) {
        throw new UnsupportedOperationException();
    }
}
