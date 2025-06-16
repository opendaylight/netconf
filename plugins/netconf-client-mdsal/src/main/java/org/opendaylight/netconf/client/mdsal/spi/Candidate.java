/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.spi;

import static org.opendaylight.netconf.api.EffectiveOperation.CREATE;
import static org.opendaylight.netconf.api.EffectiveOperation.DELETE;
import static org.opendaylight.netconf.api.EffectiveOperation.MERGE;
import static org.opendaylight.netconf.api.EffectiveOperation.REMOVE;
import static org.opendaylight.netconf.api.EffectiveOperation.REPLACE;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.Optional;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.api.EffectiveOperation;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.impl.NetconfBaseOps;
import org.opendaylight.netconf.client.mdsal.impl.NetconfRpcFutureCallback;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ChoiceNode;
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
    public ListenableFuture<? extends DOMRpcResult> cancel() {
        final var discardChanges = netconfOps.discardChanges(new NetconfRpcFutureCallback("Discard candidate", id));
        final var unlock = unlock();
        return Futures.whenAllComplete(discardChanges, unlock).call(() -> {
            final var unlockResult = Futures.getDone(unlock);
            final var discard = Futures.getDone(discardChanges);
            if (!discard.errors().isEmpty() && !allWarnings(discard.errors())) {
                return discard;
            }
            if (!unlockResult.errors().isEmpty() && !allWarnings(unlockResult.errors())) {
                return unlockResult;
            }
            return new DefaultDOMRpcResult();
        }, MoreExecutors.directExecutor());
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> create(YangInstanceIdentifier path, NormalizedNode data) {
        return editConfig(CREATE, data, path);
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> delete(YangInstanceIdentifier path) {
        return editConfig(DELETE, path);
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> remove(YangInstanceIdentifier path) {
        return editConfig(REMOVE, path);
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> merge(YangInstanceIdentifier path, NormalizedNode data) {
        return editConfig(MERGE, data, path);
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> put(YangInstanceIdentifier path, NormalizedNode data) {
        return editConfig(REPLACE, data, path);
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> commit() {
        final var commit = netconfOps.commit(new NetconfRpcFutureCallback("Commit", id));
        final var commitAndUnlock = addUnlock(commit);
        return addCancelIfFails(commitAndUnlock);
    }

    public ListenableFuture<? extends DOMRpcResult> editConfig(final EffectiveOperation operation,
            final NormalizedNode child, final YangInstanceIdentifier path) {
        final var netconfOpsChild = netconfOps.createEditConfigStructure(Optional.of(child), Optional.of(operation),
            path);
        return editConfig(netconfOpsChild);
    }

    public ListenableFuture<? extends DOMRpcResult> editConfig(final EffectiveOperation operation,
            final YangInstanceIdentifier path) {
        final var netconfOpsChild = netconfOps.createEditConfigStructure(Optional.empty(),
            Optional.of(operation), path);
        return editConfig(netconfOpsChild);
    }

    public ListenableFuture<? extends DOMRpcResult> editConfig(final ChoiceNode node) {
        final NetconfRpcFutureCallback callback = new NetconfRpcFutureCallback("Edit candidate", id);
        // FIXME: Set default-operation" parameter based on called DataStoreService method.
        //        https://www.rfc-editor.org/rfc/rfc6241#section-7.2
        final var edit = addIntoFutureChain(lock(), () -> netconfOps.editConfigCandidate(callback, node,
                rollbackSupport));
        return addCancelIfFails(edit);
    }
}
