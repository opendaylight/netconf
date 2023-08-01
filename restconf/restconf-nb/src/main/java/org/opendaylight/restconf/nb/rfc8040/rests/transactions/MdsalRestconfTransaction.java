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
import static org.opendaylight.restconf.nb.rfc8040.rests.utils.PostDataTransactionUtil.checkItemDoesNotExists;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.ReadFailedException;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.rests.utils.TransactionUtil;
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

    MdsalRestconfTransaction(final DOMDataBroker dataBroker) {
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
    public void delete(final YangInstanceIdentifier path) {
        if (!TransactionUtil.syncAccess(verifyNotNull(rwTx).exists(CONFIGURATION, path), path)) {
            LOG.trace("Operation via Restconf was not executed because data at {} does not exist", path);
            throw new RestconfDocumentedException("Data does not exist", ErrorType.PROTOCOL, ErrorTag.DATA_MISSING,
                path);
        }

        rwTx.delete(CONFIGURATION, path);
    }

    @Override
    public void remove(final YangInstanceIdentifier path) {
        verifyNotNull(rwTx).delete(CONFIGURATION, path);
    }

    @Override
    public void merge(final YangInstanceIdentifier path, final NormalizedNode data) {
        verifyNotNull(rwTx).merge(CONFIGURATION, path, data);
    }

    @Override
    public void create(final YangInstanceIdentifier path, final NormalizedNode data,
                       final EffectiveModelContext schemaContext) {
        if (data instanceof MapNode || data instanceof LeafSetNode) {
            final NormalizedNode emptySubTree = ImmutableNodes.fromInstanceId(schemaContext, path);
            merge(YangInstanceIdentifier.of(emptySubTree.name()), emptySubTree);
            TransactionUtil.ensureParentsByMerge(path, schemaContext, this);

            final Collection<? extends NormalizedNode> children = ((DistinctNodeContainer<?, ?>) data).body();
            final BatchedExistenceCheck check =
                BatchedExistenceCheck.start(verifyNotNull(rwTx), CONFIGURATION, path, children);

            for (final NormalizedNode child : children) {
                final YangInstanceIdentifier childPath = path.node(child.name());
                verifyNotNull(rwTx).put(CONFIGURATION, childPath, child);
            }
            // ... finally collect existence checks and abort the transaction if any of them failed.
            checkExistence(path, check);
        } else {
            checkItemDoesNotExists(verifyNotNull(rwTx).exists(CONFIGURATION, path), path);
            TransactionUtil.ensureParentsByMerge(path, schemaContext, this);
            verifyNotNull(rwTx).put(CONFIGURATION, path, data);
        }
    }

    @Override
    public void replace(final YangInstanceIdentifier path, final NormalizedNode data,
                        final EffectiveModelContext schemaContext) {
        if (data instanceof MapNode || data instanceof LeafSetNode) {
            final NormalizedNode emptySubtree = ImmutableNodes.fromInstanceId(schemaContext, path);
            merge(YangInstanceIdentifier.of(emptySubtree.name()), emptySubtree);
            TransactionUtil.ensureParentsByMerge(path, schemaContext, this);

            for (final NormalizedNode child : ((NormalizedNodeContainer<?>) data).body()) {
                final YangInstanceIdentifier childPath = path.node(child.name());
                verifyNotNull(rwTx).put(CONFIGURATION, childPath, child);
            }
        } else {
            TransactionUtil.ensureParentsByMerge(path, schemaContext, this);
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

    private static void checkExistence(final YangInstanceIdentifier path, final BatchedExistenceCheck check) {
        final Map.Entry<YangInstanceIdentifier, ReadFailedException> failure;
        try {
            failure = check.getFailure();
        } catch (InterruptedException e) {
            throw new RestconfDocumentedException("Could not determine the existence of path " + path, e);
        }

        if (failure != null) {
            final ReadFailedException e = failure.getValue();
            if (e == null) {
                throw new RestconfDocumentedException("Data already exists",
                    ErrorType.PROTOCOL, ErrorTag.DATA_EXISTS, failure.getKey());
            }

            throw new RestconfDocumentedException(
                "Could not determine the existence of path " + failure.getKey(), e, e.getErrorList());
        }
    }
}
