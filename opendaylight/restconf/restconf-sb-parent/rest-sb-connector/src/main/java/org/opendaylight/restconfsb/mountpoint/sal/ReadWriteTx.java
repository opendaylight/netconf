/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconfsb.mountpoint.sal;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.ListenableFuture;
import org.opendaylight.controller.md.sal.common.api.TransactionStatus;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadWriteTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMDataWriteTransaction;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

class ReadWriteTx implements DOMDataReadWriteTransaction {

    private final DOMDataReadTransaction delegateReadTx;
    private final DOMDataWriteTransaction delegateWriteTx;

    public ReadWriteTx(final ReadOnlyTx delegateReadTx, final WriteOnlyTx delegateWriteTx) {
        this.delegateReadTx = delegateReadTx;
        this.delegateWriteTx = delegateWriteTx;
    }

    @Override
    public CheckedFuture<Boolean, ReadFailedException> exists(final LogicalDatastoreType logicalDatastoreType,
                                                              final YangInstanceIdentifier yangInstanceIdentifier) {
        return delegateReadTx.exists(logicalDatastoreType, yangInstanceIdentifier);
    }

    @Override
    public CheckedFuture<Optional<NormalizedNode<?, ?>>, ReadFailedException> read(final LogicalDatastoreType logicalDatastoreType,
                                                                                   final YangInstanceIdentifier yangInstanceIdentifier) {
        return delegateReadTx.read(logicalDatastoreType, yangInstanceIdentifier);
    }

    @Override
    public void put(final LogicalDatastoreType logicalDatastoreType, final YangInstanceIdentifier yangInstanceIdentifier,
                    final NormalizedNode<?, ?> normalizedNode) {
        delegateWriteTx.put(logicalDatastoreType, yangInstanceIdentifier, normalizedNode);
    }

    @Override
    public void merge(final LogicalDatastoreType logicalDatastoreType, final YangInstanceIdentifier yangInstanceIdentifier,
                      final NormalizedNode<?, ?> normalizedNode) {
        delegateWriteTx.merge(logicalDatastoreType, yangInstanceIdentifier, normalizedNode);
    }

    @Override
    public void delete(final LogicalDatastoreType logicalDatastoreType, final YangInstanceIdentifier yangInstanceIdentifier) {
        delegateWriteTx.delete(logicalDatastoreType, yangInstanceIdentifier);
    }

    @Override
    public CheckedFuture<Void, TransactionCommitFailedException> submit() {
        return delegateWriteTx.submit();
    }

    @Override
    public ListenableFuture<RpcResult<TransactionStatus>> commit() {
        return delegateWriteTx.commit();
    }

    @Override
    public boolean cancel() {
        return delegateWriteTx.cancel();
    }

    @Override
    public Object getIdentifier() {
        return this;
    }
}
