/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.utils;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import javax.ws.rs.core.Response.Status;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMActionResult;
import org.opendaylight.mdsal.dom.api.DOMActionService;
import org.opendaylight.mdsal.dom.api.DOMDataTreeIdentifier;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorTag;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorType;
import org.opendaylight.restconf.nb.rfc8040.handlers.ActionServiceHandler;
import org.opendaylight.restconf.nb.rfc8040.handlers.RpcServiceHandler;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Util class for rpc.
 *
 */
public final class RestconfInvokeOperationsUtil {

    private static final Logger LOG = LoggerFactory.getLogger(RestconfInvokeOperationsUtil.class);

    private RestconfInvokeOperationsUtil() {
        throw new UnsupportedOperationException("Util class");
    }

    /**
     * Invoking rpc via mount point.
     *
     * @param mountPoint
     *             mount point
     * @param data
     *             input data
     * @param schemaPath
     *             schema path of data
     * @return {@link DOMRpcResult}
     */
    public static DOMRpcResult invokeRpcViaMountPoint(final DOMMountPoint mountPoint, final NormalizedNode<?, ?> data,
            final SchemaPath schemaPath) {
        final Optional<DOMRpcService> mountPointService = mountPoint.getService(DOMRpcService.class);
        if (mountPointService.isPresent()) {
            final ListenableFuture<DOMRpcResult> rpc = mountPointService.get().invokeRpc(schemaPath, data);
            return prepareResult(rpc);
        }
        final String errmsg = "RPC service is missing.";
        LOG.debug(errmsg);
        throw new RestconfDocumentedException(errmsg);
    }

    /**
     * Invoke rpc.
     *
     * @param data
     *             input data
     * @param schemaPath
     *             schema path of data
     * @param rpcServiceHandler
     *             rpc service handler to invoke rpc
     * @return {@link DOMRpcResult}
     */
    public static DOMRpcResult invokeRpc(final NormalizedNode<?, ?> data, final SchemaPath schemaPath,
            final RpcServiceHandler rpcServiceHandler) {
        final DOMRpcService rpcService = rpcServiceHandler.get();
        if (rpcService == null) {
            throw new RestconfDocumentedException(Status.SERVICE_UNAVAILABLE);
        }

        final ListenableFuture<DOMRpcResult> rpc = rpcService.invokeRpc(schemaPath, data);
        return prepareResult(rpc);
    }

    /**
     * Check the validity of the result.
     *
     * @param response
     *             response of rpc
     * @return {@link DOMRpcResult} result
     */
    public static DOMRpcResult checkResponse(final DOMRpcResult response) {
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

    private static DOMRpcResult prepareResult(final ListenableFuture<DOMRpcResult> rpc) {
        final RpcResultFactory dataFactory = new RpcResultFactory();
        FutureCallbackTx.addCallback(rpc, RestconfDataServiceConstant.PostData.POST_TX_TYPE, dataFactory);
        return dataFactory.build();
    }

    /**
     * Invoking Action via mount point.
     *
     * @param mountPoint
     *             mount point
     * @param data
     *             input data
     * @param schemaPath
     *             schema path of data
     * @return {@link DOMActionResult}
     */
    public static DOMActionResult invokeActionViaMountPoint(final DOMMountPoint mountPoint, final ContainerNode data,
        final SchemaPath schemaPath, final YangInstanceIdentifier yangIId) {
        final Optional<DOMActionService> mountPointService = mountPoint.getService(DOMActionService.class);
        if (mountPointService.isPresent()) {
            final DOMDataTreeIdentifier domDataTreeIdentifier = prepareDataTreeId(yangIId, schemaPath);
            final ListenableFuture<? extends DOMActionResult> action = mountPointService.get()
                .invokeAction(schemaPath, domDataTreeIdentifier, data);
            return prepareActionResult(action);
        }
        final String errmsg = "DomAction service is missing.";
        LOG.debug(errmsg);
        throw new RestconfDocumentedException(errmsg);
    }

    /**
     * Invoke Action via ActionServiceHandler.
     *
     * @param data
     *             input data
     * @param schemaPath
     *             schema path of data
     * @param actionServiceHandler
     *             action service handler to invoke action
     * @return {@link DOMActionResult}
     */
    public static DOMActionResult invokeAction(final ContainerNode data, final SchemaPath schemaPath,
        final ActionServiceHandler actionServiceHandler, final YangInstanceIdentifier yangIId) {
        final DOMActionService actionService = actionServiceHandler.get();
        if (actionService != null) {
            final DOMDataTreeIdentifier domDataTreeIdentifier = prepareDataTreeId(yangIId, schemaPath);
            final ListenableFuture<? extends DOMActionResult> action = actionService
                .invokeAction(schemaPath, domDataTreeIdentifier, data);
            return prepareActionResult(action);
        }
        final String errmsg = "DomAction service Service Unavailable.";
        LOG.debug(errmsg);
        throw new RestconfDocumentedException(Status.SERVICE_UNAVAILABLE);
    }

    /**
     * Check the validity of the result.
     *
     * @param response
     *             response of Action
     * @return {@link DOMActionResult} result
     */
    public static DOMActionResult checkActionResponse(final DOMActionResult response) {
        if (response != null) {
            try {
                if (response.getErrors().isEmpty()) {
                    return response;
                }
                LOG.debug("InvokeAction Error Message {}", response.getErrors());
                throw new RestconfDocumentedException("InvokeAction Error Message ", null, response.getErrors());
            } catch (final CancellationException e) {
                final String errMsg = "The Action Operation was cancelled while executing.";
                LOG.debug("Cancel Execution: {}", errMsg, e);
                throw new RestconfDocumentedException(errMsg, ErrorType.RPC, ErrorTag.PARTIAL_OPERATION, e);
            }
        }
        return null;
    }

    /**
     * Prepare Action Result.
     *
     * @param actionResult
     *            {@link DOMActionResult} - action result
     * @return {@link DOMActionResult} result
     */
    private static DOMActionResult prepareActionResult(final ListenableFuture<? extends DOMActionResult> actionResult) {
        final ActionResultFactory dataFactory = new ActionResultFactory();
        FutureCallbackTx.addCallback(actionResult, RestconfDataServiceConstant.PostData.POST_TX_TYPE, dataFactory);
        return dataFactory.build();
    }

    /**
     * Prepare DOMDataTree Identifier.
     *
     * @param yangIId
     *             {@link YangInstanceIdentifier}
     * @param schemaPath
     *              {@link SchemaPath}
     * @return {@link DOMDataTreeIdentifier} domDataTreeIdentifier
     */
    private static DOMDataTreeIdentifier prepareDataTreeId(final YangInstanceIdentifier yangIId,
        final SchemaPath schemaPath) {
        final List<PathArgument> pathArg = new ArrayList<>();
        for (PathArgument path : yangIId.getPathArguments()) {
            if (path.getNodeType().getLocalName().equals(schemaPath.getLastComponent().getLocalName())) {
                break;
            }
            pathArg.add(path);
        }
        YangInstanceIdentifier yangInstanceIdentifier = YangInstanceIdentifier.builder().append(pathArg).build();
        DOMDataTreeIdentifier domDataTreeIdentifier = new DOMDataTreeIdentifier(LogicalDatastoreType.OPERATIONAL,
            yangInstanceIdentifier);
        return domDataTreeIdentifier;
    }
}
