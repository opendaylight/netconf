/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.distributed.tx.impl;

import com.google.common.base.Function;
import com.google.common.base.Optional;

import java.io.Closeable;
import java.io.IOException;
import java.util.Deque;
import java.util.Iterator;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.util.concurrent.*;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.common.api.TransactionStatus;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.distributed.tx.api.DTxException;
import org.opendaylight.distributed.tx.spi.CachedData;
import org.opendaylight.distributed.tx.spi.DTXReadWriteTransaction;
import org.opendaylight.distributed.tx.spi.TxCache;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.data.api.ModifyAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CachingReadWriteTx implements TxCache, DTXReadWriteTransaction, Closeable {
    private static final Logger LOG = LoggerFactory.getLogger(CachingReadWriteTx.class);
    private final ReadWriteTransaction delegate;
    public final Deque<CachedData> cache = new ConcurrentLinkedDeque<>();
    private final ExecutorService executorPoolPerCache;

    public CachingReadWriteTx(@Nonnull final ReadWriteTransaction delegate) {
        this.delegate = delegate;
        this.executorPoolPerCache = Executors.newCachedThreadPool();
    }

    @Override public Iterator<CachedData> iterator() {
        return cache.descendingIterator();
    }

    @Override public <T extends DataObject> CheckedFuture<Optional<T>, ReadFailedException> read(
        final LogicalDatastoreType logicalDatastoreType, final InstanceIdentifier<T> instanceIdentifier) {
        return delegate.read(logicalDatastoreType, instanceIdentifier);
    }
    @Override public Object getIdentifier() {
        return delegate.getIdentifier();
    }

    @Override public void delete(final LogicalDatastoreType logicalDatastoreType,
        final InstanceIdentifier<?> instanceIdentifier) {
        /*  This is best effort API so that no exception will be thrown. */
        this.asyncDelete(logicalDatastoreType, instanceIdentifier);
    }

    public int getSizeOfCache(){
        return this.cache.size();
    }

    public CheckedFuture<Void, DTxException> asyncDelete(final LogicalDatastoreType logicalDatastoreType,
                                      final InstanceIdentifier<?> instanceIdentifier) {
        @SuppressWarnings("unchecked")
        final CheckedFuture<Optional<DataObject>, ReadFailedException> readFuture = delegate
                .read(logicalDatastoreType, (InstanceIdentifier<DataObject>) instanceIdentifier);

        final SettableFuture<Void> retFuture = SettableFuture.create();

        Futures.addCallback(readFuture, new FutureCallback<Optional<DataObject>>() {
            @Override public void onSuccess(final Optional<DataObject> result) {
                synchronized (this) {
                    cache.add(new CachedData(logicalDatastoreType, instanceIdentifier, result.orNull(), ModifyAction.DELETE));
                }

                final ListeningExecutorService executorService = MoreExecutors.listeningDecorator(executorPoolPerCache);
                final ListenableFuture asyncDeleteFuture = executorService.submit(new Callable() {
                    @Override
                    public Object call() throws Exception {
                        delegate.delete(logicalDatastoreType, instanceIdentifier);
                        return null;
                    }
                });

                Futures.addCallback(asyncDeleteFuture, new FutureCallback() {
                    @Override
                    public void onSuccess(@Nullable Object result) {
                        retFuture.set(null);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        LOG.trace("async delete failure");
                        retFuture.setException(t);
                    }
                });
            }

            @Override public void onFailure(final Throwable t) {
                retFuture.setException(new DTxException.ReadFailedException("failed to read from node in delete action", t));
            }
        });

        return Futures.makeChecked(retFuture, new Function<Exception, DTxException>() {
            @Nullable
            @Override
            public DTxException apply(@Nullable Exception e) {
                e =(Exception)e.getCause();
                return e instanceof DTxException ? (DTxException)e : new DTxException("delete operation failed ", e);
            }
        });
    }

    @Override public <T extends DataObject> void merge(final LogicalDatastoreType logicalDatastoreType,
        final InstanceIdentifier<T> instanceIdentifier, final T t) {
        this.asyncMerge(logicalDatastoreType, instanceIdentifier, t);
    }

    public <T extends DataObject> CheckedFuture<Void, DTxException>asyncMerge(final LogicalDatastoreType logicalDatastoreType,
                                                       final InstanceIdentifier<T> instanceIdentifier, final T t) {
        final CheckedFuture<Optional<T>, ReadFailedException> readFuture = delegate
                .read(logicalDatastoreType, instanceIdentifier);

        final SettableFuture<Void> retFuture = SettableFuture.create();

        Futures.addCallback(readFuture, new FutureCallback<Optional<T>>() {
            @Override public void onSuccess(final Optional<T> result) {
                synchronized (this) {
                    cache.add(new CachedData(logicalDatastoreType, instanceIdentifier, result.orNull(), ModifyAction.MERGE));
                }

                final ListeningExecutorService executorService = MoreExecutors.listeningDecorator(executorPoolPerCache);
                final ListenableFuture asyncMergeFuture = executorService.submit(new Callable() {
                    @Override
                    public Object call() throws Exception {
                        delegate.merge(logicalDatastoreType, instanceIdentifier, t);
                        return null;
                    }
                });

                Futures.addCallback(asyncMergeFuture, new FutureCallback() {
                    @Override
                    public void onSuccess(@Nullable Object result) {
                        retFuture.set(null);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        LOG.trace("async merge failure");
                        retFuture.setException(t);
                    }
                });
            }

            @Override public void onFailure(final Throwable t) {
                retFuture.setException(new DTxException.ReadFailedException("failed to read from node in merge action", t));
            }
        });

        return Futures.makeChecked(retFuture, new Function<Exception, DTxException>() {
            @Nullable
            @Override
            public DTxException apply(@Nullable Exception e) {
                e =(Exception)e.getCause();
                return e instanceof DTxException ? (DTxException)e : new DTxException("merge operation failed", e);
            }
        });
    }

    @Deprecated
    @Override public <T extends DataObject> void merge(final LogicalDatastoreType logicalDatastoreType,
        final InstanceIdentifier<T> instanceIdentifier, final T t, final boolean b) {
        delegate.merge(logicalDatastoreType, instanceIdentifier, t, b);
    }

    @Override public <T extends DataObject> void put(final LogicalDatastoreType logicalDatastoreType,
        final InstanceIdentifier<T> instanceIdentifier, final T t) {
        this.asyncPut(logicalDatastoreType, instanceIdentifier, t);
    }

    public <T extends DataObject> CheckedFuture<Void, DTxException> asyncPut(final LogicalDatastoreType logicalDatastoreType,
                                                     final InstanceIdentifier<T> instanceIdentifier, final T t) {
        final SettableFuture<Void> retFuture = SettableFuture.create();

        final CheckedFuture<Optional<T>, ReadFailedException> read = delegate
                .read(logicalDatastoreType, instanceIdentifier);
        Futures.addCallback(read, new FutureCallback<Optional<T>>() {

            @Override
            public void onSuccess(final Optional<T> result) {
                synchronized (this) {
                    cache.add(new CachedData(logicalDatastoreType, instanceIdentifier, result.orNull(), ModifyAction.REPLACE));
                }

                final ListeningExecutorService executorService = MoreExecutors.listeningDecorator(executorPoolPerCache);
                final ListenableFuture asyncPutFuture = executorService.submit(new Callable() {
                    @Override
                    public Object call() throws Exception {
                        delegate.put(logicalDatastoreType, instanceIdentifier, t);
                        return null;
                    }
                });

                Futures.addCallback(asyncPutFuture, new FutureCallback() {
                    @Override
                    public void onSuccess(@Nullable Object result) {
                        retFuture.set(null);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        LOG.trace("async put failure");
                        retFuture.setException(t);
                    }
                });
            }

            @Override
            public void onFailure(final Throwable t) {
                retFuture.setException(new DTxException.ReadFailedException("failed to read from node in put action", t));
            }
        });

        return Futures.makeChecked(retFuture, new Function<Exception, DTxException>() {
            @Nullable
            @Override
            public DTxException apply(@Nullable Exception e) {
                e =(Exception)e.getCause();
                return e instanceof DTxException ? (DTxException)e : new DTxException("put operation failed", e);
            }
        });
    }

    @Deprecated
    @Override public <T extends DataObject> void put(final LogicalDatastoreType logicalDatastoreType,
        final InstanceIdentifier<T> instanceIdentifier, final T t, final boolean ensureParents) {
        delegate.put(logicalDatastoreType, instanceIdentifier, t, ensureParents);
    }

    @Override public boolean cancel() {
        return delegate.cancel();
    }

    @Override public CheckedFuture<Void, TransactionCommitFailedException> submit() {
        return delegate.submit();
    }

    @Deprecated
    @Override public ListenableFuture<RpcResult<TransactionStatus>> commit() {
        return delegate.commit();
    }

    @Override public void close() throws IOException {
        cancel();
        synchronized (this) {
            cache.clear();
        }
    }
}
