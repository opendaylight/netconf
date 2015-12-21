/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.connect.netconf.sal.tx;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import org.opendaylight.controller.md.sal.common.api.TransactionStatus;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcResult;
import org.opendaylight.netconf.api.NetconfDocumentedException;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfBaseOps;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfRpcFutureCallback;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.opendaylight.yangtools.yang.data.api.ModifyAction;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tx implementation for netconf devices that support only candidate datastore and no writable running
 * The sequence goes as:
 * <ol>
 *   <li>Lock candidate datastore on tx construction
 *     <ul>
 *       <li>Lock has to succeed, if it does not, an attempt to discard changes is made</li>
 *       <li>Discard changes has to succeed</li>
 *       <li>If discard is successful, lock is reattempted</li>
 *       <li>Second lock attempt has to succeed</li>
 *     </ul>
 *   </li>
 *   <li>Edit-config in candidate N times
 *     <ul>
 *       <li>If any issue occurs during edit, datastore is discarded using discard-changes rpc, unlocked and an exception is thrown async</li>
 *     </ul>
 *   </li>
 *   <li>Commit and Unlock candidate datastore async</li>
 * </ol>
 */
public class WriteCandidateTx extends AbstractWriteTx {

    private static final Logger LOG  = LoggerFactory.getLogger(WriteCandidateTx.class);

    private static final Function<DOMRpcResult, RpcResult<TransactionStatus>> RPC_RESULT_TO_TX_STATUS = new Function<DOMRpcResult, RpcResult<TransactionStatus>>() {
        @Override
        public RpcResult<TransactionStatus> apply(final DOMRpcResult input) {
            if (isSuccess(input)) {
                return RpcResultBuilder.success(TransactionStatus.COMMITED).build();
            } else {
                final RpcResultBuilder<TransactionStatus> failed = RpcResultBuilder.failed();
                for (final RpcError rpcError : input.getErrors()) {
                    failed.withError(rpcError.getErrorType(), rpcError.getTag(), rpcError.getMessage(),
                            rpcError.getApplicationTag(), rpcError.getInfo(), rpcError.getCause());
                }
                return failed.build();
            }
        }
    };

    public WriteCandidateTx(final RemoteDeviceId id, final NetconfBaseOps rpc, final boolean rollbackSupport, long requestTimeoutMillis) {
        super(requestTimeoutMillis, rpc, id, rollbackSupport);
    }

    @Override
    protected synchronized void init() {
        LOG.trace("{}: Initializing {} transaction", id, getClass().getSimpleName());

        try {
            lock();
        } catch (final NetconfDocumentedException e) {
            try {
                LOG.warn("{}: Failed to lock candidate, attempting discard changes", id);
                discardChanges();
                LOG.warn("{}: Changes discarded successfully, attempting lock", id);
                lock();
            } catch (final NetconfDocumentedException secondE) {
                LOG.error("{}: Failed to prepare candidate. Failed to initialize transaction", id, secondE);
                throw new RuntimeException(id + ": Failed to prepare candidate. Failed to initialize transaction", secondE);
            }
        }
    }

    private void lock() throws NetconfDocumentedException {
        invoke("Lock candidate", new Function<NetconfBaseOps, ListenableFuture<DOMRpcResult>>() {
            @Override
            public ListenableFuture<DOMRpcResult> apply(final NetconfBaseOps input) {
                return input.lockCandidate(new FutureCallback<DOMRpcResult>() {
                    @Override
                    public void onSuccess(DOMRpcResult result) {
                        if (result.getErrors().isEmpty()) {
                            if (LOG.isTraceEnabled()) {
                                LOG.trace("Lock candidate succesfull");
                            }
                        } else {
                            LOG.warn("{}: lock candidate invoked unsuccessfully: {}", id, result.getErrors());
                        }
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        LOG.warn("Lock candidate operation failed. {}", t);
                        finished = true;
                        throw new RuntimeException(id + ": Failed to lock candidate datastore", t);
                    }
                });
            }
        });
    }

    @Override
    protected void cleanup() {
        discardChanges();
        cleanupOnSuccess();
    }

    @Override
    protected void handleEditException(final YangInstanceIdentifier path, final NormalizedNode<?, ?> data, final NetconfDocumentedException e, final String editType) {
        LOG.warn("{}: Error {} data to (candidate){}, data: {}, canceling", id, editType, path, data, e);
        cancel();
        throw new RuntimeException(id + ": Error while " + editType + ": (candidate)" + path, e);
    }

    @Override
    protected void handleDeleteException(final YangInstanceIdentifier path, final NetconfDocumentedException e) {
        LOG.warn("{}: Error deleting data (candidate){}, canceling", id, path, e);
        cancel();
        throw new RuntimeException(id + ": Error while deleting (candidate)" + path, e);
    }

