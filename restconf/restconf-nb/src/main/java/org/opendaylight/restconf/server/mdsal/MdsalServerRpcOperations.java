/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.mdsal;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import java.net.URI;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.restconf.server.api.DatabindPath.Rpc;
import org.opendaylight.restconf.server.api.InvokeResult;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.restconf.server.spi.LocalServerRpcOperations;
import org.opendaylight.restconf.server.spi.RpcImplementation;
import org.opendaylight.restconf.server.spi.ServerActionOperations;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;

/**
 * {@link ServerActionOperations} forwarding {@link #invokeRpc(ServerRequest, URI, Rpc, ContainerNode)} to a
 * {@link DOMRpcService}.
 */
public final class MdsalServerRpcOperations extends LocalServerRpcOperations {
    private final DOMRpcService rpcService;

    public MdsalServerRpcOperations(final ImmutableMap<QName, RpcImplementation> localRpcs,
            final DOMRpcService rpcService) {
        super(localRpcs);
        this.rpcService = requireNonNull(rpcService);
    }

    @Override
    protected void fallbackInvokeRpc(final ServerRequest<InvokeResult> request, final URI restconfURI, final Rpc path,
            final ContainerNode input) {
        Futures.addCallback(rpcService.invokeRpc(path.rpc().argument(), input), new DOMRpcResultCallback(request, path),
            MoreExecutors.directExecutor());
    }
}
