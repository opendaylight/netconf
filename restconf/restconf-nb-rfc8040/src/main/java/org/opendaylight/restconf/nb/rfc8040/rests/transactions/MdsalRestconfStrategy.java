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
import java.util.Optional;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadTransaction;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.netconf.xpath.NetconfXPathContext;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.context.WriterParameters;
import org.opendaylight.restconf.nb.rfc8040.handlers.TransactionChainHandler;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

/**
 * Strategy that allow to communicate with netconf devices in terms of md-sal transactions.
 *
 * @see DOMTransactionChain
 * @see DOMDataTreeReadWriteTransaction
 */
public class MdsalRestconfStrategy implements RestconfStrategy {
    private final InstanceIdentifierContext<?> instanceIdentifier;
    private final DOMTransactionChain transactionChain;
    private final TransactionChainHandler transactionChainHandler;
    private final WriterParameters parameters;
    private DOMDataTreeReadWriteTransaction rwTx;

    public MdsalRestconfStrategy(final InstanceIdentifierContext<?> instanceIdentifier,
            final TransactionChainHandler transactionChainHandler) {
        this(instanceIdentifier, null, transactionChainHandler);
    }

    public MdsalRestconfStrategy(final InstanceIdentifierContext<?> instanceIdentifier,
                                 @Nullable final WriterParameters parameters,
                                 final TransactionChainHandler transactionChainHandler) {
        this.parameters = parameters;
        this.instanceIdentifier = requireNonNull(instanceIdentifier);
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
            NetconfXPathContext netconfXPathContext) {
        throw new UnsupportedOperationException("Can be used just with Netconf device.");
    }

    @Override
    public FluentFuture<Boolean> exists(LogicalDatastoreType store, YangInstanceIdentifier path) {
        return rwTx.exists(store, path);
    }

    @Override
    public void delete(LogicalDatastoreType store, final YangInstanceIdentifier path) {
        rwTx.delete(store, path);
    }

    @Override
    public void merge(LogicalDatastoreType store, YangInstanceIdentifier path, NormalizedNode<?, ?> data) {
        rwTx.merge(store, path, data);
    }

    @Override
    public void create(LogicalDatastoreType store, YangInstanceIdentifier path, NormalizedNode<?, ?> data) {
        rwTx.put(store, path, data);
    }

    @Override
    public void replace(LogicalDatastoreType store, YangInstanceIdentifier path, NormalizedNode<?, ?> data) {
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
    public InstanceIdentifierContext<?> getInstanceIdentifier() {
        return instanceIdentifier;
    }

    @Override
    public TransactionChainHandler getTransactionChainHandler() {
        return transactionChainHandler;
    }

    @Override
    public WriterParameters getParameters() {
        return parameters;
    }

    @Override
    public RestconfStrategy buildStrategy(final InstanceIdentifierContext<?> instanceIdentifierContext) {
        return new MdsalRestconfStrategy(instanceIdentifierContext, this.parameters, this.transactionChainHandler);
    }
}
