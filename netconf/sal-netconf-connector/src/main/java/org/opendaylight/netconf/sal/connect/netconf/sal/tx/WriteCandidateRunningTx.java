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
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfBaseOps;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfRpcFutureCallback;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tx implementation for netconf devices that support only candidate datastore and writable running.
 * The sequence goes exactly as with only candidate supported, with one addition:
 * <ul>
 *     <li>Running datastore is locked as the first thing and this lock has to succeed</li>
 * </ul>
 */
public class WriteCandidateRunningTx extends WriteCandidateTx {
    private static final Logger LOG  = LoggerFactory.getLogger(WriteCandidateRunningTx.class);
    private volatile ListenableFuture<? extends DOMRpcResult> lockRunning;

    public WriteCandidateRunningTx(final RemoteDeviceId id, final NetconfBaseOps netOps,
                                   final boolean rollbackSupport) {
        this(id, netOps, rollbackSupport, true);
    }

    public WriteCandidateRunningTx(RemoteDeviceId id, NetconfBaseOps netconfOps, boolean rollbackSupport,
            boolean isLockAllowed) {
        super(id, netconfOps, rollbackSupport, isLockAllowed);
    }

    @Override
    protected synchronized void init() {
        lockRunning = SettableFuture.create();
        lockRunning();
        Futures.addCallback(lockRunning, new FutureCallback<DOMRpcResult>() {
            @Override
            public void onSuccess(final DOMRpcResult result) {
                if (isSuccess(result)) {
                    lockCandidate();
                    Futures.addCallback(lock, new FutureCallback<>() {
                        @Override
                        public void onSuccess(final DOMRpcResult result) {
                            if (!isSuccess(result)) {
                                unlockRunning();
                            }
                        }

                        @Override
                        public void onFailure(Throwable throwable) {
                            unlockRunning();
                        }
                    }, MoreExecutors.directExecutor());
                } else {
                    lock.set(result);
                    resultsFutures.add(lock);
                }
            }

            @Override
            public void onFailure(Throwable throwable) {
                lock.setException(throwable);
                resultsFutures.add(lock);
            }
        }, MoreExecutors.directExecutor());
    }

    @Override
    protected void cleanupOnSuccess() {
        super.cleanupOnSuccess();
        unlockRunning();
    }

    private void lockRunning() {
        if (!isLockAllowed) {
            LOG.trace("Lock is not allowed: {}", id);
            lockRunning = Futures.immediateFuture(new DefaultDOMRpcResult());
            return;
        }
        final FutureCallback<DOMRpcResult> lockRunningCallback = new FutureCallback<>() {
            @Override
            public void onSuccess(final DOMRpcResult result) {
                if (isSuccess(result)) {
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("Lock running successful");
                    }
                } else {
                    LOG.warn("{}: lock running invoked unsuccessfully: {}", id, result.getErrors());
                }
            }

            @Override
            public void onFailure(final Throwable throwable) {
                LOG.warn("Lock running operation failed", throwable);
            }
        };
        lockRunning = netOps.lockRunning(lockRunningCallback);
        resultsFutures.add(lockRunning);
    }

    /**
     * This has to be non blocking since it is called from a callback on commit
     * and its netty threadpool that is really sensitive to blocking calls.
     */
    private void unlockRunning() {
        Futures.addCallback(lockRunning, new NetconfRpcFutureCallback("Check datastore lock", id) {
            @Override
            public void onSuccess(final DOMRpcResult result) {
                if (isSuccess(result)) {
                    if (isLockAllowed) {
                        netOps.unlockRunning(new NetconfRpcFutureCallback("Unlock running", id));
                    } else {
                        LOG.trace("Unlock is not allowed: {}", id);
                    }
                }
            }
        }, MoreExecutors.directExecutor());
    }
}
