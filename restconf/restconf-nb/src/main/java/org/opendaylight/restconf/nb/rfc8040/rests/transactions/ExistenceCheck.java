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
import com.google.common.util.concurrent.MoreExecutors;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.common.api.ReadFailedException;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadOperations;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfFuture;
import org.opendaylight.restconf.common.errors.SettableRestconfFuture;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

final class ExistenceCheck implements FutureCallback<Boolean> {
    sealed interface Result {
        // Nothing else
    }

    record Conflict(@NonNull YangInstanceIdentifier path) implements Result {
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

    private final SettableRestconfFuture<Result> future;
    private final AtomicInteger counter;
    private final @NonNull YangInstanceIdentifier parentPath;
    private final @NonNull YangInstanceIdentifier path;
    private final boolean expected;

    private ExistenceCheck(final SettableRestconfFuture<Result> future, final AtomicInteger counter,
            final YangInstanceIdentifier parentPath, final YangInstanceIdentifier path, final boolean expected) {
        this.future = requireNonNull(future);
        this.counter = requireNonNull(counter);
        this.parentPath = requireNonNull(parentPath);
        this.path = requireNonNull(path);
        this.expected = expected;
    }

    static RestconfFuture<Result> start(final DOMDataTreeReadOperations tx,
            final LogicalDatastoreType datastore, final YangInstanceIdentifier parentPath, final boolean expected,
            final Collection<? extends @NonNull NormalizedNode> children) {
        final var future = new SettableRestconfFuture<Result>();
        final var counter = new AtomicInteger(children.size());

        for (var child : children) {
            final var path = parentPath.node(child.name());
            tx.exists(datastore, path).addCallback(new ExistenceCheck(future, counter, parentPath, path, expected),
                MoreExecutors.directExecutor());
        }
        return future;
    }

    @Override
    public void onSuccess(final Boolean result) {
        if (expected != result) {
            // This is okay to race with onFailure(): we either report this or failure, the end result is failure,
            // though slightly different. This a result of datastore timing, hence there is nothing we can do about it.
            future.set(new Conflict(path));
            // Failure is set first, before a parallel success can see counter being 0, hence we win
            counter.decrementAndGet();
            return;
        }

        final int count = counter.decrementAndGet();
        if (count == 0) {
            // Everything else was a success, we ware done.
            future.set(Success.INSTANCE);
        }
    }

    @Override
    @SuppressFBWarnings("BC_UNCONFIRMED_CAST_OF_RETURN_VALUE")
    public void onFailure(final Throwable throwable) {
        final var cause = ReadFailedException.MAPPER.apply(throwable instanceof Exception ex ? ex
            : new ExecutionException(throwable));
        // We are not decrementing the counter here to ensure onSuccess() does not attempt to set success. Failure paths
        // are okay -- they differ only in what we report. We rely on SettableFuture's synchronization faculties to
        // reconcile any conflict with onSuccess() failure path.
        future.setFailure(new RestconfDocumentedException("Could not determine the existence of path " + parentPath,
            cause, cause.getErrorList()));
    }
}
