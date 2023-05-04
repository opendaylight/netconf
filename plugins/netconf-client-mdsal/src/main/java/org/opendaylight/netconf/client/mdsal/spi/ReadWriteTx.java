/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.spi;

import com.google.common.util.concurrent.FluentFuture;
import java.util.Optional;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadTransaction;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteTransaction;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

class ReadWriteTx<T extends DOMDataTreeReadTransaction> implements DOMDataTreeReadWriteTransaction {
    private final DOMDataTreeWriteTransaction delegateWriteTx;
    final T delegateReadTx;

    ReadWriteTx(final T delegateReadTx, final DOMDataTreeWriteTransaction delegateWriteTx) {
        this.delegateReadTx = delegateReadTx;
        this.delegateWriteTx = delegateWriteTx;
    }

    @Override
    public final boolean cancel() {
        return delegateWriteTx.cancel();
    }

    @Override
    public final void put(final LogicalDatastoreType store, final YangInstanceIdentifier path,
                          final NormalizedNode data) {
        delegateWriteTx.put(store, path, data);
    }

    @Override
    public final void merge(final LogicalDatastoreType store, final YangInstanceIdentifier path,
                            final NormalizedNode data) {
        delegateWriteTx.merge(store, path, data);
    }

    @Override
    public final void delete(final LogicalDatastoreType store, final YangInstanceIdentifier path) {
        delegateWriteTx.delete(store, path);
    }

    @Override
    public final FluentFuture<? extends @NonNull CommitInfo> commit() {
        return delegateWriteTx.commit();
    }

    @Override
    public final FluentFuture<Optional<NormalizedNode>> read(final LogicalDatastoreType store,
            final YangInstanceIdentifier path) {
        return delegateReadTx.read(store, path);
    }

    @Override
    public final FluentFuture<Boolean> exists(final LogicalDatastoreType store, final YangInstanceIdentifier path) {
        return delegateReadTx.exists(store, path);
    }

    @Override
    public final Object getIdentifier() {
        return this;
    }
}
