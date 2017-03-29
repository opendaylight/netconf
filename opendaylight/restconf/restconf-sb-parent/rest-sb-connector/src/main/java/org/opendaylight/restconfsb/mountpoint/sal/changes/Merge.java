/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.mountpoint.sal.changes;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.util.Collection;
import javax.annotation.Nullable;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.restconfsb.communicator.api.RestconfFacade;
import org.opendaylight.restconfsb.communicator.api.http.HttpException;
import org.opendaylight.restconfsb.communicator.api.http.NotFoundException;
import org.opendaylight.restconfsb.communicator.util.RestconfUtil;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

class Merge extends Change {

    Merge(final RestconfFacade facade, final YangInstanceIdentifier path, final NormalizedNode<?, ?> normalizedNode) {
        super(path, normalizedNode, facade);
    }

    @Override
    public ListenableFuture<Void> apply(final Void input) {
        //check if resource exists, since patch will not create it
        final ListenableFuture<Void> headFuture = facade.headData(LogicalDatastoreType.CONFIGURATION, this.getPath());
        final SettableFuture<Void> resultFuture = SettableFuture.create();
        final DelegatingFutureCallback<Void> callback = new DelegatingFutureCallback<>(resultFuture);
        Futures.addCallback(headFuture, new FutureCallback<Void>() {
            @Override
            public void onSuccess(@Nullable final Void result) {
                //resource exists, patch it
                final ListenableFuture<Void> patchResult = facade.patchConfig(Merge.this.getPath(), Merge.this.getNormalizedNode());
                Futures.addCallback(patchResult, callback);
            }

            @Override
            public void onFailure(final Throwable t) {
                if (t instanceof NotFoundException) {
                    //if resource does not exist, create it
                    final ListenableFuture<Void> putResult = facade.putConfig(Merge.this.getPath(), Merge.this.getNormalizedNode());
                    Futures.addCallback(putResult, callback);
                } else if (t instanceof HttpException) {
                    final HttpException exception = (HttpException) t;
                    final Collection<RpcError> rpcErrors = facade.parseErrors(exception);
                    resultFuture.setException(new TransactionCommitFailedException("Write failed", exception, RestconfUtil.toRpcErrorArray(rpcErrors)));
                } else {
                    resultFuture.setException(new TransactionCommitFailedException("Write failed", t));
                }
            }
        });
        return resultFuture;
    }

    /**
     * Callback just delegates future result to provided settable future.
     *
     * @param <T> Result future type
     */
    private static final class DelegatingFutureCallback<T> implements FutureCallback<T> {

        private final SettableFuture<T> settableFuture;

        private DelegatingFutureCallback(final SettableFuture<T> settableFuture) {
            this.settableFuture = settableFuture;
        }

        @Override
        public void onSuccess(@Nullable final T result) {
            settableFuture.set(result);
        }

        @Override
        public void onFailure(final Throwable t) {
            settableFuture.setException(t);
        }
    }
}
