/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.transactions;

import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;
import java.util.Optional;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

/**
 * Implementation of RESTCONF operations using {@link DOMTransactionChain} and related concepts.
 *
 * @see DOMTransactionChain
 * @see DOMDataTreeReadWriteTransaction
 */
public final class MdsalRestconfStrategy extends RestconfStrategy {
    private final DOMDataBroker dataBroker;

    public MdsalRestconfStrategy(final DOMDataBroker dataBroker) {
        this.dataBroker = requireNonNull(dataBroker);
    }

    @Override
    public RestconfTransaction prepareWriteExecution() {
        return new MdsalRestconfTransaction(dataBroker);
    }

    @Override
    public ListenableFuture<Optional<NormalizedNode>> read(final LogicalDatastoreType store,
            final YangInstanceIdentifier path) {
        try (var tx = dataBroker.newReadOnlyTransaction()) {
            return tx.read(store, path);
        }
    }

    @Override
    public ListenableFuture<Optional<NormalizedNode>> read(final LogicalDatastoreType store,
            final YangInstanceIdentifier path, final List<YangInstanceIdentifier> fields) {
        return Futures.immediateFailedFuture(new UnsupportedOperationException(
                "Reading of selected subtrees is currently not supported in: " + MdsalRestconfStrategy.class));
    }

    @Override
    public ListenableFuture<Boolean> exists(final LogicalDatastoreType store, final YangInstanceIdentifier path) {
        try (var tx = dataBroker.newReadOnlyTransaction()) {
            return tx.exists(store, path);
        }
    }
}
