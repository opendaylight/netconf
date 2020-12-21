/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.sal.tx;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.api.ModifyAction;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfBaseOps;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfRpcFutureCallback;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tx implementation for netconf devices that support only writable-running with no candidate.
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
    private static final Logger LOG = LoggerFactory.getLogger(WriteRunningTx.class);
    private final List<Change> changes = new ArrayList<>();
    //Our backup of "running" database
    private volatile SettableFuture<Optional<NormalizedNode<?, ?>>> runningDatastore;

    public WriteRunningTx(final RemoteDeviceId id, final NetconfBaseOps netOps,
                          final boolean rollbackSupport) {
        this(id, netOps, rollbackSupport, true);
    }

    public WriteRunningTx(final RemoteDeviceId id, final NetconfBaseOps netconfOps, final boolean rollbackSupport,
            final boolean isLockAllowed) {
        super(id, netconfOps, rollbackSupport, isLockAllowed);
    }

    @Override
    protected synchronized void init() {
        lock();
    }

    private void lock() {
        if (!isLockAllowed) {
            LOG.trace("Lock is not allowed: {}", id);
            createBackUp();
            lock.setFuture(Futures.immediateFuture(new DefaultDOMRpcResult()));
            return;
        }
        final FutureCallback<DOMRpcResult> lockRunningCallback = new FutureCallback<>() {
            @Override
            public void onSuccess(final DOMRpcResult result) {
                if (isSuccess(result)) {
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("Lock running successful");
                    }
                    createBackUp();
                } else {
                    LOG.warn("{}: lock running invoked unsuccessfully: {}", id, result.getErrors());
                }
                lock.set(result);
            }

            @Override
            public void onFailure(final Throwable throwable) {
                LOG.warn("Lock running operation failed", throwable);
                lock.setException(throwable);
            }
        };
        netOps.lockRunning(lockRunningCallback);
        resultsFutures.add(lock);
    }

    private void createBackUp() {
        final ListenableFuture<Optional<NormalizedNode<?, ?>>> backupData = netOps.getConfigRunningData(
            new NetconfRpcFutureCallback("Backup data", id),
            Optional.of(YangInstanceIdentifier.empty()));
        runningDatastore = SettableFuture.create();
        runningDatastore.setFuture(backupData);
    }

    @Override
    protected void cleanup() {
        if (runningDatastore != null) {
            final YangInstanceIdentifier path = YangInstanceIdentifier.empty();
            Futures.addCallback(runningDatastore, new FutureCallback<>() {
                @Override
                public void onSuccess(Optional<NormalizedNode<?, ?>> result) {
                    if (result.isPresent()) {
                        try {
                            final NormalizedNode<?, Collection<NormalizedNode<?, ?>>> dataStore =
                                (NormalizedNode<?, Collection<NormalizedNode<?, ?>>>) result.get();
                            Map<YangInstanceIdentifier, NormalizedNode<?, ?>> map = new HashMap<>();
                            for (NormalizedNode<?, ?> node : dataStore.getValue()) {
                                map.put(path.node(node.getIdentifier()), node);
                            }
                            netOps.copyConfigToRunning(new NetconfRpcFutureCallback("Copy config", id), map);
                        } catch (ClassCastException ex) {
                            LOG.error("Failing discard changes", ex);
                        }
                    }
                    unlock();
                }

                @Override
                public void onFailure(Throwable throwable) {
                    LOG.error("Failing discard changes", throwable);
                    unlock();
                }
            }, MoreExecutors.directExecutor());
        } else {
            unlock();
        }
    }

    @Override
    public synchronized ListenableFuture<RpcResult<Void>> performCommit() {
        final SettableFuture<RpcResult<Void>> commitResult = SettableFuture.create();
        Futures.addCallback(lock, new FutureCallback<DOMRpcResult>() {
            @Override
            public void onSuccess(final DOMRpcResult result) {
                if (isSuccess(result)) {
                    for (final Change change : changes) {
                        resultsFutures.add(change.execute(id, netOps, rollbackSupport));
                    }
                }
                commitResult.setFuture(resultsToTxStatus());
                Futures.addCallback(commitResult, new FutureCallback<>() {
                    @Override
                    public void onSuccess(RpcResult<Void> result) {
                        unlock();
                    }

                    @Override
                    public void onFailure(Throwable throwable) {
                        cleanup();
                    }
                }, MoreExecutors.directExecutor());
            }

            @Override
            public void onFailure(Throwable throwable) {
                commitResult.setException(throwable);
                cleanup();
            }
        }, MoreExecutors.directExecutor());
        return commitResult;
    }

    @Override
    protected void editConfig(final YangInstanceIdentifier path,
                              final Optional<NormalizedNode<?, ?>> data,
                              final DataContainerChild<?, ?> editStructure,
                              final Optional<ModifyAction> defaultOperation,
                              final String operation) {
        changes.add(new Change(editStructure, defaultOperation));
    }

    private void unlock() {
        Futures.addCallback(lock, new NetconfRpcFutureCallback("Check datastore lock", id) {
            @Override
            public void onSuccess(final DOMRpcResult result) {
                if (isSuccess(result)) {
                    if (runningDatastore != null) {
                        runningDatastore.cancel(true);
                        runningDatastore = null;
                    }
                    if (isLockAllowed) {
                        netOps.unlockRunning(new NetconfRpcFutureCallback("Unlock running", id));
                    } else {
                        LOG.trace("Unlock is not allowed: {}", id);
                    }
                }
            }
        }, MoreExecutors.directExecutor());
    }

    private static final class Change {
        private final DataContainerChild<?, ?> editStructure;
        private final Optional<ModifyAction> defaultOperation;

        Change(final DataContainerChild<?, ?> editStructure, final Optional<ModifyAction> defaultOperation) {
            this.editStructure = editStructure;
            this.defaultOperation = defaultOperation;
        }

        ListenableFuture<? extends DOMRpcResult> execute(final RemoteDeviceId id, final NetconfBaseOps netOps,
                                                         final boolean rollbackSupport) {
            final NetconfRpcFutureCallback editConfigCallback = new NetconfRpcFutureCallback("Edit running", id);
            if (defaultOperation.isPresent()) {
                return netOps.editConfigRunning(editConfigCallback, editStructure, defaultOperation.get(),
                    rollbackSupport);
            } else {
                return netOps.editConfigRunning(editConfigCallback, editStructure, rollbackSupport);
            }
        }
    }
}
