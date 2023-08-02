/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.transactions;

import static java.util.Objects.requireNonNull;

import com.google.common.base.Throwables;
import com.google.common.util.concurrent.AbstractFuture;
import java.util.concurrent.ExecutionException;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;

public final class SettableRestconfFuture<V> extends AbstractFuture<@NonNull V> implements RestconfFuture<V> {
    @Override
    public boolean cancel(final boolean mayInterruptIfRunning) {
        return false;
    }

    @Override
    public boolean set(final V value) {
        return super.set(requireNonNull(value));
    }

    public boolean setFailure(final RestconfDocumentedException cause) {
        return setException(requireNonNull(cause));
    }

    @Override
    public V getOrThrow() throws RestconfDocumentedException {
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
