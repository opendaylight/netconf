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
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.ws.rs.Consumes;
import javax.ws.rs.Encoded;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMRpcException;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.MediaTypes;
import org.opendaylight.restconf.nb.rfc8040.legacy.NormalizedNodePayload;
import org.opendaylight.restconf.nb.rfc8040.streams.StreamsConfiguration;
import org.opendaylight.yang.gen.v1.urn.opendaylight.device.notification.rev221106.SubscribeDeviceNotification;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.md.sal.remote.rev140114.CreateDataChangeEventSubscription;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.opendaylight.yangtools.yang.common.YangConstants;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An operation resource represents a protocol operation defined with the YANG {@code rpc} statement. It is invoked
 * using a POST method on the operation resource.
 */
@Path("/")
public final class RestconfInvokeOperationsServiceImpl {
    private static final Logger LOG = LoggerFactory.getLogger(RestconfInvokeOperationsServiceImpl.class);

    private final DOMRpcService rpcService;
    private final DOMMountPointService mountPointService;
    private final SubscribeToStreamUtil streamUtils;

    public RestconfInvokeOperationsServiceImpl(final DOMRpcService rpcService,
            final DOMMountPointService mountPointService, final StreamsConfiguration configuration) {
        this.rpcService = requireNonNull(rpcService);
        this.mountPointService = requireNonNull(mountPointService);
        streamUtils = configuration.useSSE() ? SubscribeToStreamUtil.serverSentEvents()
            : SubscribeToStreamUtil.webSockets();
    }

    /**
     * Invoke RPC operation.
     *
     * @param identifier module name and rpc identifier string for the desired operation
     * @param payload {@link NormalizedNodePayload} - the body of the operation
     * @param uriInfo URI info
     * @param ar {@link AsyncResponse} which needs to be completed with a {@link NormalizedNodePayload} output
     */
    @POST
    @Path("/operations/{identifier:.+}")
    @Produces({
        MediaTypes.APPLICATION_YANG_DATA_JSON,
        MediaTypes.APPLICATION_YANG_DATA_XML,
        MediaType.APPLICATION_JSON,
        MediaType.APPLICATION_XML,
        MediaType.TEXT_XML
    })
    @Consumes({
        MediaTypes.APPLICATION_YANG_DATA_JSON,
        MediaTypes.APPLICATION_YANG_DATA_XML,
        MediaType.APPLICATION_JSON,
        MediaType.APPLICATION_XML,
        MediaType.TEXT_XML
    })
    public void invokeRpc(@Encoded @PathParam("identifier") final String identifier,
            final NormalizedNodePayload payload, @Context final UriInfo uriInfo, @Suspended final AsyncResponse ar) {
        final InstanceIdentifierContext context = payload.getInstanceIdentifierContext();
        final EffectiveModelContext schemaContext = context.getSchemaContext();
        final DOMMountPoint mountPoint = context.getMountPoint();
        final SchemaNode schema = context.getSchemaNode();
        final QName rpcName = schema.getQName();
        final ContainerNode rpcInput = (ContainerNode) payload.getData();

        final ListenableFuture<? extends DOMRpcResult> future;
        if (mountPoint == null) {
            if (CreateDataChangeEventSubscription.QNAME.equals(rpcName)) {
                future = Futures.immediateFuture(
                    CreateStreamUtil.createDataChangeNotifiStream(rpcInput, schemaContext));
            } else if (SubscribeDeviceNotification.QNAME.equals(rpcName)) {
                final String baseUrl = streamUtils.prepareUriByStreamName(uriInfo, "").toString();
                future = Futures.immediateFuture(CreateStreamUtil.createDeviceNotificationListener(baseUrl, rpcInput,
                    streamUtils, mountPointService));
            } else {
                future = invokeRpc(rpcInput, rpcName, rpcService);
            }
        } else {
            future = invokeRpc(rpcInput, rpcName, mountPoint);
        }

        Futures.addCallback(future, new FutureCallback<DOMRpcResult>() {
            @Override
            public void onSuccess(final DOMRpcResult response) {
                final var errors = response.errors();
                if (!errors.isEmpty()) {
                    LOG.debug("RpcError message {}", response.errors());
                    ar.resume(new RestconfDocumentedException("RPCerror message ", null, response.errors()));
                    return;
                }

                final ContainerNode resultData = response.value();
                if (resultData == null || resultData.isEmpty()) {
                    ar.resume(new WebApplicationException(Status.NO_CONTENT));
                } else {
                    ar.resume(NormalizedNodePayload.of(context, resultData));
                }
            }

            @Override
            public void onFailure(final Throwable failure) {
                ar.resume(failure);
            }
        }, MoreExecutors.directExecutor());
    }

    /**
     * Invoking rpc via mount point.
     *
     * @param mountPoint mount point
     * @param data input data
     * @param rpc RPC type
     * @return {@link DOMRpcResult}
     */
    @VisibleForTesting
    static ListenableFuture<? extends DOMRpcResult> invokeRpc(final ContainerNode data, final QName rpc,
            final DOMMountPoint mountPoint) {
        return invokeRpc(data, rpc, mountPoint.getService(DOMRpcService.class).orElseThrow(() -> {
            final String errmsg = "RPC service is missing.";
            LOG.debug(errmsg);
            return new RestconfDocumentedException(errmsg);
        }));
    }

    /**
     * Invoke rpc.
     *
     * @param input input data
     * @param rpc RPC type
     * @param rpcService rpc service to invoke rpc
     * @return {@link DOMRpcResult}
     */
    @VisibleForTesting
    static ListenableFuture<? extends DOMRpcResult> invokeRpc(final ContainerNode input, final QName rpc,
            final DOMRpcService rpcService) {
        return Futures.catching(rpcService.invokeRpc(rpc, nonnullInput(rpc, input)), DOMRpcException.class,
            cause -> new DefaultDOMRpcResult(List.of(RpcResultBuilder.newError(ErrorType.RPC, ErrorTag.OPERATION_FAILED,
                cause.getMessage()))),
            MoreExecutors.directExecutor());
    }

    private static @NonNull ContainerNode nonnullInput(final QName type, final ContainerNode input) {
        return input != null ? input
                : ImmutableNodes.containerNode(YangConstants.operationInputQName(type.getModule()));
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
}
