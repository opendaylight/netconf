/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.connect.netconf.sal.tx;

import org.opendaylight.controller.md.sal.dom.api.DOMRpcResult;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfBaseOps;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;

/**
 * Tx implementation for netconf devices that support only candidate datastore and writable running
 * The sequence goes exactly as with only candidate supported, with one addition:
 * <ul>
 *     <li>Running datastore is locked as the first thing and this lock has to succeed</li>
 * </ul>
 */
public class WriteCandidateRunningTx extends WriteCandidateTx {

    private static final Logger LOG  = LoggerFactory.getLogger(WriteCandidateRunningTx.class);

    public WriteCandidateRunningTx(final RemoteDeviceId id, final NetconfBaseOps netOps, final boolean rollbackSupport, long requestTimeoutMillis) {
        super(id, netOps, rollbackSupport, requestTimeoutMillis);
    }

    @Override
    protected synchronized void init() {
        lockRunning();
        super.init();
    }

    @Override
    protected void cleanupOnSuccess() {
        super.cleanupOnSuccess();
        unlockRunning();
    }

    private void lockRunning() {
        invoke("Lock running", new Function<NetconfBaseOps, ListenableFuture<DOMRpcResult>>() {
            @Override
            public ListenableFuture<DOMRpcResult> apply(final NetconfBaseOps input) {
                return input.lockRunning(new FutureCallback<DOMRpcResult>() {
                    @Override
                    public void onSuccess(DOMRpcResult result) {
                        if (result.getErrors().isEmpty()) {
                            if (LOG.isTraceEnabled()) {
                                LOG.trace("Lock running succesfull");
                            }
                        } else {
                            LOG.warn("{}: lock running invoked unsuccessfully: {}", id, result.getErrors());
                        }
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        LOG.warn("{}: Failed to lock running. Failed to initialize transaction", id, t);
                        finished = true;
                        throw new RuntimeException(id + ": Failed to lock running. Failed to initialize transaction", t);
                    }
                });
            }
        });
    }

    /**
     * This has to be non blocking since it is called from a callback on commit and its netty threadpool that is really sensitive to blocking calls
     */
    private void unlockRunning() {
        invoke("Unlocking running", new Function<NetconfBaseOps, ListenableFuture<DOMRpcResult>>() {
            @Override
            public ListenableFuture<DOMRpcResult> apply(final NetconfBaseOps input) {
                return input.unlockRunning(new FutureCallback<DOMRpcResult>() {
                    @Override
                    public void onSuccess(DOMRpcResult result) {
                        if (result.getErrors().isEmpty()) {
                            if (LOG.isTraceEnabled()) {
                                LOG.trace("Unlock running succesfull");
                            }
                        } else {
                            LOG.warn("{}: Unlock running invoked unsuccessfully: {}", id, result.getErrors());
                        }
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        LOG.warn("Unlock running operation failed. {}", t);
                        finished = true;
                        throw new RuntimeException(id + ": Failed to unlock runing datastore", t);
                    }
                });
            }
        });
    }
}
