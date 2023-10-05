/*
 * Copyright (c) 2017 Pantheon Technologies, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.transactions;

import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.common.api.ReadFailedException;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadOperations;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

final class ExistenceCheck {
    sealed interface Result {
        // Nothing else
    }

    record Conflict(@NonNull YangInstanceIdentifier path, @Nullable ReadFailedException cause) implements Result {
        Conflict {
            requireNonNull(path);
        }
    }

    // Hidden on purpose:
    // - users care only about conflicts
    // - we could use null, but that is ugly and conflicts with RestconfException, which we want to use
    private static final class Success implements Result {
        static final @NonNull Success INSTANCE = new Success();
    }

    private static final AtomicIntegerFieldUpdater<ExistenceCheck> UPDATER =
        AtomicIntegerFieldUpdater.newUpdater(ExistenceCheck.class, "outstanding");

    private final SettableFuture<@NonNull Result> future = SettableFuture.create();

    @SuppressWarnings("unused")
    private volatile int outstanding;

    private ExistenceCheck(final int total) {
        outstanding = total;
    }

    static ListenableFuture<@NonNull Result> start(final DOMDataTreeReadOperations tx,
            final LogicalDatastoreType datastore, final YangInstanceIdentifier parentPath, final boolean expected,
            final Collection<? extends NormalizedNode> children) {
        final var ret = new ExistenceCheck(children.size());
        for (var child : children) {
            final var path = parentPath.node(child.name());
            tx.exists(datastore, path).addCallback(new FutureCallback<Boolean>() {
                @Override
                public void onSuccess(final Boolean result) {
                    ret.complete(path, expected, result);
                }

                @Override
                @SuppressFBWarnings("BC_UNCONFIRMED_CAST_OF_RETURN_VALUE")
                public void onFailure(final Throwable throwable) {
                    ret.complete(path, ReadFailedException.MAPPER.apply(
                        throwable instanceof Exception ex ? ex : new ExecutionException(throwable)));
                }
            }, MoreExecutors.directExecutor());
        }
        return ret.future;
    }

    private void complete(final YangInstanceIdentifier path, final boolean expected, final boolean present) {
        final int count = UPDATER.decrementAndGet(this);
        if (expected != present) {
            future.set(new Conflict(path, null));
        } else if (count == 0) {
            future.set(Success.INSTANCE);
        }
    }

    private void complete(final YangInstanceIdentifier path, final ReadFailedException cause) {
        UPDATER.decrementAndGet(this);
        future.set(new Conflict(path, cause));
    }
}
