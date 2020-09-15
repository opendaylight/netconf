/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.transactions;

import static java.util.Objects.requireNonNull;
import static org.opendaylight.restconf.nb.rfc8040.rests.utils.DeleteDataTransactionUtil.DELETE_TX_TYPE;
import static org.opendaylight.restconf.nb.rfc8040.rests.utils.PostDataTransactionUtil.checkItemDoesNotExists;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.common.api.ReadFailedException;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadTransaction;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.restconf.nb.rfc8040.handlers.TransactionChainHandler;
import org.opendaylight.restconf.nb.rfc8040.rests.utils.DeleteDataTransactionUtil;
import org.opendaylight.restconf.nb.rfc8040.rests.utils.TransactionUtil;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNodeContainer;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

/**
 * Implementation of RESTCONF operations using {@link DOMTransactionChain} and related concepts.
 *
 * @see DOMTransactionChain
 * @see DOMDataTreeReadWriteTransaction
 */
public final class MdsalRestconfStrategy extends RestconfStrategy {
    private final DOMTransactionChain transactionChain;
    private final TransactionChainHandler transactionChainHandler;

    private DOMDataTreeReadWriteTransaction rwTx;

    public MdsalRestconfStrategy(final DOMDataBroker dataBroker) {
        this(new TransactionChainHandler(dataBroker));
    }

    public MdsalRestconfStrategy(final TransactionChainHandler transactionChainHandler) {
        this.transactionChainHandler = requireNonNull(transactionChainHandler);
        transactionChain = transactionChainHandler.get();
    }

    @Override
    public void prepareReadWriteExecution() {
        rwTx = transactionChain.newReadWriteTransaction();
    }

    @Override
    public void cancel() {
        if (rwTx != null) {
            rwTx.cancel();
        }
        transactionChain.close();
    }

    @Override
    public ListenableFuture<Optional<NormalizedNode<?, ?>>> read(final LogicalDatastoreType store,
            final YangInstanceIdentifier path) {
        try (DOMDataTreeReadTransaction tx = transactionChain.newReadOnlyTransaction()) {
            return tx.read(store, path);
        }
    }

    @Override
    public FluentFuture<Boolean> exists(final LogicalDatastoreType store, final YangInstanceIdentifier path) {
        return rwTx.exists(store, path);
    }

    @Override
    public void delete(final LogicalDatastoreType store, final YangInstanceIdentifier path) {
        DeleteDataTransactionUtil.checkItemExists(this, LogicalDatastoreType.CONFIGURATION, path,
            DELETE_TX_TYPE);
        rwTx.delete(store, path);
    }

    @Override
    public void remove(final LogicalDatastoreType store, final YangInstanceIdentifier path) {
        rwTx.delete(store, path);
    }

    @Override
    public void merge(final LogicalDatastoreType store, final YangInstanceIdentifier path,
            final NormalizedNode<?, ?> data) {
        rwTx.merge(store, path, data);
    }

    @Override
    public void create(final LogicalDatastoreType store, final YangInstanceIdentifier path,
                       final NormalizedNode<?, ?> data, final SchemaContext schemaContext) {
        if (data instanceof MapNode || data instanceof LeafSetNode) {
            final NormalizedNode<?, ?> emptySubTree = ImmutableNodes.fromInstanceId(schemaContext, path);
            merge(LogicalDatastoreType.CONFIGURATION, YangInstanceIdentifier.create(emptySubTree.getIdentifier()),
                emptySubTree);
            TransactionUtil.ensureParentsByMerge(path, schemaContext, this);

            final Collection<? extends NormalizedNode<?, ?>> children =
                ((NormalizedNodeContainer<?, ?, ?>) data).getValue();
            final BatchedExistenceCheck check =
                BatchedExistenceCheck.start(this, LogicalDatastoreType.CONFIGURATION, path, children);

            for (final NormalizedNode<?, ?> child : children) {
                final YangInstanceIdentifier childPath = path.node(child.getIdentifier());
                rwTx.put(store, childPath, child);
            }

            // ... finally collect existence checks and abort the transaction if any of them failed.
            checkExistence(path, check);
        } else {
            checkItemDoesNotExists(this, LogicalDatastoreType.CONFIGURATION, path);
            TransactionUtil.ensureParentsByMerge(path, schemaContext, this);
            rwTx.put(store, path, data);
        }
    }

    @Override
    public void replace(final LogicalDatastoreType store, final YangInstanceIdentifier path,
                        final NormalizedNode<?, ?> data, final SchemaContext schemaContext,
                        final boolean mergeParents) {
        if (data instanceof MapNode || data instanceof LeafSetNode) {
            final NormalizedNode<?, ?> emptySubtree = ImmutableNodes.fromInstanceId(schemaContext, path);
            merge(LogicalDatastoreType.CONFIGURATION, YangInstanceIdentifier.create(emptySubtree.getIdentifier()),
                emptySubtree);
            TransactionUtil.ensureParentsByMerge(path, schemaContext, this);

            for (final NormalizedNode<?, ?> child : ((NormalizedNodeContainer<?, ?, ?>) data).getValue()) {
                final YangInstanceIdentifier childPath = path.node(child.getIdentifier());
                rwTx.put(store, childPath, child);
            }
        } else {
            if (mergeParents) {
                TransactionUtil.ensureParentsByMerge(path, schemaContext, this);
            }
            rwTx.put(store, path, data);
        }
    }

    @Override
    public FluentFuture<? extends @NonNull CommitInfo> commit() {
        return rwTx.commit();
    }

    @Override
    public DOMTransactionChain getTransactionChain() {
        return transactionChain;
    }

    @Override
    public TransactionChainHandler getTransactionChainHandler() {
        return transactionChainHandler;
    }

    private void checkExistence(YangInstanceIdentifier path, BatchedExistenceCheck check) {
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
                    RestconfError.ErrorType.PROTOCOL, RestconfError.ErrorTag.DATA_EXISTS, failure.getKey());
            }

            throw new RestconfDocumentedException(
                "Could not determine the existence of path " + failure.getKey(), e, e.getErrorList());
        }
    }
}
