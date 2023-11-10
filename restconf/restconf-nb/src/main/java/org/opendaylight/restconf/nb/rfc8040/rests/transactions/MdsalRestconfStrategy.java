/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.transactions;

import static java.util.Objects.requireNonNull;
import static org.opendaylight.mdsal.common.api.LogicalDatastoreType.CONFIGURATION;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.List;
import java.util.Optional;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.SettableRestconfFuture;
import org.opendaylight.restconf.server.spi.RpcImplementation;
import org.opendaylight.yangtools.yang.common.Empty;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

/**
 * Implementation of RESTCONF operations using {@link DOMTransactionChain} and related concepts.
 *
 * @see DOMTransactionChain
 * @see DOMDataTreeReadWriteTransaction
 */
public final class MdsalRestconfStrategy extends RestconfStrategy {
    private final DOMDataBroker dataBroker;

    public MdsalRestconfStrategy(final EffectiveModelContext modelContext, final DOMDataBroker dataBroker,
            final @Nullable DOMRpcService rpcService, final ImmutableMap<QName, RpcImplementation> localRpcs) {
        super(modelContext, localRpcs, rpcService);
        this.dataBroker = requireNonNull(dataBroker);
    }

    public MdsalRestconfStrategy(final EffectiveModelContext modelContext, final DOMDataBroker dataBroker,
            final @Nullable DOMRpcService rpcService) {
        this(modelContext, dataBroker, rpcService, ImmutableMap.of());
    }

    @Override
    RestconfTransaction prepareWriteExecution() {
        return new MdsalRestconfTransaction(modelContext(), dataBroker);
    }

    @Override
    void delete(final SettableRestconfFuture<Empty> future, final YangInstanceIdentifier path) {
        final var tx = dataBroker.newReadWriteTransaction();
        tx.exists(CONFIGURATION, path).addCallback(new FutureCallback<>() {
            @Override
            public void onSuccess(final Boolean result) {
                if (!result) {
                    cancelTx(new RestconfDocumentedException("Data does not exist", ErrorType.PROTOCOL,
                        ErrorTag.DATA_MISSING, path));
                    return;
                }

                tx.delete(CONFIGURATION, path);
                tx.commit().addCallback(new FutureCallback<CommitInfo>() {
                    @Override
                    public void onSuccess(final CommitInfo result) {
                        future.set(Empty.value());
                    }

                    @Override
                    public void onFailure(final Throwable cause) {
                        future.setFailure(new RestconfDocumentedException("Transaction to delete " + path + " failed",
                            cause));
                    }
                }, MoreExecutors.directExecutor());
            }

            @Override
            public void onFailure(final Throwable cause) {
                cancelTx(new RestconfDocumentedException("Failed to access " + path, cause));
            }

            private void cancelTx(final RestconfDocumentedException ex) {
                tx.cancel();
                future.setFailure(ex);
            }
        }, MoreExecutors.directExecutor());
    }

    @Override
    ListenableFuture<Optional<NormalizedNode>> read(final LogicalDatastoreType store,
            final YangInstanceIdentifier path) {
        try (var tx = dataBroker.newReadOnlyTransaction()) {
            return tx.read(store, path);
        }
    }

    @Override
    ListenableFuture<Optional<NormalizedNode>> read(final LogicalDatastoreType store, final YangInstanceIdentifier path,
            final List<YangInstanceIdentifier> fields) {
        return Futures.immediateFailedFuture(new UnsupportedOperationException(
                "Reading of selected subtrees is currently not supported in: " + MdsalRestconfStrategy.class));
    }

    @Override
    ListenableFuture<Boolean> exists(final YangInstanceIdentifier path) {
        try (var tx = dataBroker.newReadOnlyTransaction()) {
            return tx.exists(LogicalDatastoreType.CONFIGURATION, path);
        }
    }
}
