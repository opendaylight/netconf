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
import com.google.common.util.concurrent.MoreExecutors;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
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
        super.init();
        Futures.addCallback(lock, new NetconfRpcFutureCallback("Check datastore lock", id) {
            @Override
            public void onSuccess(final DOMRpcResult result) {
                if (isSuccess(result)) {
                    lockRunning();
                }
            }
        }, MoreExecutors.directExecutor());
    }

    @Override
    protected void cleanupOnSuccess() {
        super.cleanupOnSuccess();
        unlockRunning();
    }

    @SuppressFBWarnings(value = "UPM_UNCALLED_PRIVATE_METHOD",
        justification = "https://github.com/spotbugs/spotbugs/issues/811")
    private void lockRunning() {
        if (isLockAllowed) {
            final FutureCallback<DOMRpcResult> lockRunningCallback = new FutureCallback<>() {
                @Override
                public void onSuccess(final DOMRpcResult result) {
                    if (isSuccess(result)) {
                        if (LOG.isTraceEnabled()) {
                            LOG.trace("Lock running successful");
                        }
                    } else {
                        LOG.warn("{}: lock running invoked unsuccessfully: {}", id, result.getErrors());
                        if (isLockAllowed) {
                            netOps.unlockCandidate(new NetconfRpcFutureCallback("Unlock candidate", id));
                        }
                        resultsFutures.add(lock);
                    }
                }

                @Override
                public void onFailure(final Throwable throwable) {
                    LOG.warn("Lock running operation failed", throwable);
                    if (isLockAllowed) {
                        netOps.unlockCandidate(new NetconfRpcFutureCallback("Unlock candidate", id));
                    }
                    resultsFutures.add(lock);
                }
            };
            lock = netOps.lockRunning(lockRunningCallback);
        } else {
            LOG.trace("Lock is not allowed: {}", id);
        }
    }

    /**
     * This has to be non blocking since it is called from a callback on commit
     * and its netty threadpool that is really sensitive to blocking calls.
     */
    private void unlockRunning() {
        Futures.addCallback(lock, new NetconfRpcFutureCallback("Check datastore lock", id) {
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
