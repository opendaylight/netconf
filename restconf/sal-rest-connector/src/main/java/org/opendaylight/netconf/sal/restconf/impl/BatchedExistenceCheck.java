/*
 * Copyright (c) 2017 Pantheon Technologies, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.restconf.impl;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Collection;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadTransaction;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

final class BatchedExistenceCheck {
    private static final AtomicIntegerFieldUpdater<BatchedExistenceCheck> UPDATER =
            AtomicIntegerFieldUpdater.newUpdater(BatchedExistenceCheck.class, "outstanding");

    private final SettableFuture<Entry<YangInstanceIdentifier, ReadFailedException>> future = SettableFuture.create();

    @SuppressWarnings("unused")
    private volatile int outstanding;

    private BatchedExistenceCheck(final int total) {
        this.outstanding = total;
    }

    static BatchedExistenceCheck start(final DOMDataReadTransaction readTx,
            final LogicalDatastoreType datastore, final YangInstanceIdentifier parentPath,
            final Collection<? extends NormalizedNode<?, ?>> children) {
        final BatchedExistenceCheck ret = new BatchedExistenceCheck(children.size());
        for (NormalizedNode<?, ?> child : children) {
            final YangInstanceIdentifier path = parentPath.node(child.getIdentifier());
            final ListenableFuture<Boolean> f = readTx.exists(datastore, path);
            Futures.addCallback(f, new FutureCallback<Boolean>() {
                @Override
                public void onSuccess(final Boolean result) {
                    ret.complete(path, result.booleanValue());
                }

                @Override
                public void onFailure(final Throwable throwable) {
                    final Exception e;
                    if (throwable instanceof Exception) {
                        e = (Exception) throwable;
                    } else {
                        e = new ExecutionException(throwable);
                    }

                    ret.complete(path, ReadFailedException.MAPPER.apply(e));
                }
            });
        }

        return ret;
    }

    Entry<YangInstanceIdentifier, ReadFailedException> getFailure() throws InterruptedException {
        try {
            return future.get();
        } catch (ExecutionException e) {
            // This should never happen
            throw new IllegalStateException(e);
        }
    }

    private void complete(final YangInstanceIdentifier childPath, final boolean present) {
        final int count = UPDATER.decrementAndGet(this);
        if (present) {
            future.set(new SimpleImmutableEntry<>(childPath, null));
        } else if (count == 0) {
            future.set(null);
        }
    }

    private void complete(final YangInstanceIdentifier childPath, final ReadFailedException cause) {
        UPDATER.decrementAndGet(this);
        future.set(new SimpleImmutableEntry<>(childPath, cause));
    }
}
