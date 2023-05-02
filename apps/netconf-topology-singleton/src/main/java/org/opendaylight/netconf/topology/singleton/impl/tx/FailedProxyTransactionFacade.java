/*
 * Copyright (c) 2018 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl.tx;

import com.google.common.util.concurrent.FluentFuture;
import java.util.Objects;
import java.util.Optional;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.common.api.ReadFailedException;
import org.opendaylight.mdsal.common.api.TransactionCommitFailedException;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.yangtools.util.concurrent.FluentFutures;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of ProxyTransactionFacade that fails each request.
 *
 * @author Thomas Pantelis
 */
class FailedProxyTransactionFacade implements ProxyTransactionFacade {
    private static final Logger LOG = LoggerFactory.getLogger(FailedProxyTransactionFacade.class);

    private final RemoteDeviceId id;
    private final Throwable failure;

    FailedProxyTransactionFacade(final RemoteDeviceId id, final Throwable failure) {
        this.id = Objects.requireNonNull(id);
        this.failure = Objects.requireNonNull(failure);
    }

    @Override
    public Object getIdentifier() {
        return id;
    }

    @Override
    public boolean cancel() {
        return true;
    }

    @Override
    public FluentFuture<Optional<NormalizedNode>> read(final LogicalDatastoreType store,
            final YangInstanceIdentifier path) {
        LOG.debug("{}: Read {} {} - failure", id, store, path, failure);
        return FluentFutures.immediateFailedFluentFuture(ReadFailedException.MAPPER.apply(
                failure instanceof Exception ? (Exception)failure : new ReadFailedException("read", failure)));
    }

    @Override
    public FluentFuture<Boolean> exists(final LogicalDatastoreType store,
            final YangInstanceIdentifier path) {
        LOG.debug("{}: Exists {} {} - failure", id, store, path, failure);
        return FluentFutures.immediateFailedFluentFuture(ReadFailedException.MAPPER.apply(
                failure instanceof Exception ? (Exception)failure : new ReadFailedException("read", failure)));
    }

    @Override
    public void delete(final LogicalDatastoreType store, final YangInstanceIdentifier path) {
        LOG.debug("{}: Delete {} {} - failure", id, store, path, failure);
    }

    @Override
    public void put(final LogicalDatastoreType store, final YangInstanceIdentifier path, final NormalizedNode data) {
        LOG.debug("{}: Put {} {} - failure", id, store, path, failure);
    }

    @Override
    public void merge(final LogicalDatastoreType store, final YangInstanceIdentifier path, final NormalizedNode data) {
        LOG.debug("{}: Merge {} {} - failure", id, store, path, failure);
    }

    @Override
    public @NonNull FluentFuture<? extends @NonNull CommitInfo> commit() {
        LOG.debug("{}: Commit - failure", id, failure);
        final TransactionCommitFailedException txCommitEx;
        if (failure instanceof TransactionCommitFailedException) {
            txCommitEx = (TransactionCommitFailedException) failure;
        } else {
            txCommitEx = new TransactionCommitFailedException("commit", failure);
        }
        return FluentFutures.immediateFailedFluentFuture(txCommitEx);
    }
}
