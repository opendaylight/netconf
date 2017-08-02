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
import javax.annotation.Nullable;
import org.opendaylight.controller.md.sal.common.api.TransactionStatus;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcResult;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfBaseOps;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfRpcFutureCallback;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.data.api.ModifyAction;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tx implementation for netconf devices that support only candidate datastore and no writable running.
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
 *       <li>If any issue occurs during edit,
 *       datastore is discarded using discard-changes rpc, unlocked and an exception is thrown async</li>
 *     </ul>
 *   </li>
 *   <li>Commit and Unlock candidate datastore async</li>
 * </ol>
 */
public class WriteCandidateTx extends AbstractWriteTx {

    private static final Logger LOG  = LoggerFactory.getLogger(WriteCandidateTx.class);

    public WriteCandidateTx(final RemoteDeviceId id, final NetconfBaseOps rpc, final boolean rollbackSupport) {
        super(rpc, id, rollbackSupport);
    }

    @Override
    protected synchronized void init() {
        LOG.trace("{}: Initializing {} transaction", id, getClass().getSimpleName());
        lock();
    }

    private void lock() {
        final FutureCallback<DOMRpcResult> lockCandidateCallback = new FutureCallback<DOMRpcResult>() {
            @Override
            public void onSuccess(final DOMRpcResult result) {
                if (isSuccess(result)) {
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("Lock candidate successful");
                    }
                } else {
                    LOG.warn("{}: lock candidate invoked unsuccessfully: {}", id, result.getErrors());
                }
            }

            @Override
            public void onFailure(final Throwable throwable) {
                LOG.warn("Lock candidate operation failed. {}", throwable);
                discardChanges();
            }
        };
        resultsFutures.add(netOps.lockCandidate(lockCandidateCallback));
    }

    @Override
    protected void cleanup() {
        discardChanges();
        cleanupOnSuccess();
    }

    @Override
    public synchronized CheckedFuture<Void, TransactionCommitFailedException> submit() {
        final ListenableFuture<Void> commitFutureAsVoid = Futures.transform(commit(),
                input -> {
                    Preconditions.checkArgument(input.isSuccessful() && input.getErrors().isEmpty(),
                            "Submit failed with errors: %s", input.getErrors());
                    return null;
                });

        return Futures.makeChecked(commitFutureAsVoid, input -> new TransactionCommitFailedException(
                "Submit of transaction " + getIdentifier() + " failed", input));
    }

    /**
     * This has to be non blocking since it is called from a callback on commit
     * and its netty threadpool that is really sensitive to blocking calls.
     */
    private void discardChanges() {
        netOps.discardChanges(new NetconfRpcFutureCallback("Discarding candidate", id));
    }

    @Override
    public synchronized ListenableFuture<RpcResult<TransactionStatus>> performCommit() {
        resultsFutures.add(netOps.commit(new NetconfRpcFutureCallback("Commit", id)));
        final ListenableFuture<RpcResult<TransactionStatus>> txResult = resultsToTxStatus();

        Futures.addCallback(txResult, new FutureCallback<RpcResult<TransactionStatus>>() {
            @Override
            public void onSuccess(@Nullable final RpcResult<TransactionStatus> result) {
                cleanupOnSuccess();
            }

            @Override
            public void onFailure(final Throwable throwable) {
                // TODO If lock is cause of this failure cleanup will issue warning log
                // cleanup is trying to do unlock, but this will fail
                cleanup();
            }
        });

        return txResult;
    }

    protected void cleanupOnSuccess() {
        unlock();
    }

    @Override
    protected void editConfig(final YangInstanceIdentifier path,
                              final Optional<NormalizedNode<?, ?>> data,
                              final DataContainerChild<?, ?> editStructure,
                              final Optional<ModifyAction> defaultOperation,
                              final String operation) {

        final NetconfRpcFutureCallback editConfigCallback = new NetconfRpcFutureCallback("Edit candidate", id);

        if (defaultOperation.isPresent()) {
            resultsFutures.add(netOps.editConfigCandidate(
                    editConfigCallback, editStructure, defaultOperation.get(), rollbackSupport));
        } else {
            resultsFutures.add(netOps.editConfigCandidate(editConfigCallback, editStructure, rollbackSupport));
        }
    }

    /**
     * This has to be non blocking since it is called from a callback on commit
     * and its netty threadpool that is really sensitive to blocking calls.
     */
    private void unlock() {
        netOps.unlockCandidate(new NetconfRpcFutureCallback("Unlock candidate", id));
    }

}
