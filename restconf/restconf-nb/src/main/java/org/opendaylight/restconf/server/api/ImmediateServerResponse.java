/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import static java.util.Objects.requireNonNull;

import com.google.common.base.VerifyException;
import java.util.concurrent.Executor;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.restconf.server.api.ServerResponse.Failure;
import org.opendaylight.restconf.server.api.ServerResponse.Success;

/**
 * An immediately-available {@link ServerResponse} manifested as a {@link ServerResponseFuture}.
 *
 * @param <T> success type
 */
record ImmediateServerResponse<T extends Success>(@NonNull ServerResponse response) implements ServerResponseFuture<T> {
    ImmediateServerResponse {
        requireNonNull(response);
    }

    @Override
    public ServerResponseFuture<T> onComplete(final OnComplete<? super T> onComplete, final Executor executor) {
        executor.execute(() -> onComplete(onComplete, response));
        return this;
    }

    @SuppressWarnings("unchecked")
    static <T extends Success> void onComplete(final OnComplete<? super T> onComplete, final ServerResponse response) {
        if (response instanceof Success success) {
            onComplete.onSuccess((T) success);
        } else if (response instanceof Failure failure) {
            onComplete.onFailure(failure);
        } else {
            throw new VerifyException("Unhandled " + response);
        }
    }
}