    @Override
    public synchronized CheckedFuture<Void, TransactionCommitFailedException> submit() {
        final ListenableFuture<Void> commitFutureAsVoid = Futures.transform(commit(), new Function<RpcResult<TransactionStatus>, Void>() {
            @Override
            public Void apply(final RpcResult<TransactionStatus> input) {
                Preconditions.checkArgument(input.isSuccessful() && input.getErrors().isEmpty(), "Submit failed with errors: %s", input.getErrors());
                return null;
            }
        });

        return Futures.makeChecked(commitFutureAsVoid, new Function<Exception, TransactionCommitFailedException>() {
            @Override
            public TransactionCommitFailedException apply(final Exception input) {
                return new TransactionCommitFailedException("Submit of transaction " + getIdentifier() + " failed", input);
            }
        });
    }

    /**
     * This has to be non blocking since it is called from a callback on commit and its netty threadpool that is really sensitive to blocking calls
     */
    private void discardChanges() {
        netOps.discardChanges(new NetconfRpcFutureCallback("Discarding candidate", id));
    }

    @Override
    public synchronized ListenableFuture<RpcResult<TransactionStatus>> performCommit() {
        final ListenableFuture<DOMRpcResult> rpcResult = netOps.commit(new NetconfRpcFutureCallback("Commit", id) {
            @Override
            public void onSuccess(final DOMRpcResult result) {
                super.onSuccess(result);
                LOG.debug("{}: Write successful, transaction: {}. Unlocking", id, getIdentifier());
                cleanupOnSuccess();
            }

            @Override
            protected void onUnsuccess(final DOMRpcResult result) {
                LOG.error("{}: Write failed, transaction {}, discarding changes, unlocking: {}", id, getIdentifier(), result.getErrors());
                cleanup();
            }

            @Override
            public void onFailure(final Throwable t) {
                LOG.error("{}: Write failed, transaction {}, discarding changes, unlocking", id, getIdentifier(), t);
                cleanup();
            }
        });

        return Futures.transform(rpcResult, RPC_RESULT_TO_TX_STATUS);
    }

    protected void cleanupOnSuccess() {
        unlock();
    }

    @Override
    protected void editConfig(final DataContainerChild<?, ?> editStructure, final Optional<ModifyAction> defaultOperation) throws NetconfDocumentedException {
        invoke("Edit candidate", new Function<NetconfBaseOps, ListenableFuture<DOMRpcResult>>() {
            @Override
            public ListenableFuture<DOMRpcResult> apply(final NetconfBaseOps input) {
                FutureCallback<DOMRpcResult> editConfigFuture = new FutureCallback<DOMRpcResult>() {
                    @Override
                    public void onSuccess(DOMRpcResult result) {
                        if (result.getErrors().isEmpty()) {
                            if (LOG.isTraceEnabled()) {
                                LOG.trace("Edit candidate succesfull");
                            }
                        } else {
                            LOG.warn("{}: Edit candidate invoked unsuccessfully: {}", id, result.getErrors());
                        }
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        LOG.warn("Edit candidate operation failed. {}", t);
                        finished = true;
                        throw new RuntimeException(id + ": Failed to edit candidate datastore", t);
                    }
                };
                return defaultOperation.isPresent()
                        ? input.editConfigCandidate(editConfigFuture, editStructure, defaultOperation.get(), rollbackSupport)
                        : input.editConfigCandidate(editConfigFuture, editStructure, rollbackSupport);
                }
        });
    }

    /**
     * This has to be non blocking since it is called from a callback on commit and its netty threadpool that is really sensitive to blocking calls
     */
    private void unlock() {
        invoke("Unlocking candidate", new Function<NetconfBaseOps, ListenableFuture<DOMRpcResult>>() {
            @Override
            public ListenableFuture<DOMRpcResult> apply(final NetconfBaseOps input) {
                return input.unlockCandidate(new FutureCallback<DOMRpcResult>() {
                    @Override
                    public void onSuccess(DOMRpcResult result) {
                        if (result.getErrors().isEmpty()) {
                            if (LOG.isTraceEnabled()) {
                                LOG.trace("Unlock succesfull");
                            }
                        } else {
                            LOG.warn("{}: Unlock candidate invoked unsuccessfully: {}", id, result.getErrors());
                        }
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        LOG.warn("Unlock candidate operation failed. {}", t);
                        finished = true;
                        throw new RuntimeException(id + ": Failed to unlock candidate datastore", t);
                    }
                });
            }
        });
    }

}
