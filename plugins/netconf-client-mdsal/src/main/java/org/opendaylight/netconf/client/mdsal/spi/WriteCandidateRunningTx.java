/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.spi;

import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.impl.NetconfBaseOps;
import org.opendaylight.netconf.client.mdsal.impl.NetconfRpcFutureCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tx implementation for netconf devices that support only candidate datastore and writable running.
 * The sequence goes exactly as with only candidate supported, with one addition:
 * <ul>
 *     <li>Running datastore is locked as the first thing and this lock has to succeed</li>
 * </ul>
 */
class WriteCandidateRunningTx extends WriteCandidateTx {
    private static final Logger LOG  = LoggerFactory.getLogger(WriteCandidateRunningTx.class);

    WriteCandidateRunningTx(final RemoteDeviceId id, final NetconfBaseOps netOps, final boolean rollbackSupport) {
        this(id, netOps, rollbackSupport, true);
    }

    WriteCandidateRunningTx(final RemoteDeviceId id, final NetconfBaseOps netconfOps, final boolean rollbackSupport,
            final boolean isLockAllowed) {
        super(id, netconfOps, rollbackSupport, isLockAllowed);
    }

    @Override
    synchronized void init() {
        lockRunning();
        super.init();
    }

    @Override
    void cleanupOnSuccess() {
        super.cleanupOnSuccess();
        unlockRunning();
    }

    private void lockRunning() {
        if (isLockAllowed) {
            resultsFutures.add(netOps.lockRunning(new NetconfRpcFutureCallback("Lock running", id)));
        } else {
            LOG.trace("Lock is not allowed: {}", id);
        }
    }

    /**
     * This has to be non blocking since it is called from a callback on commit
     * and its netty threadpool that is really sensitive to blocking calls.
     */
    private void unlockRunning() {
        if (isLockAllowed) {
            netOps.unlockRunning(new NetconfRpcFutureCallback("Unlock running", id));
        } else {
            LOG.trace("Unlock is not allowed: {}", id);
        }
    }
}
