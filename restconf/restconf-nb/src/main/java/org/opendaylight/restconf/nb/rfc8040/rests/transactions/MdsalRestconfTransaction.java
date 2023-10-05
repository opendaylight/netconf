/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.transactions;

import static com.google.common.base.Verify.verifyNotNull;
import static org.opendaylight.mdsal.common.api.LogicalDatastoreType.CONFIGURATION;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.ExistenceCheck.Conflict;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.ExistenceCheck.Result;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.DistinctNodeContainer;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNodeContainer;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class MdsalRestconfTransaction extends RestconfTransaction {
    private static final Logger LOG = LoggerFactory.getLogger(MdsalRestconfTransaction.class);

    private DOMDataTreeReadWriteTransaction rwTx;

    MdsalRestconfTransaction(final EffectiveModelContext modelContext, final DOMDataBroker dataBroker) {
        super(modelContext);
        rwTx = dataBroker.newReadWriteTransaction();
    }

    @Override
    public void cancel() {
        if (rwTx != null) {
            rwTx.cancel();
            rwTx = null;
        }
    }

    @Override
    void deleteImpl(final YangInstanceIdentifier path) {
        if (TransactionUtil.syncAccess(verifyNotNull(rwTx).exists(CONFIGURATION, path), path)) {
            rwTx.delete(CONFIGURATION, path);
        } else {
            LOG.trace("Operation via Restconf was not executed because data at {} does not exist", path);
            throw new RestconfDocumentedException("Data does not exist", ErrorType.PROTOCOL, ErrorTag.DATA_MISSING,
                path);
        }
    }

    @Override
    void removeImpl(final YangInstanceIdentifier path) {
        verifyNotNull(rwTx).delete(CONFIGURATION, path);
    }

    @Override
    void mergeImpl(final YangInstanceIdentifier path, final NormalizedNode data) {
        verifyNotNull(rwTx).merge(CONFIGURATION, path, data);
    }

    @Override
    void createImpl(final YangInstanceIdentifier path, final NormalizedNode data) {
        if (data instanceof MapNode || data instanceof LeafSetNode) {
            final var emptySubTree = ImmutableNodes.fromInstanceId(modelContext, path);
            merge(YangInstanceIdentifier.of(emptySubTree.name()), emptySubTree);
            ensureParentsByMerge(path);

            final var children = ((DistinctNodeContainer<?, ?>) data).body();
            final var check = ExistenceCheck.start(verifyNotNull(rwTx), CONFIGURATION, path, false, children);

            for (var child : children) {
                final var childPath = path.node(child.name());
                verifyNotNull(rwTx).put(CONFIGURATION, childPath, child);
            }
            // ... finally collect existence checks and abort the transaction if any of them failed.
            checkExistence(path, check);
        } else {
            RestconfStrategy.checkItemDoesNotExists(verifyNotNull(rwTx).exists(CONFIGURATION, path), path);
            ensureParentsByMerge(path);
            verifyNotNull(rwTx).put(CONFIGURATION, path, data);
        }
    }

    @Override
    void replaceImpl(final YangInstanceIdentifier path, final NormalizedNode data) {
        if (data instanceof MapNode || data instanceof LeafSetNode) {
            final var emptySubtree = ImmutableNodes.fromInstanceId(modelContext, path);
            merge(YangInstanceIdentifier.of(emptySubtree.name()), emptySubtree);
            ensureParentsByMerge(path);

            for (var child : ((NormalizedNodeContainer<?>) data).body()) {
                final var childPath = path.node(child.name());
                verifyNotNull(rwTx).put(CONFIGURATION, childPath, child);
            }
        } else {
            ensureParentsByMerge(path);
            verifyNotNull(rwTx).put(CONFIGURATION, path, data);
        }
    }

    @Override
    public ListenableFuture<? extends @NonNull CommitInfo> commit() {
        final var ret = verifyNotNull(rwTx).commit();
        rwTx = null;
        return ret;
    }

    @Override
    ListenableFuture<Optional<NormalizedNode>> read(final YangInstanceIdentifier path) {
        return verifyNotNull(rwTx).read(CONFIGURATION, path);
    }

    private static void checkExistence(final YangInstanceIdentifier path,
            final ListenableFuture<@NonNull Result> future) {
        final Result result;
        try {
            result = future.get();
        } catch (ExecutionException e) {
            // This should never happen
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            throw new RestconfDocumentedException("Could not determine the existence of path " + path, e);
        }

        if (result instanceof Conflict conflict) {
            final var cause = conflict.cause();
            if (cause == null) {
                throw new RestconfDocumentedException("Data already exists",
                    ErrorType.PROTOCOL, ErrorTag.DATA_EXISTS, conflict.path());
            }
            throw new RestconfDocumentedException("Could not determine the existence of path " + conflict.path(), cause,
                cause.getErrorList());
        }
    }
}
