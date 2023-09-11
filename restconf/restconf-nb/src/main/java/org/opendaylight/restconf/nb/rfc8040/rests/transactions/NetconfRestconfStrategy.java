/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.transactions;

import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import java.util.List;
import java.util.Optional;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.common.api.ReadFailedException;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.netconf.dom.api.NetconfDataTreeService;
import org.opendaylight.restconf.common.errors.SettableRestconfFuture;
import org.opendaylight.yangtools.yang.common.Empty;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

/**
 * Implementation of RESTCONF operations on top of a raw NETCONF backend.
 *
 * @see NetconfDataTreeService
 */
public final class NetconfRestconfStrategy extends RestconfStrategy {
    private final NetconfDataTreeService netconfService;

    public NetconfRestconfStrategy(final EffectiveModelContext modelContext,
            final NetconfDataTreeService netconfService, final @Nullable DOMRpcService rpcService) {
        super(modelContext, rpcService);
        this.netconfService = requireNonNull(netconfService);
    }

    @Override
    void delete(final SettableRestconfFuture<Empty> future, final YangInstanceIdentifier path) {
        final var tx = prepareWriteExecution();
        tx.delete(path);
        Futures.addCallback(tx.commit(), new FutureCallback<CommitInfo>() {
            @Override
            public void onSuccess(final CommitInfo result) {
                future.set(Empty.value());
            }

            @Override
            public void onFailure(final Throwable cause) {
                future.setFailure(TransactionUtil.decodeException(cause, "DELETE", path, modelContext()));
            }
        }, MoreExecutors.directExecutor());
    }

    @Override
    RestconfTransaction prepareWriteExecution() {
        return new NetconfRestconfTransaction(modelContext(), netconfService);
    }

    @Override
    ListenableFuture<Optional<NormalizedNode>> read(final LogicalDatastoreType store,
            final YangInstanceIdentifier path) {
        return switch (store) {
            case CONFIGURATION -> netconfService.getConfig(path);
            case OPERATIONAL -> netconfService.get(path);
        };
    }

    @Override
    ListenableFuture<Optional<NormalizedNode>> read(final LogicalDatastoreType store,
            final YangInstanceIdentifier path, final List<YangInstanceIdentifier> fields) {
        return switch (store) {
            case CONFIGURATION -> netconfService.getConfig(path, fields);
            case OPERATIONAL -> netconfService.get(path, fields);
        };
    }

    @Override
    ListenableFuture<Boolean> exists(final YangInstanceIdentifier path) {
        return Futures.transform(remapException(netconfService.getConfig(path)),
            optionalNode -> optionalNode != null && optionalNode.isPresent(),
            MoreExecutors.directExecutor());
    }

    private static <T> ListenableFuture<T> remapException(final ListenableFuture<T> input) {
        final var ret = SettableFuture.<T>create();
        Futures.addCallback(input, new FutureCallback<T>() {
            @Override
            public void onSuccess(final T result) {
                ret.set(result);
            }

            @Override
            public void onFailure(final Throwable cause) {
                ret.setException(cause instanceof ReadFailedException ? cause
                    : new ReadFailedException("NETCONF operation failed", cause));
            }
        }, MoreExecutors.directExecutor());
        return ret;
    }
}
