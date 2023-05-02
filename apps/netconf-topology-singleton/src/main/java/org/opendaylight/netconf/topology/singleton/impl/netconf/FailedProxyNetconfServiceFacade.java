/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl.netconf;

import static java.util.Objects.requireNonNull;
import static org.opendaylight.mdsal.common.api.LogicalDatastoreType.CONFIGURATION;
import static org.opendaylight.mdsal.common.api.LogicalDatastoreType.OPERATIONAL;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;
import java.util.Optional;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.common.api.ReadFailedException;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.netconf.api.EffectiveOperation;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.yangtools.util.concurrent.FluentFutures;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FailedProxyNetconfServiceFacade implements ProxyNetconfServiceFacade {
    private static final Logger LOG = LoggerFactory.getLogger(FailedProxyNetconfServiceFacade.class);

    private final @NonNull RemoteDeviceId id;
    private final @NonNull Throwable failure;

    public FailedProxyNetconfServiceFacade(final RemoteDeviceId id, final Throwable failure) {
        this.id = requireNonNull(id);
        this.failure = requireNonNull(failure);
    }

    @Override
    public ListenableFuture<DOMRpcResult> lock() {
        LOG.debug("{}: Lock - failure", id, failure);
        return serviceFailed("lock");
    }

    @Override
    public ListenableFuture<DOMRpcResult> unlock() {
        LOG.debug("{}: Unlock - failure", id, failure);
        return serviceFailed("unlock");
    }

    @Override
    public ListenableFuture<DOMRpcResult> discardChanges() {
        LOG.debug("{}: Discard changes - failure", id, failure);
        return serviceFailed("discard changes");
    }

    @Override
    public ListenableFuture<Optional<NormalizedNode>> get(final YangInstanceIdentifier path) {
        LOG.debug("{}: Get {} {} - failure", id, OPERATIONAL, path, failure);
        return readFailed("get");
    }

    @Override
    public ListenableFuture<Optional<NormalizedNode>> get(final YangInstanceIdentifier path,
            final List<YangInstanceIdentifier> fields) {
        LOG.debug("{}: Get {} {} with fields {} - failure", id, OPERATIONAL, path, fields, failure);
        return readFailed("get");
    }

    @Override
    public ListenableFuture<Optional<NormalizedNode>> getConfig(final YangInstanceIdentifier path) {
        LOG.debug("{}: GetConfig {} {} - failure", id, CONFIGURATION, path, failure);
        return readFailed("getConfig");
    }

    @Override
    public ListenableFuture<Optional<NormalizedNode>> getConfig(final YangInstanceIdentifier path,
            final List<YangInstanceIdentifier> fields) {
        LOG.debug("{}: GetConfig {} {} with fields {} - failure", id, CONFIGURATION, path, fields, failure);
        return readFailed("getConfig");
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> merge(final LogicalDatastoreType store,
            final YangInstanceIdentifier path, final NormalizedNode data,
            final Optional<EffectiveOperation> defaultOperation) {
        LOG.debug("{}: Merge {} {} - failure", id, store, path, failure);
        return serviceFailed("merge");
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> replace(final LogicalDatastoreType store,
            final YangInstanceIdentifier path, final NormalizedNode data,
            final Optional<EffectiveOperation> defaultOperation) {
        LOG.debug("{}: Replace {} {} - failure", id, store, path, failure);
        return serviceFailed("replace");
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> create(final LogicalDatastoreType store,
            final YangInstanceIdentifier path, final NormalizedNode data,
            final Optional<EffectiveOperation> defaultOperation) {
        LOG.debug("{}: Create {} {} - failure", id, store, path, failure);
        return serviceFailed("create");
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> delete(final LogicalDatastoreType store,
            final YangInstanceIdentifier path) {
        LOG.debug("{}: Delete {} {} - failure", id, store, path, failure);
        return serviceFailed("delete");
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> remove(final LogicalDatastoreType store,
            final YangInstanceIdentifier path) {
        LOG.debug("{}: Remove {} {} - failure", id, store, path, failure);
        return serviceFailed("remove");
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> commit() {
        LOG.debug("{}: Commit - failure", id, failure);
        return serviceFailed("commit");
    }

    @Override
    public Object getDeviceId() {
        return id;
    }

    private <T> ListenableFuture<T> readFailed(final String operation) {
        return FluentFutures.immediateFailedFluentFuture(new ReadFailedException(operation, failure));
    }

    private <T> ListenableFuture<T> serviceFailed(final String operation) {
        return FluentFutures.immediateFailedFluentFuture(new NetconfServiceFailedException(operation, failure));
    }
}
