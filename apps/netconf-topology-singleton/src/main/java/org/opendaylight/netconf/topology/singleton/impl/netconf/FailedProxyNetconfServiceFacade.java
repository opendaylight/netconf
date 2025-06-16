/*
 * Copyright (c) 2020 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.topology.singleton.impl.netconf;

import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.Optional;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.common.api.ReadFailedException;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.netconf.client.mdsal.api.RemoteDeviceId;
import org.opendaylight.netconf.databind.DatabindPath;
import org.opendaylight.restconf.server.api.DataGetParams;
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
    public ListenableFuture<? extends DOMRpcResult> createData(YangInstanceIdentifier path, NormalizedNode data) {
        LOG.debug("{}: Create{} - failure", id, path, failure);
        return serviceFailed("create");
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> deleteData(DatabindPath.Data path) {
        LOG.debug("{}: Delete {} - failure", id, path, failure);
        return serviceFailed("delete");
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> removeData(DatabindPath.Data path) {
        LOG.debug("{}: Remove {} - failure", id, path, failure);
        return serviceFailed("remove");
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> mergeData(YangInstanceIdentifier path, NormalizedNode data) {
        LOG.debug("{}: Merge {} - failure", id, path, failure);
        return serviceFailed("merge");
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> putData(YangInstanceIdentifier path, NormalizedNode data) {
        LOG.debug("{}: Put {} - failure", id, path, failure);
        return serviceFailed("put");
    }

    @Override
    public ListenableFuture<Optional<NormalizedNode>> getData(DatabindPath.Data path, DataGetParams params) {
        LOG.debug("{}: Get {} - failure", id, path, failure);
        return readFailed("get");
    }

    @Override
    public ListenableFuture<? extends DOMRpcResult> commit() {
        LOG.debug("{}: Commit - failure", id, failure);
        return serviceFailed("commit");
    }

    private <T> ListenableFuture<T> readFailed(final String operation) {
        return FluentFutures.immediateFailedFluentFuture(new ReadFailedException(operation, failure));
    }

    private <T> ListenableFuture<T> serviceFailed(final String operation) {
        return FluentFutures.immediateFailedFluentFuture(new NetconfServiceFailedException(operation, failure));
    }
}
