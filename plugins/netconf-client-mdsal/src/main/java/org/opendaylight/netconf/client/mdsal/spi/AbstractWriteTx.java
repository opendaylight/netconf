/*
 * Copyright (c) 2014, 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.spi;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.common.api.TransactionCommitFailedException;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.netconf.api.EffectiveOperation;
import org.opendaylight.netconf.api.NetconfDocumentedException;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.impl.NetconfBaseOps;
import org.opendaylight.yangtools.yang.common.ErrorSeverity;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.MixinNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class AbstractWriteTx implements DOMDataTreeWriteTransaction {
    private static final Logger LOG  = LoggerFactory.getLogger(AbstractWriteTx.class);

    final RemoteDeviceId id;
    final NetconfBaseOps netOps;
    final boolean rollbackSupport;
    final List<ListenableFuture<? extends DOMRpcResult>> resultsFutures = new ArrayList<>();
    private final List<TxListener> listeners = new CopyOnWriteArrayList<>();
    // Allow commit to be called only once
    volatile boolean finished = false;
    final boolean isLockAllowed;

    @SuppressFBWarnings(value = "MC_OVERRIDABLE_METHOD_CALL_IN_CONSTRUCTOR", justification = "Behavior-only subclasses")
    AbstractWriteTx(final RemoteDeviceId id, final NetconfBaseOps netconfOps, final boolean rollbackSupport,
            final boolean isLockAllowed) {
        netOps = netconfOps;
        this.id = id;
        this.rollbackSupport = rollbackSupport;
        this.isLockAllowed = isLockAllowed;
        init();
    }

    static boolean isSuccess(final DOMRpcResult result) {
        return result.errors().isEmpty();
    }

    void checkNotFinished() {
        checkState(!isFinished(), "%s: Transaction %s already finished", id, getIdentifier());
    }

    boolean isFinished() {
        return finished;
    }

    @Override
    public synchronized boolean cancel() {
        if (isFinished()) {
            return false;
        }
        listeners.forEach(listener -> listener.onTransactionCancelled(this));
        finished = true;
        cleanup();
        return true;
    }

    // FIXME: only called from ctor which needs @SuppressDBWarnings. Refactor class hierarchy without this method (here)
    abstract void init();

    abstract void cleanup();

    @Override
    public Object getIdentifier() {
        return this;
    }

    @Override
    public synchronized void put(final LogicalDatastoreType store, final YangInstanceIdentifier path,
                                 final NormalizedNode data) {
        checkEditable(store);

        // Trying to write only mixin nodes (not visible when serialized).
        // Ignoring. Some devices cannot handle empty edit-config rpc
        if (containsOnlyNonVisibleData(path, data)) {
            LOG.debug("Ignoring put for {} and data {}. Resulting data structure is empty.", path, data);
            return;
        }

        final DataContainerChild editStructure = netOps.createEditConfigStructure(Optional.ofNullable(data),
                        Optional.of(EffectiveOperation.REPLACE), path);
        editConfig(path, Optional.ofNullable(data), editStructure, Optional.empty(), "put");
    }

    @Override
    public synchronized void merge(final LogicalDatastoreType store, final YangInstanceIdentifier path,
                                   final NormalizedNode data) {
        checkEditable(store);

        // Trying to write only mixin nodes (not visible when serialized).
        // Ignoring. Some devices cannot handle empty edit-config rpc
        if (containsOnlyNonVisibleData(path, data)) {
            LOG.debug("Ignoring merge for {} and data {}. Resulting data structure is empty.", path, data);
            return;
        }

        final DataContainerChild editStructure =  netOps.createEditConfigStructure(Optional.ofNullable(data),
            Optional.empty(), path);
        editConfig(path, Optional.ofNullable(data), editStructure, Optional.empty(), "merge");
    }

    /**
     * Check whether the data to be written consists only from mixins.
     */
    private static boolean containsOnlyNonVisibleData(final YangInstanceIdentifier path, final NormalizedNode data) {
        // There's only one such case:top level list (pathArguments == 1 && data is Mixin)
        // any other mixin nodes are contained by a "regular" node thus visible when serialized
        return path.getPathArguments().size() == 1 && data instanceof MixinNode;
    }

    @Override
    public synchronized void delete(final LogicalDatastoreType store, final YangInstanceIdentifier path) {
        checkEditable(store);
        final DataContainerChild editStructure = netOps.createEditConfigStructure(Optional.empty(),
                        Optional.of(EffectiveOperation.DELETE), path);
        editConfig(path, Optional.empty(), editStructure, Optional.of(EffectiveOperation.NONE), "delete");
    }

    @Override
    public FluentFuture<? extends CommitInfo> commit() {
        final SettableFuture<CommitInfo> resultFuture = SettableFuture.create();
        Futures.addCallback(commitConfiguration(), new FutureCallback<RpcResult<Void>>() {
            @Override
            public void onSuccess(final RpcResult<Void> result) {
                if (!result.isSuccessful()) {
                    resultFuture.setException(new TransactionCommitFailedException(
                        String.format("Commit of transaction %s failed", getIdentifier()),
                        result.getErrors().toArray(new RpcError[0])));
                    return;
                }

                resultFuture.set(CommitInfo.empty());
            }

            @Override
            public void onFailure(final Throwable failure) {
                resultFuture.setException(new TransactionCommitFailedException(
                        String.format("Commit of transaction %s failed", getIdentifier()), failure));
            }
        }, MoreExecutors.directExecutor());

        return FluentFuture.from(resultFuture);
    }

    final ListenableFuture<RpcResult<Void>> commitConfiguration() {
        listeners.forEach(listener -> listener.onTransactionSubmitted(this));
        checkNotFinished();
        finished = true;
        final ListenableFuture<RpcResult<Void>> result = performCommit();
        Futures.addCallback(result, new FutureCallback<RpcResult<Void>>() {
            @Override
            public void onSuccess(final RpcResult<Void> rpcResult) {
                if (rpcResult.isSuccessful()) {
                    listeners.forEach(txListener -> txListener.onTransactionSuccessful(AbstractWriteTx.this));
                } else {
                    final TransactionCommitFailedException cause =
                            new TransactionCommitFailedException("Transaction failed",
                                    rpcResult.getErrors().toArray(new RpcError[rpcResult.getErrors().size()]));
                    listeners.forEach(listener -> listener.onTransactionFailed(AbstractWriteTx.this, cause));
                }
            }

            @Override
            public void onFailure(final Throwable throwable) {
                listeners.forEach(listener -> listener.onTransactionFailed(AbstractWriteTx.this, throwable));
            }
        }, MoreExecutors.directExecutor());
        return result;
    }

    abstract ListenableFuture<RpcResult<Void>> performCommit();

    private void checkEditable(final LogicalDatastoreType store) {
        checkNotFinished();
        checkArgument(store == LogicalDatastoreType.CONFIGURATION,
                "Can edit only configuration data, not %s", store);
    }

    abstract void editConfig(YangInstanceIdentifier path, Optional<NormalizedNode> data,
                             DataContainerChild editStructure, Optional<EffectiveOperation> defaultOperation,
                             String operation);

    ListenableFuture<RpcResult<Void>> resultsToTxStatus() {
        final SettableFuture<RpcResult<Void>> transformed = SettableFuture.create();

        Futures.addCallback(Futures.allAsList(resultsFutures), new FutureCallback<List<DOMRpcResult>>() {
            @Override
            public void onSuccess(final List<DOMRpcResult> domRpcResults) {
                if (!transformed.isDone()) {
                    extractResult(domRpcResults, transformed);
                }
            }

            @Override
            public void onFailure(final Throwable throwable) {
                final NetconfDocumentedException exception =
                        new NetconfDocumentedException(
                                id + ":RPC during tx returned an exception" + throwable.getMessage(),
                                // FIXME: add proper unmask/wrap to ExecutionException
                                new Exception(throwable),
                                ErrorType.APPLICATION, ErrorTag.OPERATION_FAILED, ErrorSeverity.ERROR);
                transformed.setException(exception);
            }
        }, MoreExecutors.directExecutor());

        return transformed;
    }

    private void extractResult(final List<DOMRpcResult> domRpcResults,
                               final SettableFuture<RpcResult<Void>> transformed) {
        ErrorType errType = ErrorType.APPLICATION;
        ErrorSeverity errSeverity = ErrorSeverity.ERROR;
        StringBuilder msgBuilder = new StringBuilder();
        boolean errorsEncouneterd = false;
        ErrorTag errorTag = ErrorTag.OPERATION_FAILED;

        for (final DOMRpcResult domRpcResult : domRpcResults) {
            if (!domRpcResult.errors().isEmpty()) {
                errorsEncouneterd = true;
                final RpcError error = domRpcResult.errors().iterator().next();

                errType = error.getErrorType();
                errSeverity = error.getSeverity();
                msgBuilder.append(error.getMessage());
                msgBuilder.append(error.getInfo());
                errorTag = error.getTag();
            }
        }
        if (errorsEncouneterd) {
            final NetconfDocumentedException exception = new NetconfDocumentedException(
                    id + ":RPC during tx failed. " + msgBuilder, errType, errorTag, errSeverity);
            transformed.setException(exception);
            return;
        }
        transformed.set(RpcResultBuilder.<Void>success().build());
    }

    AutoCloseable addListener(final TxListener listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }
}
