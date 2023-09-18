/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.server.api.RestconfServer;
import org.opendaylight.restconf.server.api.RestconfServerFuture;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;

/**
 * Utility base class for implementing {@link RestconfServer}s. Handles both datastore access and RPC/action invocation
 * based on interfaces exposed in this package.
 */
@NonNullByDefault
public abstract class AbstractRestconfServer implements RestconfServer {
    private final ConcurrentMap<QName, RestconfRpcImplementation> localRpcs = new ConcurrentHashMap<>();

    @Override
    public final RestconfServerFuture<ContainerNode> invokeRpc(final RequestInfo info,
            final ApiPath rpc, final ContainerNode input) {
        // FIXME: interpret rpc. which may involve a mount point ... how?
        final QName rpcName = null;
        final var local = localRpcs.get(rpcName);
        return local != null ? local.invokeRpc(info, input) : defaultInvokeRpc(info, rpcName, input);
    }

    protected abstract RestconfServerFuture<ContainerNode> defaultInvokeRpc(RequestInfo info, QName rpcName,
        ContainerNode input);
}
