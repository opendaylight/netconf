/*
 * Copyright (c) 2014, 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.sal.tx;

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
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.common.api.TransactionCommitFailedException;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.api.ModifyAction;
import org.opendaylight.netconf.api.NetconfDocumentedException;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfBaseOps;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.MixinNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractWriteTx implements DOMDataTreeWriteTransaction {
    private static final Logger LOG  = LoggerFactory.getLogger(AbstractWriteTx.class);

    protected final RemoteDeviceId id;
    protected final NetconfBaseOps netOps;
    protected final boolean rollbackSupport;
    protected final List<ListenableFuture<? extends DOMRpcResult>> resultsFutures = new ArrayList<>();
    private final List<TxListener> listeners = new CopyOnWriteArrayList<>();
    // Allow commit to be called only once
    protected volatile boolean finished = false;
    protected final boolean isLockAllowed;
    protected volatile ListenableFuture<? extends DOMRpcResult> lock =
        Futures.immediateFailedFuture(new NetconfDocumentedException("Lock database operation must be called first!"));

    public AbstractWriteTx(final RemoteDeviceId id, final NetconfBaseOps netconfOps, final boolean rollbackSupport,
            final boolean isLockAllowed) {
        this.netOps = netconfOps;
        this.id = id;
        this.rollbackSupport = rollbackSupport;
        this.isLockAllowed = isLockAllowed;
        init();
    }

    protected static boolean isSuccess(final DOMRpcResult result) {
        return result.getErrors().isEmpty();
    }

    protected void checkNotFinished() {
        checkState(!isFinished(), "%s: Transaction %s already finished", id, getIdentifier());
    }

    protected boolean isFinished() {
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

    protected abstract void init();

    protected abstract void cleanup();

    @Override
    public Object getIdentifier() {
        return this;
    }

    @Override
    public synchronized void put(final LogicalDatastoreType store, final YangInstanceIdentifier path,
                                 final NormalizedNode<?, ?> data) {
        checkEditable(store);

        // Trying to write only mixin nodes (not visible when serialized).
        // Ignoring. Some devices cannot handle empty edit-config rpc
        if (containsOnlyNonVisibleData(path, data)) {
            LOG.debug("Ignoring put for {} and data {}. Resulting data structure is empty.", path, data);
            return;
        }

        final DataContainerChild<?, ?> editStructure = netOps.createEditConfigStrcture(Optional.ofNullable(data),
                        Optional.of(ModifyAction.REPLACE), path);
        editConfig(path, Optional.ofNullable(data), editStructure, Optional.empty(), "put");
    }

    @Override
    public synchronized void merge(final LogicalDatastoreType store, final YangInstanceIdentifier path,
                                   final NormalizedNode<?, ?> data) {
        checkEditable(store);

        // Trying to write only mixin nodes (not visible when serialized).
        // Ignoring. Some devices cannot handle empty edit-config rpc
        if (containsOnlyNonVisibleData(path, data)) {
            LOG.debug("Ignoring merge for {} and data {}. Resulting data structure is empty.", path, data);
            return;
        }

        final DataContainerChild<?, ?> editStructure =  netOps.createEditConfigStrcture(Optional.ofNullable(data),
            Optional.empty(), path);
        editConfig(path, Optional.ofNullable(data), editStructure, Optional.empty(), "merge");
    }

    /**
     * Check whether the data to be written consists only from mixins.
     */
    private static boolean containsOnlyNonVisibleData(final YangInstanceIdentifier path,
                                                      final NormalizedNode<?, ?> data) {
        // There's only one such case:top level list (pathArguments == 1 && data is Mixin)
        // any other mixin nodes are contained by a "regular" node thus visible when serialized
        return path.getPathArguments().size() == 1 && data instanceof MixinNode;
    }

    @Override
    public synchronized void delete(final LogicalDatastoreType store, final YangInstanceIdentifier path) {
        checkEditable(store);
        final DataContainerChild<?, ?> editStructure = netOps.createEditConfigStrcture(Optional.empty(),
                        Optional.of(ModifyAction.DELETE), path);
        editConfig(path, Optional.empty(), editStructure, Optional.of(ModifyAction.NONE), "delete");
    }

    @Override
    public FluentFuture<? extends CommitInfo> commit() {
        final SettableFuture<CommitInfo> resultFuture = SettableFuture.create();
        Futures.addCallback(commitConfiguration(), new FutureCallback<>() {
            @Override
            public void onSuccess(final RpcResult<Void> result) {
                if (!result.isSuccessful()) {
                    final Collection<RpcError> errors = result.getErrors();
                    resultFuture.setException(new TransactionCommitFailedException(
                        String.format("Commit of transaction %s failed", getIdentifier()),
                            errors.toArray(new RpcError[errors.size()])));
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

    protected final ListenableFuture<RpcResult<Void>> commitConfiguration() {
        listeners.forEach(listener -> listener.onTransactionSubmitted(this));
        checkNotFinished();
        finished = true;
        final ListenableFuture<RpcResult<Void>> result = performCommit();
        Futures.addCallback(result, new FutureCallback<>() {
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

    protected abstract ListenableFuture<RpcResult<Void>> performCommit();

    private void checkEditable(final LogicalDatastoreType store) {
        checkNotFinished();
        checkArgument(store == LogicalDatastoreType.CONFIGURATION,
                "Can edit only configuration data, not %s", store);
    }

    protected abstract void editConfig(YangInstanceIdentifier path, Optional<NormalizedNode<?, ?>> data,
                                       DataContainerChild<?, ?> editStructure,
                                       Optional<ModifyAction> defaultOperation, String operation);

    protected ListenableFuture<RpcResult<Void>> resultsToTxStatus() {
        final SettableFuture<RpcResult<Void>> transformed = SettableFuture.create();

        Futures.addCallback(Futures.allAsList(resultsFutures), new FutureCallback<>() {
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
                                new Exception(throwable),
                                DocumentedException.ErrorType.APPLICATION,
                                DocumentedException.ErrorTag.OPERATION_FAILED,
                                DocumentedException.ErrorSeverity.ERROR);
                transformed.setException(exception);
            }
        }, MoreExecutors.directExecutor());

        return transformed;
    }

    @SuppressFBWarnings(value = "UPM_UNCALLED_PRIVATE_METHOD",
            justification = "https://github.com/spotbugs/spotbugs/issues/811")
    private void extractResult(final List<DOMRpcResult> domRpcResults,
                               final SettableFuture<RpcResult<Void>> transformed) {
        DocumentedException.ErrorType errType = DocumentedException.ErrorType.APPLICATION;
        DocumentedException.ErrorSeverity errSeverity = DocumentedException.ErrorSeverity.ERROR;
        StringBuilder msgBuilder = new StringBuilder();
        boolean errorsEncouneterd = false;
        String errorTag = "operation-failed";

        for (final DOMRpcResult domRpcResult : domRpcResults) {
            if (!domRpcResult.getErrors().isEmpty()) {
                errorsEncouneterd = true;
                final RpcError error = domRpcResult.getErrors().iterator().next();
                final RpcError.ErrorType errorType = error.getErrorType();
                switch (errorType) {
                    case RPC:
                        errType = DocumentedException.ErrorType.RPC;
                        break;
                    case PROTOCOL:
                        errType = DocumentedException.ErrorType.PROTOCOL;
                        break;
                    case TRANSPORT:
                        errType = DocumentedException.ErrorType.TRANSPORT;
                        break;
                    case APPLICATION:
                        errType = DocumentedException.ErrorType.APPLICATION;
                        break;
                    default:
                        errType = DocumentedException.ErrorType.APPLICATION;
                        break;
                }
                final RpcError.ErrorSeverity severity = error.getSeverity();
                switch (severity) {
                    case ERROR:
                        errSeverity = DocumentedException.ErrorSeverity.ERROR;
                        break;
                    case WARNING:
                        errSeverity = DocumentedException.ErrorSeverity.WARNING;
                        break;
                    default:
                        errSeverity = DocumentedException.ErrorSeverity.ERROR;
                        break;
                }
                msgBuilder.append(error.getMessage());
                msgBuilder.append(error.getInfo());
                errorTag = error.getTag();
            }
        }
        if (errorsEncouneterd) {
            final NetconfDocumentedException exception = new NetconfDocumentedException(id
                    + ":RPC during tx failed. " + msgBuilder.toString(),
                    errType,
                    DocumentedException.ErrorTag.from(errorTag),
                    errSeverity);
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
