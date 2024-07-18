/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import java.net.URI;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.server.api.DatabindPath.OperationPath;
import org.opendaylight.restconf.server.api.DatabindPath.Rpc;
import org.opendaylight.restconf.server.api.InvokeResult;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;

/**
 * Abstract base class for {@link ServerRpcOperations}.
 */
public abstract class AbstractServerRpcOperations implements ServerRpcOperations {
    @Override
    public final void invokeRpc(final ServerRequest<InvokeResult> request, final URI restconfURI, final Rpc path,
            final ContainerNode input) {
        final var local = lookupImplementation(path.rpc().argument());
        if (local != null) {
            local.invoke(request.transform(result -> outputToInvokeResult(path, result)), restconfURI,
                new OperationInput(path, input));
        } else {
            fallbackInvokeRpc(request, restconfURI, path, input);
        }
    }

    protected abstract void fallbackInvokeRpc(ServerRequest<InvokeResult> request, URI restconfURI, Rpc path,
        ContainerNode input);

    protected abstract @Nullable RpcImplementation lookupImplementation(@NonNull QName type);

    public static final @NonNull InvokeResult outputToInvokeResult(final @NonNull OperationPath path,
            final @Nullable ContainerNode value) {
        return value == null || value.isEmpty() ? InvokeResult.EMPTY
            : new InvokeResult(NormalizedFormattableBody.of(path, value));
    }
}
