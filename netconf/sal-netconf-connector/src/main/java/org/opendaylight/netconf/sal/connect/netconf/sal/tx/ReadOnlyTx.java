/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.connect.netconf.sal.tx;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadOnlyTransaction;
import org.opendaylight.mdsal.common.api.MappingCheckedFuture;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfBaseOps;
import org.opendaylight.netconf.sal.connect.netconf.util.NetconfRpcFutureCallback;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public final class ReadOnlyTx implements DOMDataReadOnlyTransaction {

    private static final Logger LOG  = LoggerFactory.getLogger(ReadOnlyTx.class);

    private final NetconfBaseOps netconfOps;
    private final RemoteDeviceId id;

    public ReadOnlyTx(final NetconfBaseOps netconfOps, final RemoteDeviceId id) {
        this.netconfOps = netconfOps;
        this.id = id;
    }

    private CheckedFuture<Optional<NormalizedNode<?, ?>>, ReadFailedException> readConfigurationData(
            final YangInstanceIdentifier path) {
        final ListenableFuture<Optional<NormalizedNode<?, ?>>> configRunning = netconfOps.getConfigRunningData(
                new NetconfRpcFutureCallback("Data read", id), Optional.fromNullable(path));

        return MappingCheckedFuture.create(configRunning, ReadFailedException.MAPPER);
    }

    private CheckedFuture<Optional<NormalizedNode<?, ?>>, ReadFailedException> readOperationalData(
            final YangInstanceIdentifier path) {
        final ListenableFuture<Optional<NormalizedNode<?, ?>>> configCandidate = netconfOps.getData(
                new NetconfRpcFutureCallback("Data read", id), Optional.fromNullable(path));

        return MappingCheckedFuture.create(configCandidate, ReadFailedException.MAPPER);
    }

    @Override
    public void close() {
        // NOOP
    }

    @Override
    public CheckedFuture<Optional<NormalizedNode<?, ?>>, ReadFailedException> read(
            final LogicalDatastoreType store, final YangInstanceIdentifier path) {
        switch (store) {
            case CONFIGURATION:
                return readConfigurationData(path);
            case OPERATIONAL:
                return readOperationalData(path);
            default:
                LOG.info("Unknown datastore type: {}.", store);
                throw new IllegalArgumentException(String.format(
                    "%s, Cannot read data %s for %s datastore, unknown datastore type", id, path, store));
        }

    }

    @Override
    public CheckedFuture<Boolean, ReadFailedException> exists(final LogicalDatastoreType store,
                                                              final YangInstanceIdentifier path) {
        final ListenableFuture<Boolean> result = Futures.transform(read(store, path),
            optionalNode -> optionalNode != null && optionalNode.isPresent(), MoreExecutors.directExecutor());
        return MappingCheckedFuture.create(result, ReadFailedException.MAPPER);
    }

    @Override
    public Object getIdentifier() {
        return this;
    }
}
