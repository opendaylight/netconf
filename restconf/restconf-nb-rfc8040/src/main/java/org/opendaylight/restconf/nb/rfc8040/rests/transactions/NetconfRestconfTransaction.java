/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.transactions;

import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.common.api.TransactionCommitFailedException;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.netconf.dom.api.NetconfDataTreeService;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNodeContainer;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

final class NetconfRestconfTransaction extends RestconfTransaction {
    private final NetconfDataTreeService netconfService;

    private final SettableFuture<DOMRpcResult> lock;
    private List<ListenableFuture<? extends DOMRpcResult>> resultsFutures = new ArrayList<>();

    NetconfRestconfTransaction(final NetconfDataTreeService netconfService) {
        this.netconfService = requireNonNull(netconfService);
        //TODO we always return one value here
        final List<ListenableFuture<? extends DOMRpcResult>> lockDatabase = netconfService.lock();
        this.resultsFutures.addAll(lockDatabase);
        lock = SettableFuture.create();
        lock.setFuture(lockDatabase.get(0));
    }

    @Override
    public void cancel() {
        resultsFutures = null;
        Futures.addCallback(lock, new FutureCallback<>() {
            @Override
            public void onSuccess(final DOMRpcResult result) {
                if (isSuccess(result)) {
                    netconfService.discardChanges();
                    netconfService.unlock();
                }
            }

            @Override
            public void onFailure(Throwable throwable) {
            }
        }, MoreExecutors.directExecutor());
    }

    @Override
    public void delete(final LogicalDatastoreType store, final YangInstanceIdentifier path) {
        final SettableFuture<DOMRpcResult> delete = SettableFuture.create();
        Futures.addCallback(lock, new FutureCallback<>() {
            @Override
            public void onSuccess(final DOMRpcResult result) {
                if (isSuccess(result)) {
                    delete.setFuture(netconfService.delete(store, path));
                } else {
                    delete.set(result);
                }
            }

            @Override
            public void onFailure(Throwable throwable) {
                delete.setException(throwable);
            }
        }, MoreExecutors.directExecutor());
        resultsFutures.add(delete);
    }

    @Override
    public void remove(final LogicalDatastoreType store, final YangInstanceIdentifier path) {
        final SettableFuture<DOMRpcResult> remove = SettableFuture.create();
        Futures.addCallback(lock, new FutureCallback<>() {
            @Override
            public void onSuccess(final DOMRpcResult result) {
                if (isSuccess(result)) {
                    remove.setFuture(netconfService.remove(store, path));
                } else {
                    remove.set(result);
                }
            }

            @Override
            public void onFailure(Throwable throwable) {
                remove.setException(throwable);
            }
        }, MoreExecutors.directExecutor());
        resultsFutures.add(remove);
    }

    @Override
    public void merge(final LogicalDatastoreType store, final YangInstanceIdentifier path,
                      final NormalizedNode<?, ?> data) {
        final SettableFuture<DOMRpcResult> merge = SettableFuture.create();
        Futures.addCallback(lock, new FutureCallback<>() {
            @Override
            public void onSuccess(final DOMRpcResult result) {
                if (isSuccess(result)) {
                    merge.setFuture(netconfService.merge(store, path, data, Optional.empty()));
                } else {
                    merge.set(result);
                }
            }

            @Override
            public void onFailure(Throwable throwable) {
                merge.setException(throwable);
            }
        }, MoreExecutors.directExecutor());
        resultsFutures.add(merge);
    }

    @Override
    public void create(final LogicalDatastoreType store, final YangInstanceIdentifier path,
                       final NormalizedNode<?, ?> data, final SchemaContext schemaContext) {
        if (data instanceof MapNode || data instanceof LeafSetNode) {
            final NormalizedNode<?, ?> emptySubTree = ImmutableNodes.fromInstanceId(schemaContext, path);
            merge(LogicalDatastoreType.CONFIGURATION, YangInstanceIdentifier.create(emptySubTree.getIdentifier()),
                emptySubTree);

            for (final NormalizedNode<?, ?> child : ((NormalizedNodeContainer<?, ?, ?>) data).getValue()) {
                final YangInstanceIdentifier childPath = path.node(child.getIdentifier());
                doCreate(store, childPath, child);
            }
        } else {
            doCreate(store, path, data);
        }
    }

