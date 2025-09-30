/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.mdsal.spi;

import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import java.net.URI;
import java.util.stream.Collectors;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.netconf.databind.DatabindPath.Rpc;
import org.opendaylight.netconf.databind.RequestError;
import org.opendaylight.netconf.databind.RequestException;
import org.opendaylight.restconf.server.api.InvokeResult;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.restconf.server.spi.InterceptingServerRpcOperations;
import org.opendaylight.restconf.server.spi.ServerRpcOperations;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link ServerRpcOperations} delegating to a {@link DOMRpcService}.
 */
public record DOMServerRpcOperations(@NonNull DOMRpcService rpcService) implements ServerRpcOperations {
    private static final Logger LOG = LoggerFactory.getLogger(DOMServerRpcOperations.class);

    public DOMServerRpcOperations {
        requireNonNull(rpcService);
    }

    @Override
    public void invokeRpc(final ServerRequest<InvokeResult> request, final URI restconfURI, final Rpc path,
            final ContainerNode input) {
        // FIXME: NETCONF-773: why not DOMRpcResultCallback?
        Futures.addCallback(rpcService.invokeRpc(path.statement().argument(), input),
            new FutureCallback<DOMRpcResult>() {
                @Override
                public void onSuccess(final DOMRpcResult result) {
                    final var errors = result.errors();
                    if (errors.isEmpty()) {
                        request.completeWith(InterceptingServerRpcOperations.invokeResultOf(path, result.value()));
                    } else {
                        LOG.debug("RPC invocation reported {}", result.errors());
                        request.failWith(new RequestException(result.errors().stream()
                            .map(RequestError::ofRpcError)
                            .collect(Collectors.toList()), null, "Opereation implementation reported errors"));
                    }
                }

                @Override
                public void onFailure(final Throwable cause) {
                    LOG.debug("RPC invocation failed, cause");
                    if (cause instanceof RequestException ex) {
                        request.failWith(ex);
                    } else {
                        // TODO: YangNetconfErrorAware if we ever get into a broader invocation scope
                        request.failWith(new RequestException(ErrorType.RPC, ErrorTag.OPERATION_FAILED, cause));
                    }
                }
            }, MoreExecutors.directExecutor());
    }
}
