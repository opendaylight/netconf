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
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadTransaction;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
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

    public MdsalRestconfStrategy(final DOMDataBroker dataBroker) {
        this(new TransactionChainHandler(dataBroker));
    }

    public MdsalRestconfStrategy(final TransactionChainHandler transactionChainHandler) {
        transactionChain = requireNonNull(transactionChainHandler).get();
    }

    @Override
    public RestconfTransaction prepareWriteExecution() {
        return new MdsalRestconfTransaction(transactionChain);
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
        try (DOMDataTreeReadTransaction tx = transactionChain.newReadOnlyTransaction()) {
            return tx.exists(store, path);
        }
    }

    @Override
    public void close() {
        transactionChain.close();
    }
}
