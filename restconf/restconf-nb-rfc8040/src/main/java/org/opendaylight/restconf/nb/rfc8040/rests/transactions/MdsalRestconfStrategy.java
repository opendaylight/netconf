/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.transactions;

import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;
import java.util.Optional;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadTransaction;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.netconf.api.dom.NetconfDOMFieldsReadTransaction;
import org.opendaylight.restconf.nb.rfc8040.handlers.TransactionChainHandler;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

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
    public ListenableFuture<Optional<NormalizedNode<?, ?>>> read(LogicalDatastoreType store,
                                                                 YangInstanceIdentifier path,
                                                                 List<YangInstanceIdentifier> fields) {
        try (NetconfDOMFieldsReadTransaction tx =
                     (NetconfDOMFieldsReadTransaction) transactionChain.newReadOnlyTransaction()) {
            return tx.read(store, path, fields);
        }
    }

    @Override
    public FluentFuture<Boolean> exists(final LogicalDatastoreType store, final YangInstanceIdentifier path) {
        return rwTx.exists(store, path);
    }

    @Override
    public void delete(final LogicalDatastoreType store, final YangInstanceIdentifier path) {
        rwTx.delete(store, path);
    }

    @Override
    public void merge(final LogicalDatastoreType store, final YangInstanceIdentifier path,
            final NormalizedNode<?, ?> data) {
        rwTx.merge(store, path, data);
    }

    @Override
    public void create(final LogicalDatastoreType store, final YangInstanceIdentifier path,
            final NormalizedNode<?, ?> data) {
        rwTx.put(store, path, data);
    }

    @Override
    public void replace(final LogicalDatastoreType store, final YangInstanceIdentifier path,
            final NormalizedNode<?, ?> data) {
        create(store, path, data);
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
}
