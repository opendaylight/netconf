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
        try {
            final String operation = "lock running";

            ListenableFuture<DOMRpcResult> lockFuture = netOps.lockRunning(new NetconfRpcFutureCallback(operation, id));
            listenableFutureToCheckedFuture(lockFuture, operation).checkedGet();
        } catch (NetconfDocumentedException e) {
            throw new RuntimeException(e);
        }
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
    protected void handleDeleteException(final YangInstanceIdentifier path, final NetconfDocumentedException e) {
        LOG.warn("{}: Error deleting data (running){}, canceling", id, path, e);
        cancel();
        throw new RuntimeException(id + ": Error while deleting (running)" + path, e);
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
                              final String operationType) throws NetconfDocumentedException {

        final String operation = "edit running - " + operationType;

        ListenableFuture<DOMRpcResult> editConfigFuture = null;
        if (defaultOperation.isPresent()) {
            editConfigFuture  = netOps.editConfigRunning(new NetconfRpcFutureCallback(operation, id), editStructure, defaultOperation.get(), rollbackSupport);
        } else {
            editConfigFuture = netOps.editConfigRunning(new NetconfRpcFutureCallback(operation, id), editStructure, rollbackSupport);
        }

        listenableFutureToCheckedFuture(editConfigFuture, operation).checkedGet();
    }

    private void unlock() {
        netOps.unlockRunning(new NetconfRpcFutureCallback("Unlock running", id));
    }
}
