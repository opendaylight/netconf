/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.spi;

import com.google.common.util.concurrent.ListenableFuture;
import javax.xml.transform.dom.DOMSource;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.netconf.api.EffectiveOperation;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.impl.NetconfBaseOps;
import org.opendaylight.netconf.client.mdsal.impl.NetconfRpcFutureCallback;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.AnyxmlNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Candidate extends AbstractDataStore {
    private static final Logger LOG = LoggerFactory.getLogger(Candidate.class);

    Candidate(NetconfBaseOps netconfOps, final RemoteDeviceId id,
            boolean rollbackSupport, boolean lockDatastore) {
        super(netconfOps, id, rollbackSupport, lockDatastore);
    }

    ListenableFuture<? extends DOMRpcResult> lock() {
        if (lockDatastore && !lock.getAndSet(true)) {
            return netconfOps.lockCandidate(new NetconfRpcFutureCallback("Lock candidate", id));
        }
        return RPC_SUCCESS;
    }

    ListenableFuture<? extends DOMRpcResult> unlock() {
        if (lockDatastore && lock.getAndSet(false)) {
            return netconfOps.unlockCandidate(new NetconfRpcFutureCallback("Unlock candidate", id));
        }
        return RPC_SUCCESS;
    }

    @Override
    public void cancel() {
        readListCache.clear();
        executeWithLogging(() -> netconfOps.discardChanges(new NetconfRpcFutureCallback("Discard candidate", id)));
        executeWithLogging(this::unlock);
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> commit() {
        final var commit = netconfOps.commit(new NetconfRpcFutureCallback("Commit", id));
        addCallback(commit, true);
        return commit;
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> editConfig(final EffectiveOperation operation,
            final NormalizedNode child, final YangInstanceIdentifier path) {
        final var netconfOpsChild = netconfOps.createNode(child, operation, path);
        return editConfig(netconfOpsChild);
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> editConfig(final EffectiveOperation operation,
            final YangInstanceIdentifier path) {
        final var netconfOpsChild = netconfOps.createNode(operation, path);
        return editConfig(netconfOpsChild);
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> editConfig(final AnyxmlNode<DOMSource> node) {
        final NetconfRpcFutureCallback callback = new NetconfRpcFutureCallback("Edit candidate", id);
        return addIntoFutureChain(lock(), () -> netconfOps.editConfigCandidate(callback, node, rollbackSupport), false);
    }
}
