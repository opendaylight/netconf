/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.restconf.server.api.ServerResponse.Failure;
import org.opendaylight.restconf.server.api.ServerResponse.Success;

/**
 * A future {@link ServerResponse}. This is equivalent to a {@link CompletionStage} except it cannot fail for any
 * reason -- it will always complete with a {@link ServerResponse}.
 *
 * <p>
 * This interface is the consumer-side view of asynchronous execution completing a {@link ServerResponsePromise}.
 *
 * <p>
 * Naming chosen here follows Netty/Scala convention: a {@code Future} is something that can be observed to complete,
 * whereas a {@code Promise} is something a provider can complete.
 *
 * @param <T> success type
 */
public sealed interface ServerResponseFuture<T extends Success> permits ImmediateServerResponse, ServerResponsePromise {
    /**
     * Completion callback. Only one of the provided methods is invoked upon completion.
     *
     * @param <T> success type
     */
    interface OnComplete<T extends Success> {
        /**
         * Invoked when {@link ServerResponseFuture} results in a {@link Success}.
         *
         * @param success a {@link Success}
         */
        void onSuccess(@NonNull T success);

        /**
         * Invoked when {@link ServerResponseFuture} results in a {@link Failure}.
         *
         * @param success a {@link Failure}
         */
        void onFailure(@NonNull Failure failure);
    }

    static <T extends Success> @NonNull ServerResponseFuture<T> of(final @NonNull T success) {
        return new ImmediateServerResponse<>(success);
    }

    static <T extends Success> @NonNull ServerResponseFuture<T> of(final @NonNull Failure failure) {
        return new ImmediateServerResponse<>(failure);
    }

    @NonNull ServerResponseFuture<T> onComplete(OnComplete<? super T> onComplete, Executor executor);
}
