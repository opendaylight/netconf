/*
 * Copyright (c) 2018 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl.tx;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import java.util.Objects;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.controller.md.sal.common.api.data.AsyncWriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.netconf.sal.connect.util.RemoteDeviceId;
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

    FailedProxyTransactionFacade(RemoteDeviceId id, Throwable failure) {
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
    public CheckedFuture<Optional<NormalizedNode<?, ?>>, ReadFailedException> read(LogicalDatastoreType store,
            YangInstanceIdentifier path) {
        LOG.debug("{}: Read {} {} - failure {}", id, store, path, failure);
        return Futures.immediateFailedCheckedFuture(ReadFailedException.MAPPER.apply(
                failure instanceof Exception ? (Exception)failure : new ReadFailedException("read", failure)));
    }

    @Override
    public CheckedFuture<Boolean, ReadFailedException> exists(LogicalDatastoreType store, YangInstanceIdentifier path) {
        LOG.debug("{}: Exists {} {} - failure {}", id, store, path, failure);
        return Futures.immediateFailedCheckedFuture(ReadFailedException.MAPPER.apply(
                failure instanceof Exception ? (Exception)failure : new ReadFailedException("read", failure)));
    }

    @Override
    public void delete(LogicalDatastoreType store, YangInstanceIdentifier path) {
        LOG.debug("{}: Delete {} {} - failure {}", id, store, path, failure);
    }

    @Override
    public void put(LogicalDatastoreType store, YangInstanceIdentifier path, NormalizedNode<?, ?> data) {
        LOG.debug("{}: Put {} {} - failure {}", id, store, path, failure);
    }

    @Override
    public void merge(LogicalDatastoreType store, YangInstanceIdentifier path, NormalizedNode<?, ?> data) {
        LOG.debug("{}: Merge {} {} - failure {}", id, store, path, failure);
    }

    @Override
    public @NonNull FluentFuture<? extends @NonNull CommitInfo> commit() {
        LOG.debug("{}: Commit {} {} - failure {}", id, failure);
        return FluentFuture.from(Futures.immediateFailedFuture(failure instanceof Exception
                ? AsyncWriteTransaction.SUBMIT_EXCEPTION_MAPPER.apply((Exception)failure)
                        : new TransactionCommitFailedException("commit", failure)));
    }
}
