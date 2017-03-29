/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.mountpoint.sal;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.util.Collection;
import javax.annotation.Nullable;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadOnlyTransaction;
import org.opendaylight.restconfsb.communicator.api.RestconfFacade;
import org.opendaylight.restconfsb.communicator.api.http.HttpException;
import org.opendaylight.restconfsb.communicator.api.http.NotFoundException;
import org.opendaylight.restconfsb.communicator.util.RestconfUtil;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

class ReadOnlyTx implements DOMDataReadOnlyTransaction {

    private final RestconfFacade facade;

    ReadOnlyTx(final RestconfFacade facade) {
        this.facade = facade;
    }

    @Override
    public void close() {

    }

    @Override
    public CheckedFuture<Optional<NormalizedNode<?, ?>>, ReadFailedException> read(
            final LogicalDatastoreType datastore, final YangInstanceIdentifier path) {
        final ListenableFuture<Optional<NormalizedNode<?, ?>>> result;
        result = facade.getData(datastore, path);

        return Futures.makeChecked(result, new Function<Exception, ReadFailedException>() {
            @Nullable
            @Override
            public ReadFailedException apply(final Exception input) {
                if (input.getCause() instanceof HttpException) {
                    final HttpException exception = (HttpException) input.getCause();
                    final Collection<RpcError> rpcErrors = facade.parseErrors(exception);
                    return new ReadFailedException("read failed", exception, RestconfUtil.toRpcErrorArray(rpcErrors));
                } else {
                    return new ReadFailedException("Read failed", input);
                }
            }
        });
    }

    @Override
    public CheckedFuture<Boolean, ReadFailedException> exists(final LogicalDatastoreType datastore, final YangInstanceIdentifier path) {
        final ListenableFuture<Void> result = facade.headData(datastore, path);
        final SettableFuture<Boolean> headResult = SettableFuture.create();
        Futures.addCallback(result, new FutureCallback<Void>() {
            @Override
            public void onSuccess(final Void normalizedNodeOptional) {
                headResult.set(true);
            }

            @Override
            public void onFailure(final Throwable throwable) {
                if (throwable instanceof NotFoundException) {
                    headResult.set(false);
                } else if (throwable instanceof HttpException) {
                    final HttpException exc = (HttpException) throwable;
                    final Collection<RpcError> rpcErrors = facade.parseErrors(exc);
                    headResult.setException(new ReadFailedException("read failed", RestconfUtil.toRpcErrorArray(rpcErrors)));
                } else {
                    headResult.setException(throwable);
                }
            }
        });

        return Futures.makeChecked(headResult, new Function<Exception, ReadFailedException>() {
            @Nullable
            @Override
            public ReadFailedException apply(@Nullable final Exception e) {
                if (e != null && e.getCause() instanceof ReadFailedException) {
                    return (ReadFailedException) e.getCause();
                }
                return new ReadFailedException("Read request failed %s", e);
            }
        });
    }

    @Override
    public Object getIdentifier() {
        return this;
    }
}