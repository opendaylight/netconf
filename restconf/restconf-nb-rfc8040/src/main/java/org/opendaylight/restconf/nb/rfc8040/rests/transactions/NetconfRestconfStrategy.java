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
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.common.api.ReadFailedException;
import org.opendaylight.netconf.dom.api.NetconfDataTreeService;
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

    public NetconfRestconfStrategy(final NetconfDataTreeService netconfService) {
        this.netconfService = requireNonNull(netconfService);
    }

    @Override
    public RestconfTransaction prepareWriteExecution() {
        return new NetconfRestconfTransaction(netconfService);
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
    public ListenableFuture<Optional<NormalizedNode<?, ?>>> read(final LogicalDatastoreType store,
            final YangInstanceIdentifier path, final List<YangInstanceIdentifier> fields) {
        switch (store) {
            case CONFIGURATION:
                return netconfService.getConfig(path, fields);
            case OPERATIONAL:
                return netconfService.get(path, fields);
            default:
                LOG.info("Unknown datastore type: {}.", store);
                throw new IllegalArgumentException(String.format(
                        "%s, Cannot read data %s with fields %s for %s datastore, unknown datastore type",
                        netconfService.getDeviceId(), path, fields, store));
        }
    }

    @Override
    public FluentFuture<Boolean> exists(final LogicalDatastoreType store, final YangInstanceIdentifier path) {
        return remapException(read(store, path))
                .transform(optionalNode -> optionalNode != null && optionalNode.isPresent(),
                        MoreExecutors.directExecutor());
    }

    @Override
    public void close() {
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
