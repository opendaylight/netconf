/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.api;

import static java.util.Objects.requireNonNull;

import com.google.common.base.VerifyException;
import com.google.common.util.concurrent.AbstractFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.eclipse.jdt.annotation.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link ListenableFuture} specialization, which cannot fail. It always produces a {@link RestconfResponse}.
 *
 * @param <V> resulting value type
 */
public sealed class RequestFuture extends AbstractFuture<@NonNull RestconfResponse>
        permits SettableRequestFuture {
    /**
     * A callback for use with {@link RequestFuture} to get notified when it completes.
     */
    @FunctionalInterface
    public interface Listener {
        /**
         * Invoked when a {@link RestconfResponse} is available.
         *
         * @param result The {@link RestconfResponse}
         */
        void run(@NonNull RestconfResponse result);
    }

    private static final Logger LOG = LoggerFactory.getLogger(RequestFuture.class);

    RequestFuture() {
        // Hidden on purpose
    }

    public static final RequestFuture of(final RestconfResponse value) {
        final var future = new RequestFuture();
        future.set(requireNonNull(value));
        return future;
    }

    @Override
    public final boolean cancel(final boolean mayInterruptIfRunning) {
        return false;
    }

    @Override
    public final RestconfResponse get() throws InterruptedException {
        try {
            return super.get();
        } catch (ExecutionException e) {
            // This should never happen
            throw new VerifyException("Unexpected failure", e);
        }
    }

    @Override
    public final RestconfResponse get(final long timeout, final TimeUnit unit)
            throws InterruptedException, TimeoutException {
        try {
            return super.get(timeout, unit);
        } catch (ExecutionException e) {
            // This should never happen
            throw new VerifyException("Unexpected failure", e);
        }
    }

    public final void addListener(final Listener listener) {
        addListener(listener, MoreExecutors.directExecutor());
    }

    public final void addListener(final Listener listener, final Executor executor) {
        final var delegate = requireNonNull(listener);
        addListener(() -> {
            final RestconfResponse result;
            try {
                result = Futures.getDone(this);
            } catch (ExecutionException e) {
                LOG.warn("Unexpected failure, {} will not be notified", delegate, e);
                return;
            }
            delegate.run(result);
        }, executor);
    }
}
