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
import org.opendaylight.netconf.api.NetconfDocumentedException;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.impl.NetconfBaseOps;
import org.opendaylight.netconf.client.mdsal.impl.NetconfRpcFutureCallback;
import org.opendaylight.yangtools.yang.common.ErrorSeverity;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ChoiceNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Candidate extends AbstractDataStore {
    private static final Logger LOG = LoggerFactory.getLogger(Candidate.class);

    Candidate(final NetconfBaseOps netconfOps, final RemoteDeviceId id, final boolean rollbackSupport,
            final boolean lockDatastore) {
        super(netconfOps, id, rollbackSupport, lockDatastore);
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> create(final YangInstanceIdentifier path,
            final NormalizedNode data) {
        return editConfig(CREATE, data, path);
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> delete(final YangInstanceIdentifier path) {
        return editConfig(DELETE, path);
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> remove(final YangInstanceIdentifier path) {
        return editConfig(REMOVE, path);
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> merge(final YangInstanceIdentifier path,
            final NormalizedNode data) {
        return editConfig(MERGE, data, path);
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> replace(final YangInstanceIdentifier path,
            final NormalizedNode data) {
        return editConfig(REPLACE, data, path);
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> commit() {
        if (!lock.get()) {
            LOG.error("Can not perform commit when lock is released on device {}", id);
            final var documentedException = new NetconfDocumentedException(
                "Can not perform commit when lock is released", ErrorType.APPLICATION, ErrorTag.OPERATION_FAILED,
                ErrorSeverity.ERROR);
            return Futures.immediateFailedFuture(documentedException);
        }
        LOG.trace("Execute commit on device {}", id);
        final var commit = netconfOps.commit(new NetconfRpcFutureCallback("Commit", id));
        final var commitAndUnlock = addUnlock(commit);
        return addCancelIfFails(commitAndUnlock);
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> cancel() {
        LOG.trace("Execute cancel on device {}", id);
        final var discardChanges = netconfOps.discardChanges(new NetconfRpcFutureCallback("Discard candidate", id));
        final var unlock = unlock();
        return Futures.whenAllComplete(discardChanges, unlock).call(() -> {
            final var discard = Futures.getDone(discardChanges);
            if (discard != null && !discard.errors().isEmpty() && noWarnings(discard.errors())) {
                LOG.warn("Discard operation fails with errors {}. Result ignored", discard.errors());
            }
            final var unlockResult = Futures.getDone(unlock);
            if (unlockResult != null && !unlockResult.errors().isEmpty() && noWarnings(unlockResult.errors())) {
                LOG.warn("Unlock operation fails with errors {}. Result ignored", unlockResult.errors());
            }
            return new DefaultDOMRpcResult();
        }, MoreExecutors.directExecutor());
    }

    @Override
    ListenableFuture<? extends DOMRpcResult> unlock() {
        if (lockDatastore && lock.getAndSet(false)) {
            LOG.trace("Execute unlock on device {}", id);
            return netconfOps.unlockCandidate(new NetconfRpcFutureCallback("Unlock candidate", id));
        }
        return RPC_SUCCESS;
    }

    @Override
    ListenableFuture<? extends DOMRpcResult> lock() {
        if (lockDatastore && !lock.getAndSet(true)) {
            LOG.trace("Execute lock operation on device {}", id);
            return netconfOps.lockCandidate(new NetconfRpcFutureCallback("Lock candidate", id));
        }
        return RPC_SUCCESS;
    }

    private ListenableFuture<? extends DOMRpcResult> editConfig(final EffectiveOperation operation,
            final NormalizedNode child, final YangInstanceIdentifier path) {
        final ChoiceNode netconfOpsChild;
        try {
            netconfOpsChild = netconfOps.createEditConfigStructure(Optional.of(child), Optional.of(operation), path);
        } catch (IllegalArgumentException | IllegalStateException cause) {
            LOG.error("Failed to create edit-config structure node on device {} for child {} with path {}, with RPC"
                    + " operation {}", id, child, path, operation);
            final var documentedException = new NetconfDocumentedException(
                "Failed to create edit-config structure node for RPC operation", cause, ErrorType.APPLICATION,
                ErrorTag.OPERATION_FAILED, ErrorSeverity.ERROR);
            return Futures.immediateFailedFuture(documentedException);
        }
        LOG.trace("Execute editConfig operation {} on device {} with data {} and path {}", id, operation, child, path);
        return editConfig(netconfOpsChild);
    }

    private ListenableFuture<? extends DOMRpcResult> editConfig(final EffectiveOperation operation,
            final YangInstanceIdentifier path) {
        final ChoiceNode netconfOpsChild;
        try {
            netconfOpsChild = netconfOps.createEditConfigStructure(Optional.empty(), Optional.of(operation), path);
        } catch (IllegalArgumentException | IllegalStateException cause) {
            LOG.error("Failed to create edit-config structure node on device {} with path {}, and RPC operation {}",
                id, path, operation);
            final var documentedException = new NetconfDocumentedException(
                "Failed to create edit-config structure node for RPC operation", cause, ErrorType.APPLICATION,
                ErrorTag.OPERATION_FAILED, ErrorSeverity.ERROR);
            return Futures.immediateFailedFuture(documentedException);
        }
        LOG.trace("Execute editConfig operation {} on device {} with path {}", id, operation, path);
        return editConfig(netconfOpsChild);
    }

    private ListenableFuture<? extends DOMRpcResult> editConfig(final ChoiceNode node) {
        final NetconfRpcFutureCallback callback = new NetconfRpcFutureCallback("Edit candidate", id);
        // FIXME: Set default-operation" parameter based on called DataStoreService method.
        //        https://www.rfc-editor.org/rfc/rfc6241#section-7.2
        final var edit = addLockBeforeFuture(() -> netconfOps.editConfigCandidate(callback, node, rollbackSupport));
        return addCancelIfFails(edit);
    }
}
