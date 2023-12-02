/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.common.errors;

import static java.util.Objects.requireNonNull;

import com.google.common.base.Throwables;
import com.google.common.util.concurrent.AbstractFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import org.eclipse.jdt.annotation.NonNull;

/**
 * A {@link ListenableFuture} specialization, which fails only with {@link RestconfDocumentedException} and does not
 * produce {@code null} values.
 *
 * @param <V> resulting value type
 */
public sealed class RestconfFuture<V> extends AbstractFuture<@NonNull V> permits SettableRestconfFuture {
    RestconfFuture() {
        // Hidden on purpose
    }

    public static <V> @NonNull RestconfFuture<V> of(final V value) {
        final var future = new RestconfFuture<V>();
        future.set(requireNonNull(value));
        return future;
    }

    public static <V> @NonNull RestconfFuture<V> failed(final RestconfDocumentedException cause) {
        final var future = new RestconfFuture<V>();
        future.setException(requireNonNull(cause));
        return future;
    }

    @Override
    public final boolean cancel(final boolean mayInterruptIfRunning) {
        return false;
    }

    /**
     * Add a callback to invoke when this future completes, or immediately if it is already complete.
     *
     * @param callback Callback to invoke
     * @return This future
     * @throws NullPointerException if {@code callback} is {@code null}
     */
    public final @NonNull RestconfFuture<V> addCallback(final RestconfCallback<? super V> callback) {
        Futures.addCallback(this, callback, MoreExecutors.directExecutor());
        return this;
    }

    /**
     * Transform the result of this future using the specified function.
     *
     * @param <T> Resulting type
     * @param function Function to apply
     * @return Transformed future
     * @throws NullPointerException if {@code function} is {@code null}
     */
    public final <T> @NonNull RestconfFuture<T> transform(final Function<@NonNull V, @NonNull T> function) {
        final var fun = requireNonNull(function);
        final var ret = new RestconfFuture<T>();
        addCallback(new RestconfCallback<>() {
            @Override
            public void onSuccess(final V result) {
                ret.set(requireNonNull(fun.apply(result)));
            }

            @Override
            protected void onFailure(final RestconfDocumentedException failure) {
                ret.setException(failure);
            }
        });
        return ret;
    }

    /**
     * Get the result.
     *
     * @return The result
     * @throws RestconfDocumentedException if this future failed or this call is interrupted.
     */
    public final @NonNull V getOrThrow() throws RestconfDocumentedException {
        try {
            return get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RestconfDocumentedException("Interrupted while waiting", e);
        } catch (ExecutionException e) {
            Throwables.throwIfInstanceOf(e.getCause(), RestconfDocumentedException.class);
            throw new RestconfDocumentedException("Operation failed", e);
        }
    }
}
