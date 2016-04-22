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
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.opendaylight.yangtools.yang.data.api.ModifyAction;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tx implementation for netconf devices that support only writable-running with no candidate
 * The sequence goes as:
 * <ol>
 *   <li>Lock running datastore on tx construction
 *     <ul>
 *       <li> Lock has to succeed, if it does not, transaction is failed</li>
 *     </ul>
 *   </li>
 *   <li>Edit-config in running N times
 *     <ul>
 *       <li>If any issue occurs during edit, datastore is unlocked and an exception is thrown</li>
 *     </ul>
 *   </li>
 *   <li>Unlock running datastore on tx commit</li>
 * </ol>
 */
public class WriteRunningTx extends AbstractWriteTx {

    private static final Logger LOG  = LoggerFactory.getLogger(WriteRunningTx.class);

    public WriteRunningTx(final RemoteDeviceId id, final NetconfBaseOps netOps,
                          final boolean rollbackSupport) {
        super(netOps, id, rollbackSupport);
    }

    @Override
    protected synchronized void init() {
        lock();
    }

    private void lock() {
        final FutureCallback<DOMRpcResult> lockCallback = new FutureCallback<DOMRpcResult>() {
            @Override
            public void onSuccess(DOMRpcResult result) {
                if (isSuccess(result)) {
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("Lock running succesfull");
                    }
                } else {
                    LOG.warn("{}: lock running invoked unsuccessfully: {}", id, result.getErrors());
                }
            }

            @Override
            public void onFailure(Throwable t) {
                LOG.warn("Lock running operation failed. {}", t);
                throw new RuntimeException(id + ": Failed to lock running datastore", t);
            }
        };
        netOps.lockRunning(lockCallback);
    }

    @Override
    protected void cleanup() {
        unlock();
    }

    @Override
    protected void handleEditException(final YangInstanceIdentifier path, final NormalizedNode<?, ?> data, final NetconfDocumentedException e, final String editType) {
        LOG.warn("{}: Error {} data to (running){}, data: {}, canceling", id, editType, path, data, e);
        cancel();
        throw new RuntimeException(id + ": Error while " + editType + ": (running)" + path, e);
    }

    @Override
    public synchronized CheckedFuture<Void, TransactionCommitFailedException> submit() {
        final ListenableFuture<Void> commmitFutureAsVoid = Futures.transform(commit(), new Function<RpcResult<TransactionStatus>, Void>() {
            @Override
            public Void apply(final RpcResult<TransactionStatus> input) {
                return null;
            }
        });

        return Futures.makeChecked(commmitFutureAsVoid, new Function<Exception, TransactionCommitFailedException>() {
            @Override
            public TransactionCommitFailedException apply(final Exception input) {
                return new TransactionCommitFailedException("Submit of transaction " + getIdentifier() + " failed", input);
            }
        });
    }

    @Override
    public synchronized ListenableFuture<RpcResult<TransactionStatus>> performCommit() {
        unlock();
        return Futures.immediateFuture(RpcResultBuilder.success(TransactionStatus.COMMITED).build());
    }

    @Override
    protected void editConfig(final YangInstanceIdentifier path,
                              final Optional<NormalizedNode<?, ?>> data,
                              final DataContainerChild<?, ?> editStructure,
                              final Optional<ModifyAction> defaultOperation,
                              final String operation) {
        FutureCallback<DOMRpcResult> editConfigCallback = new FutureCallback<DOMRpcResult>() {
            @Override
            public void onSuccess(DOMRpcResult result) {
                if (isSuccess(result)) {
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("Edit running succesfull");
                    }
                } else {
                    LOG.warn("{}: Edit running invoked unsuccessfully: {}", id, result.getErrors());
                }
            }

            @Override
            public void onFailure(Throwable t) {
                LOG.warn("Edit running operation failed. {}", t);
                NetconfDocumentedException e = new NetconfDocumentedException(id + ": Edit running operation failed.", NetconfDocumentedException.ErrorType.application,
                        NetconfDocumentedException.ErrorTag.operation_failed, NetconfDocumentedException.ErrorSeverity.warning);
                handleEditException(path, data.orNull(), e, operation);
            }
        };
        if (defaultOperation.isPresent()) {
            netOps.editConfigRunning(editConfigCallback, editStructure, defaultOperation.get(), rollbackSupport);
        } else {
            netOps.editConfigRunning(editConfigCallback, editStructure, rollbackSupport);
        }
    }

    private void unlock() {
        netOps.unlockRunning(new NetconfRpcFutureCallback("Unlock running", id));
    }
}
