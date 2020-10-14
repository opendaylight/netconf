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
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import java.util.List;
import java.util.Optional;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.common.api.ReadFailedException;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.netconf.dom.api.NetconfDataTreeService;
import org.opendaylight.restconf.nb.rfc8040.handlers.TransactionChainHandler;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of RESTCONF operations on top of a raw NETCONF backend.
 *
 * @see NetconfDataTreeService
 */
public final class NetconfRestconfStrategy extends RestconfStrategy {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfRestconfStrategy.class);

    private final NetconfDataTreeService netconfService;

    private List<ListenableFuture<? extends DOMRpcResult>> resultsFutures;

    public NetconfRestconfStrategy(final NetconfDataTreeService netconfService) {
        this.netconfService = requireNonNull(netconfService);
    }

    @Override
    public void prepareReadWriteExecution() {
        resultsFutures = netconfService.lock();
    }

    @Override
    public void cancel() {
        netconfService.discardChanges();
        netconfService.unlock();
    }

    @Override
    public ListenableFuture<Optional<NormalizedNode<?, ?>>> read(final LogicalDatastoreType store,
                                                                 final YangInstanceIdentifier path) {
        switch (store) {
            case CONFIGURATION:
                return netconfService.getConfig(path);
            case OPERATIONAL:
                return netconfService.get(path);
            default:
                LOG.info("Unknown datastore type: {}.", store);
                throw new IllegalArgumentException(String.format(
                        "%s, Cannot read data %s for %s datastore, unknown datastore type",
                        netconfService.getDeviceId(), path, store));
        }
    }

    @Override
    public FluentFuture<Boolean> exists(final LogicalDatastoreType store, final YangInstanceIdentifier path) {
        return remapException(read(store, path))
                .transform(optionalNode -> optionalNode != null && optionalNode.isPresent(),
                        MoreExecutors.directExecutor());
    }

    @Override
    public void delete(final LogicalDatastoreType store, final YangInstanceIdentifier path) {
        resultsFutures.add(netconfService.delete(store, path));
    }

    @Override
    public void merge(final LogicalDatastoreType store, final YangInstanceIdentifier path,
                      final NormalizedNode<?, ?> data) {
        resultsFutures.add(netconfService.merge(store, path, data, Optional.empty()));
    }

    @Override
    public void create(final LogicalDatastoreType store, final YangInstanceIdentifier path,
                       final NormalizedNode<?, ?> data) {
        resultsFutures.add(netconfService.create(store, path, data, Optional.empty()));
    }

    @Override
    public void replace(final LogicalDatastoreType store, final YangInstanceIdentifier path,
                        final NormalizedNode<?, ?> data) {
        resultsFutures.add(netconfService.replace(store, path, data, Optional.empty()));
    }

    @Override
    public FluentFuture<? extends @NonNull CommitInfo> commit() {
        return FluentFuture.from(netconfService.commit(resultsFutures));
    }

    /**
     * As we are not using any transactions here, always return null.
     */
    @Override
    public DOMTransactionChain getTransactionChain() {
        return null;
    }

    /**
     * As we are not using any transactions here, always return null.
     */
    @Override
    public TransactionChainHandler getTransactionChainHandler() {
        return null;
    }

    private static <T> FluentFuture<T> remapException(final ListenableFuture<T> input) {
        final SettableFuture<T> ret = SettableFuture.create();
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
        return FluentFuture.from(ret);
    }
}
