/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.services.impl;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import javax.ws.rs.Path;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMRpcException;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.context.NormalizedNodeContext;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.handlers.SchemaContextHandler;
import org.opendaylight.restconf.nb.rfc8040.rests.services.api.RestconfInvokeOperationsService;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.opendaylight.yangtools.yang.common.YangConstants;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of {@link RestconfInvokeOperationsService}.
 *
 */
@Path("/")
public class RestconfInvokeOperationsServiceImpl implements RestconfInvokeOperationsService {
    private static final Logger LOG = LoggerFactory.getLogger(RestconfInvokeOperationsServiceImpl.class);

    private final DOMRpcService rpcService;
    private final SchemaContextHandler schemaContextHandler;

    public RestconfInvokeOperationsServiceImpl(final DOMRpcService rpcService,
            final SchemaContextHandler schemaContextHandler) {
        this.rpcService = requireNonNull(rpcService);
        this.schemaContextHandler = requireNonNull(schemaContextHandler);
    }

    @Override
    public NormalizedNodeContext invokeRpc(final String identifier, final NormalizedNodeContext payload,
            final UriInfo uriInfo) {
        final QName rpcName = payload.getInstanceIdentifierContext().getSchemaNode().getQName();
        final DOMMountPoint mountPoint = payload.getInstanceIdentifierContext().getMountPoint();

        final DOMRpcResult response;
        final EffectiveModelContext schemaContextRef;
        if (mountPoint == null) {
            response = invokeRpc(payload.getData(), rpcName, rpcService);
            schemaContextRef = schemaContextHandler.get();
        } else {
            response = invokeRpc(payload.getData(), rpcName, mountPoint);
            schemaContextRef = modelContext(mountPoint);
        }

        final DOMRpcResult result = checkResponse(response);

        RpcDefinition resultNodeSchema = null;
        NormalizedNode resultData = null;
        if (result != null && result.getResult() != null) {
            resultData = result.getResult();
            resultNodeSchema = (RpcDefinition) payload.getInstanceIdentifierContext().getSchemaNode();
        }

        if (resultData != null && ((ContainerNode) resultData).isEmpty()) {
            throw new WebApplicationException(Response.Status.NO_CONTENT);
        } else {
            return new NormalizedNodeContext(new InstanceIdentifierContext<>(null, resultNodeSchema, mountPoint,
                    schemaContextRef), resultData);
        }
    }

    /**
     * Invoking rpc via mount point.
     *
     * @param mountPoint mount point
     * @param data input data
     * @param rpc RPC type
     * @return {@link DOMRpcResult}
     */
    // FIXME: NETCONF-718: we should be returning a future here
    @VisibleForTesting
    static DOMRpcResult invokeRpc(final NormalizedNode data, final QName rpc, final DOMMountPoint mountPoint) {
        return invokeRpc(data, rpc, mountPoint.getService(DOMRpcService.class).orElseThrow(() -> {
            final String errmsg = "RPC service is missing.";
            LOG.debug(errmsg);
            return new RestconfDocumentedException(errmsg);
        }));
    }

    /**
     * Invoke rpc.
     *
     * @param data input data
     * @param rpc RPC type
     * @param rpcService rpc service to invoke rpc
     * @return {@link DOMRpcResult}
     */
    // FIXME: NETCONF-718: we should be returning a future here
    @VisibleForTesting
    static DOMRpcResult invokeRpc(final NormalizedNode data, final QName rpc, final DOMRpcService rpcService) {
        return checkedGet(Futures.catching(
            rpcService.invokeRpc(rpc, nonnullInput(rpc, data)), DOMRpcException.class,
            cause -> new DefaultDOMRpcResult(ImmutableList.of(RpcResultBuilder.newError(
                RpcError.ErrorType.RPC, "operation-failed", cause.getMessage()))),
            MoreExecutors.directExecutor()));
    }

    private static @NonNull NormalizedNode nonnullInput(final QName type, final NormalizedNode input) {
        return input != null ? input
                : ImmutableNodes.containerNode(YangConstants.operationInputQName(type.getModule()));
    }

    /**
     * Check the validity of the result.
     *
     * @param response response of rpc
     * @return {@link DOMRpcResult} result
     */
    @VisibleForTesting
    static DOMRpcResult checkResponse(final DOMRpcResult response) {
        if (response == null) {
            return null;
        }
        try {
            if (response.getErrors().isEmpty()) {
                return response;
            }
            LOG.debug("RpcError message {}", response.getErrors());
            throw new RestconfDocumentedException("RPCerror message ", null, response.getErrors());
        } catch (final CancellationException e) {
            final String errMsg = "The operation was cancelled while executing.";
            LOG.debug("Cancel RpcExecution: {}", errMsg, e);
            throw new RestconfDocumentedException(errMsg, ErrorType.RPC, ErrorTag.PARTIAL_OPERATION, e);
        }
    }

    @Deprecated
    static <T> T checkedGet(final ListenableFuture<T> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            throw new RestconfDocumentedException("Interrupted while waiting for result of invocation", e);
        } catch (ExecutionException e) {
            Throwables.throwIfInstanceOf(e.getCause(), RestconfDocumentedException.class);
            throw new RestconfDocumentedException("Invocation failed", e);
        }
    }

    private static EffectiveModelContext modelContext(final DOMMountPoint mountPoint) {
        return mountPoint.getService(DOMSchemaService.class)
            .flatMap(svc -> Optional.ofNullable(svc.getGlobalContext()))
            .orElse(null);
    }
}
