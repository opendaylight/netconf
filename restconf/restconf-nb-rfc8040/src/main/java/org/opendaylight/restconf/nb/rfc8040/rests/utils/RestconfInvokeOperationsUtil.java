/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.utils;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.CancellationException;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMActionResult;
import org.opendaylight.mdsal.dom.api.DOMActionService;
import org.opendaylight.mdsal.dom.api.DOMDataTreeIdentifier;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.YangConstants;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Util class for rpc.
 */
public final class RestconfInvokeOperationsUtil {
    private static final Logger LOG = LoggerFactory.getLogger(RestconfInvokeOperationsUtil.class);

    private RestconfInvokeOperationsUtil() {
        // Hidden on purpose
    }

    /**
     * Invoking rpc via mount point.
     *
     * @param mountPoint
     *             mount point
     * @param data
     *             input data
     * @param rpc
     *             RPC type
     * @return {@link DOMRpcResult}
     */
    public static DOMRpcResult invokeRpc(final NormalizedNode data, final QName rpc, final DOMMountPoint mountPoint) {
        return invokeRpc(data, rpc, mountPoint.getService(DOMRpcService.class).orElseThrow(() -> {
            final String errmsg = "RPC service is missing.";
            LOG.debug(errmsg);
            return new RestconfDocumentedException(errmsg);
        }));
    }

    /**
     * Invoke rpc.
     *
     * @param data
     *             input data
     * @param rpc
     *             RPC type
     * @param rpcService
     *             rpc service to invoke rpc
     * @return {@link DOMRpcResult}
     */
    public static DOMRpcResult invokeRpc(final NormalizedNode data, final QName rpc, final DOMRpcService rpcService) {
        final ListenableFuture<? extends DOMRpcResult> future = rpcService.invokeRpc(rpc, nonnullInput(rpc, data));
        final RpcResultFactory dataFactory = new RpcResultFactory();
        FutureCallbackTx.addCallback(future, PostDataTransactionUtil.POST_TX_TYPE, dataFactory);
        return dataFactory.build();
    }

    private static @NonNull NormalizedNode nonnullInput(final QName type, final NormalizedNode input) {
        return input != null ? input
                : ImmutableNodes.containerNode(YangConstants.operationInputQName(type.getModule()));
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
    public static DOMActionResult invokeAction(final ContainerNode data,
            final Absolute schemaPath, final YangInstanceIdentifier yangIId, final DOMMountPoint mountPoint) {
        return invokeAction(data, schemaPath, yangIId, mountPoint.getService(DOMActionService.class)
            .orElseThrow(() -> new RestconfDocumentedException("DomAction service is missing.")));
    }

    /**
     * Invoke Action via ActionServiceHandler.
     *
     * @param data
     *             input data
     * @param schemaPath
     *             schema path of data
     * @param actionService
     *             action service to invoke action
     * @return {@link DOMActionResult}
     */
    public static DOMActionResult invokeAction(final ContainerNode data, final Absolute schemaPath,
            final YangInstanceIdentifier yangIId, final DOMActionService actionService) {
        final ListenableFuture<? extends DOMActionResult> future = actionService.invokeAction(schemaPath,
            new DOMDataTreeIdentifier(LogicalDatastoreType.OPERATIONAL, yangIId.getParent()), data);
        final ActionResultFactory dataFactory = new ActionResultFactory();
        FutureCallbackTx.addCallback(future, PostDataTransactionUtil.POST_TX_TYPE, dataFactory);
        return dataFactory.build();
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
}
