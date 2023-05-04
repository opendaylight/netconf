/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.client.mdsal.spi;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import java.util.Optional;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.common.api.ReadFailedException;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadTransaction;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.client.mdsal.impl.NetconfBaseOps;
import org.opendaylight.netconf.client.mdsal.impl.NetconfRpcFutureCallback;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class AbstractReadOnlyTx implements DOMDataTreeReadTransaction {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractReadOnlyTx.class);

    final NetconfBaseOps netconfOps;
    final RemoteDeviceId id;

    AbstractReadOnlyTx(final NetconfBaseOps netconfOps, final RemoteDeviceId id) {
        this.netconfOps = netconfOps;
        this.id = id;
    }

    private FluentFuture<Optional<NormalizedNode>> readConfigurationData(
            final YangInstanceIdentifier path) {
        return remapException(netconfOps.getConfigRunningData(
            new NetconfRpcFutureCallback("Data read", id), Optional.ofNullable(path)));
    }

    private FluentFuture<Optional<NormalizedNode>> readOperationalData(
            final YangInstanceIdentifier path) {
        return remapException(netconfOps.getData(
            new NetconfRpcFutureCallback("Data read", id), Optional.ofNullable(path)));
    }

    static final <T> @NonNull FluentFuture<T> remapException(final ListenableFuture<T> input) {
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

    @Override
    public final void close() {
        // NOOP
    }

    @Override
    public final FluentFuture<Optional<NormalizedNode>> read(final LogicalDatastoreType store,
            final YangInstanceIdentifier path) {
        return switch (store) {
            case CONFIGURATION -> readConfigurationData(path);
            case OPERATIONAL -> readOperationalData(path);
            default -> {
                LOG.info("Unknown datastore type: {}.", store);
                throw new IllegalArgumentException(String.format(
                    "%s, Cannot read data %s for %s datastore, unknown datastore type", id, path, store));
            }
        };
    }

    @Override
    public final FluentFuture<Boolean> exists(final LogicalDatastoreType store, final YangInstanceIdentifier path) {
        return read(store, path).transform(optionalNode -> optionalNode != null && optionalNode.isPresent(),
                MoreExecutors.directExecutor());
    }

    @Override
    public final Object getIdentifier() {
        return this;
    }
}
