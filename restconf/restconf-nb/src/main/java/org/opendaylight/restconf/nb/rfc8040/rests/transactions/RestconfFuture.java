/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.transactions;

import com.google.common.base.Throwables;
import com.google.common.util.concurrent.AbstractFuture;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.ExecutionException;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.restconf.server.api.ServerException;

/**
 * A {@link ListenableFuture} specialization, which fails only with {@link ServerException} and does not produce
 * {@code null} values.
 *
 * @param <V> resulting value type
 */
sealed class RestconfFuture<V> extends AbstractFuture<@NonNull V> permits SettableRestconfFuture {
    RestconfFuture() {
        // Hidden on purpose
    }

    @Override
    public final boolean cancel(final boolean mayInterruptIfRunning) {
        return false;
    }

    /**
     * Get the result.
     *
     * @return The result
     * @throws ServerException if this future failed or this call is interrupted
     */
    final @NonNull V getOrThrow() throws ServerException {
        try {
            return get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServerException("Interrupted while waiting", e);
        } catch (ExecutionException e) {
            Throwables.throwIfInstanceOf(e.getCause(), ServerException.class);
            throw new ServerException("Operation failed", e);
        }
    }
}
