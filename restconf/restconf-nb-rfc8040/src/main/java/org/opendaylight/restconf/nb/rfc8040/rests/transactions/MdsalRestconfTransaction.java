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
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

public class MdsalRestconfTransaction extends RestconfTransaction {
    private final DOMTransactionChain transactionChain;
    private final DOMDataTreeReadWriteTransaction rwTx;

    MdsalRestconfTransaction(DOMTransactionChain transactionChain) {
        this.transactionChain = requireNonNull(transactionChain);
        this.rwTx = transactionChain.newReadWriteTransaction();
    }

    @Override
    public void cancel() {
        rwTx.cancel();
        transactionChain.close();
    }

    @Override
    public void remove(final LogicalDatastoreType store, final YangInstanceIdentifier path) {
        rwTx.delete(store, path);
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
}
