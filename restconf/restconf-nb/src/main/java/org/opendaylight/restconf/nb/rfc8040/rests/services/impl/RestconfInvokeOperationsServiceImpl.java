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
import javax.ws.rs.Path;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.AsyncResponse;
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
import org.opendaylight.restconf.nb.rfc8040.legacy.NormalizedNodePayload;
import org.opendaylight.restconf.nb.rfc8040.rests.services.api.RestconfInvokeOperationsService;
import org.opendaylight.restconf.nb.rfc8040.streams.StreamsConfiguration;
import org.opendaylight.yang.gen.v1.urn.opendaylight.device.notification.rev221106.SubscribeDeviceNotificationInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.md.sal.remote.rev140114.CreateDataChangeEventSubscriptionInput;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.opendaylight.yangtools.yang.common.YangConstants;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of {@link RestconfInvokeOperationsService}.
 *
 */
@Path("/")
public class RestconfInvokeOperationsServiceImpl implements RestconfInvokeOperationsService {
    private static final Logger LOG = LoggerFactory.getLogger(RestconfInvokeOperationsServiceImpl.class);

    // FIXME: at some point we do not want to have this here, as this is only used for dispatch
    private static final QNameModule SAL_REMOTE_NAMESPACE = CreateDataChangeEventSubscriptionInput.QNAME.getModule();
    private static final QNameModule DEVICE_NOTIFICATION_NAMESPACE = SubscribeDeviceNotificationInput.QNAME.getModule();

    private final DOMRpcService rpcService;
    private final DOMMountPointService mountPointService;
    private final SubscribeToStreamUtil streamUtils;
    private final String basePath;

    public RestconfInvokeOperationsServiceImpl(final DOMRpcService rpcService,
            final DOMMountPointService mountPointService, final StreamsConfiguration configuration) {
        this.rpcService = requireNonNull(rpcService);
        this.mountPointService = requireNonNull(mountPointService);
        streamUtils = configuration.useSSE() ? SubscribeToStreamUtil.serverSentEvents()
            : SubscribeToStreamUtil.webSockets();
        basePath = configuration.basePath();
    }

    @Override
    public void invokeRpc(final String identifier, final NormalizedNodePayload payload, final UriInfo uriInfo,
            final AsyncResponse ar) {
        final InstanceIdentifierContext context = payload.getInstanceIdentifierContext();
        final EffectiveModelContext schemaContext = context.getSchemaContext();
        final DOMMountPoint mountPoint = context.getMountPoint();
        final SchemaNode schema = context.getSchemaNode();
        final QName rpcName = schema.getQName();

        final ListenableFuture<? extends DOMRpcResult> future;
        if (mountPoint == null) {
            // FIXME: this really should be a normal RPC invocation service which has its own interface with JAX-RS,
            //        except ... we check 'identifier' for .contains() instead of exact RPC name!
            if (SAL_REMOTE_NAMESPACE.equals(rpcName.getModule())) {
                if (identifier.contains("create-data-change-event-subscription")) {
                    future = Futures.immediateFuture(
                        CreateStreamUtil.createDataChangeNotifiStream(payload, schemaContext, basePath));
                } else {
                    future = Futures.immediateFailedFuture(new RestconfDocumentedException("Unsupported operation",
                        ErrorType.RPC, ErrorTag.OPERATION_NOT_SUPPORTED));
                }
            } else if (DEVICE_NOTIFICATION_NAMESPACE.equals(rpcName.getModule())) {
                // FIXME: this should be a match on RPC QName
                final String baseUrl = streamUtils.prepareUriByStreamName(uriInfo, "", basePath).toString();
                future = Futures.immediateFuture(CreateStreamUtil.createDeviceNotificationListener(baseUrl, payload,
                    streamUtils, mountPointService));
            } else {
                future = invokeRpc((ContainerNode)payload.getData(), rpcName, rpcService);
            }
        } else {
            future = invokeRpc(payload.getData(), rpcName, mountPoint);
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
    static ListenableFuture<? extends DOMRpcResult> invokeRpc(final NormalizedNode data, final QName rpc,
            final DOMMountPoint mountPoint) {
        return invokeRpc((ContainerNode) data, rpc, mountPoint.getService(DOMRpcService.class).orElseThrow(() -> {
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
    @VisibleForTesting
    static ListenableFuture<? extends DOMRpcResult> invokeRpc(final ContainerNode data, final QName rpc,
            final DOMRpcService rpcService) {
        return Futures.catching(rpcService.invokeRpc(rpc, nonnullInput(rpc, data)),
            DOMRpcException.class,
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
