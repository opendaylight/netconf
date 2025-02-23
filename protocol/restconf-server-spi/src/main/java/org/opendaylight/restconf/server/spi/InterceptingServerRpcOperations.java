/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.Beta;
import java.net.URI;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.common.DatabindPath.OperationPath;
import org.opendaylight.netconf.common.DatabindPath.Rpc;
import org.opendaylight.restconf.server.api.InvokeResult;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;

/**
 * A {@link ServerRpcOperations} which attempts to intercept the invocation or forward it to a delegate
 * {@link ServerRpcOperations}.
 */
@NonNullByDefault
public record InterceptingServerRpcOperations(Interceptor interceptor, ServerRpcOperations delegate)
        implements ServerRpcOperations {
    @FunctionalInterface
    public interface Interceptor {

        @Nullable RpcImplementation interceptRpc(Rpc path);
    }

    public InterceptingServerRpcOperations {
        requireNonNull(interceptor);
        requireNonNull(delegate);
    }

    public InterceptingServerRpcOperations(final Interceptor interceptor) {
        this(interceptor, NotSupportedServerRpcOperations.INSTANCE);
    }

    @Override
    public void invokeRpc(final ServerRequest<InvokeResult> request, final URI restconfURI, final Rpc path,
            final ContainerNode input) {
        final var impl = interceptor.interceptRpc(path);
        if (impl != null) {
            impl.invoke(request.transform(result -> invokeResultOf(path, result)), restconfURI,
                new OperationInput(path, input));
        } else {
            delegate.invokeRpc(request, restconfURI, path, input);
        }
    }

    @Beta
    public static InvokeResult invokeResultOf(final OperationPath path, final @Nullable ContainerNode value) {
        return value == null || value.isEmpty() ? InvokeResult.EMPTY
            : new InvokeResult(NormalizedFormattableBody.of(path, value));
    }
}