    private void doCreate(final LogicalDatastoreType store, final YangInstanceIdentifier path,
                          final NormalizedNode<?, ?> data) {
        final SettableFuture<DOMRpcResult> create = SettableFuture.create();
        Futures.addCallback(lock, new FutureCallback<>() {
            @Override
            public void onSuccess(final DOMRpcResult result) {
                if (isSuccess(result)) {
                    create.setFuture(netconfService.create(store, path, data, Optional.empty()));
                } else {
                    create.set(result);
                }
            }

            @Override
            public void onFailure(Throwable throwable) {
                create.setException(throwable);
            }
        }, MoreExecutors.directExecutor());
        resultsFutures.add(create);
    }

    @Override
    public void replace(final LogicalDatastoreType store, final YangInstanceIdentifier path,
                        final NormalizedNode<?, ?> data, final SchemaContext schemaContext) {
        if (data instanceof MapNode || data instanceof LeafSetNode) {
            final NormalizedNode<?, ?> emptySubTree = ImmutableNodes.fromInstanceId(schemaContext, path);
            merge(LogicalDatastoreType.CONFIGURATION, YangInstanceIdentifier.create(emptySubTree.getIdentifier()),
                emptySubTree);

            for (final NormalizedNode<?, ?> child : ((NormalizedNodeContainer<?, ?, ?>) data).getValue()) {
                final YangInstanceIdentifier childPath = path.node(child.getIdentifier());
                doReplace(store, childPath, child);
            }
        } else {
            doReplace(store, path, data);
        }
    }

    private void doReplace(final LogicalDatastoreType store, final YangInstanceIdentifier path,
                           final NormalizedNode<?, ?> data) {
        final SettableFuture<DOMRpcResult> replace = SettableFuture.create();
        Futures.addCallback(lock, new FutureCallback<>() {
            @Override
            public void onSuccess(final DOMRpcResult result) {
                if (isSuccess(result)) {
                    replace.setFuture(netconfService.replace(store, path, data, Optional.empty()));
                } else {
                    replace.set(result);
                }
            }

            @Override
            public void onFailure(Throwable throwable) {
                replace.setException(throwable);
            }
        }, MoreExecutors.directExecutor());
        resultsFutures.add(replace);
    }

    @Override
    public FluentFuture<? extends @NonNull CommitInfo> commit() {
        final SettableFuture<CommitInfo> commit = SettableFuture.create();
        Futures.addCallback(lock, new FutureCallback<>() {
            @Override
            public void onSuccess(final DOMRpcResult result) {
                if (isSuccess(result)) {
                    Futures.addCallback(commit, new FutureCallback<>() {
                        @Override
                        public void onSuccess(CommitInfo result) {
                            netconfService.unlock();
                        }

                        @Override
                        public void onFailure(Throwable throwable) {
                            netconfService.discardChanges();
                            netconfService.unlock();
                        }
                    }, MoreExecutors.directExecutor());
                    commit.setFuture(netconfService.commit(resultsFutures));
                } else {
                    final Collection<? extends RpcError> errors = result.getErrors();
                    commit.setException(new TransactionCommitFailedException(
                        String.format("Commit of transaction %s failed", this),
                        errors.toArray(new RpcError[errors.size()])));
                }
            }

            @Override
            public void onFailure(Throwable throwable) {
                commit.setException(throwable);
            }
        }, MoreExecutors.directExecutor());
        return FluentFuture.from(commit);
    }

    @SuppressFBWarnings(value = "UPM_UNCALLED_PRIVATE_METHOD",
        justification = "https://github.com/spotbugs/spotbugs/issues/811")
    private static boolean isSuccess(final DOMRpcResult result) {
        return result.getErrors().isEmpty();
    }
}
