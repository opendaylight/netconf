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
import java.util.concurrent.CancellationException;
import java.util.stream.Collectors;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.dom.api.DOMActionException;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.netconf.databind.DatabindPath.OperationPath;
import org.opendaylight.netconf.databind.RequestError;
import org.opendaylight.netconf.databind.RequestException;
import org.opendaylight.restconf.server.api.InvokeResult;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.restconf.server.spi.InterceptingServerRpcOperations;
import org.opendaylight.yangtools.yang.common.ErrorSeverity;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class DOMRpcResultCallback implements FutureCallback<DOMRpcResult> {
    private static final Logger LOG = LoggerFactory.getLogger(DOMRpcResultCallback.class);

    private final @NonNull ServerRequest<? super InvokeResult> request;
    private final @NonNull OperationPath path;

    DOMRpcResultCallback(final ServerRequest<? super InvokeResult> request, final OperationPath path) {
        this.request = requireNonNull(request);
        this.path = requireNonNull(path);
    }

    @Override
    public void onSuccess(final DOMRpcResult result) {
        final var rpcErrors = result.errors();
        LOG.debug("InvokeAction Error Message {}", rpcErrors);

        final var errors = rpcErrors.stream()
            .filter(rpcError -> {
                if (ErrorSeverity.WARNING == rpcError.getSeverity()) {
                    LOG.debug("Not reporting warning: {}", rpcError);
                    return false;
                }
                return true;
            })
            .map(RequestError::ofRpcError)
            .collect(Collectors.toList());
        if (errors.isEmpty()) {
            request.completeWith(InterceptingServerRpcOperations.invokeResultOf(path, result.value()));
        } else {
            request.completeWith(new RequestException(errors, null, "Invocation failed"));
        }
    }

    @Override
    public void onFailure(final Throwable cause) {
        request.completeWith(switch (cause) {
            case CancellationException e -> new RequestException(ErrorType.RPC, ErrorTag.PARTIAL_OPERATION,
                "Action cancelled while executing", e);
            case DOMActionException e -> new RequestException(ErrorType.RPC, ErrorTag.OPERATION_FAILED, e);
            case RequestException e -> e;
            default -> new RequestException("Invocation failed", cause);
        });
    }
}
