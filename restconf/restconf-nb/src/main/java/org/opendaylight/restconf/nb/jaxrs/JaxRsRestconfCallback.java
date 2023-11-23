/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.jaxrs;

import static java.util.Objects.requireNonNull;

import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.core.Response;
import org.opendaylight.restconf.common.errors.RestconfCallback;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;

/**
 * A {@link RestconfCallback} completing an {@link AsyncResponse}.
 *
 * @param <V> value type
 */
abstract class JaxRsRestconfCallback<V> extends RestconfCallback<V> {
    private final AsyncResponse ar;

    JaxRsRestconfCallback(final AsyncResponse ar) {
        this.ar = requireNonNull(ar);
    }

    @Override
    public final void onSuccess(final V result) {
        final Response response;
        try {
            response = transform(result);
        } catch (RestconfDocumentedException e) {
            onFailure(e);
            return;
        }
        ar.resume(response);
    }

    @Override
    protected final void onFailure(final RestconfDocumentedException failure) {
        ar.resume(failure);
    }

    abstract Response transform(V result) throws RestconfDocumentedException;
}
