/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.connect.netconf.sal.tx;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.common.api.ReadFailedException;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadTransaction;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReadWriteTx implements DOMDataTreeReadWriteTransaction {

    private static final Logger LOG  = LoggerFactory.getLogger(ReadWriteTx.class);

    private final DOMDataTreeReadTransaction delegateReadTx;
    private final DOMDataTreeWriteTransaction delegateWriteTx;
    private List<ListenableFuture<? extends DOMRpcResult>> resultsFutures = new ArrayList<>();

    public ReadWriteTx(final DOMDataTreeReadTransaction delegateReadTx,
            final DOMDataTreeWriteTransaction delegateWriteTx) {
        this.delegateReadTx = delegateReadTx;
        this.delegateWriteTx = delegateWriteTx;
    }

    @Override
    public boolean cancel() {
        return delegateWriteTx.cancel();
    }

    @Override
    public void put(final LogicalDatastoreType store, final YangInstanceIdentifier path,
                    final NormalizedNode<?, ?> data) {
        delegateWriteTx.put(store, path, data);
    }

    @Override
    public void merge(final LogicalDatastoreType store, final YangInstanceIdentifier path,
                      final NormalizedNode<?, ?> data) {
        delegateWriteTx.merge(store, path, data);
    }

    @Override
    public void delete(final LogicalDatastoreType store, final YangInstanceIdentifier path) {
        delegateWriteTx.delete(store, path);
    }

    @Override
    public FluentFuture<? extends @NonNull CommitInfo> commit() {
        return delegateWriteTx.commit();
    }

    @Override
    public FluentFuture<Optional<NormalizedNode<?, ?>>> read(final LogicalDatastoreType store,
                                                             final YangInstanceIdentifier path) {
        final SettableFuture<Optional<NormalizedNode<?, ?>>> resultFuture = SettableFuture.create();
        RpcError rpcError = getResultsFutures();
        if (rpcError == null) {
            return delegateReadTx.read(store, path);
        }
        else {
            resultFuture.setException(new ReadFailedException(rpcError.getTag(), rpcError));
        }
        return FluentFuture.from(resultFuture);
    }

    @Override
    public FluentFuture<Boolean> exists(final LogicalDatastoreType store, final YangInstanceIdentifier path) {
        final SettableFuture<Boolean> resultFuture = SettableFuture.create();
        RpcError rpcError = getResultsFutures();
        if (rpcError == null) {
            return delegateReadTx.exists(store, path);
        }
        else {
            resultFuture.setException(new ReadFailedException(rpcError.getTag(), rpcError));
        }
        return FluentFuture.from(resultFuture);
    }

    public void setResultFutures(List<ListenableFuture<? extends DOMRpcResult>> resultFutures) {
        this.resultsFutures = resultFutures;
    }

    public RpcError getResultsFutures() {
        RpcError rpcError = null;
        if (!resultsFutures.isEmpty()) {
            for (ListenableFuture<? extends DOMRpcResult> domRpcResultListenableFuture : resultsFutures) {
                try {
                    if (!domRpcResultListenableFuture.get().getErrors().isEmpty()) {
                        if (domRpcResultListenableFuture.get().getErrors().iterator().hasNext()) {
                            rpcError = domRpcResultListenableFuture.get().getErrors().iterator().next();
                            break;
                        }
                    }
                } catch (InterruptedException e) {
                    LOG.error("Exception while getting rpc results", e);
                } catch (ExecutionException e) {
                    LOG.error("Exception while getting rpc results", e);
                }

            }
        }
        return rpcError;
    }

    @Override
    public Object getIdentifier() {
        return this;
    }
}
