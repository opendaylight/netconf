/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableMap;
import java.net.URI;
import org.opendaylight.restconf.server.api.DatabindPath.Rpc;
import org.opendaylight.restconf.server.api.InvokeResult;
import org.opendaylight.restconf.server.api.ServerException;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;

/**
 * A {@link ServerRpcOperations} operations implementation with a fixed set of {@link RpcImplementation}s.
 */
public class LocalServerRpcOperations extends AbstractServerRpcOperations {
    private final ImmutableMap<QName, RpcImplementation> rpcs;

    public LocalServerRpcOperations(final ImmutableMap<QName, RpcImplementation> rpcs) {
        this.rpcs = requireNonNull(rpcs);
    }

    @Override
    protected final RpcImplementation lookupImplementation(final QName type) {
        return rpcs.get(type);
    }

    @Override
    protected void fallbackInvokeRpc(final ServerRequest<InvokeResult> request, final URI restconfURI, final Rpc path,
            final ContainerNode input) {
        request.completeWith(new ServerException(ErrorType.PROTOCOL, ErrorTag.OPERATION_NOT_SUPPORTED,
            "RPC implementation not available"));
    }
}
