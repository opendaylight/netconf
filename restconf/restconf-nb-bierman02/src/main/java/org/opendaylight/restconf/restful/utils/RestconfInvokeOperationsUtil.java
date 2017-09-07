/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.restful.utils;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import java.util.concurrent.CancellationException;
import javax.ws.rs.core.Response.Status;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPoint;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcException;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcResult;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorTag;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorType;
import org.opendaylight.restconf.handlers.RpcServiceHandler;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link Deprecated} move to splitted module restconf-nb-rfc8040. Util class for rpc.
 *
 */
@Deprecated
public class RestconfInvokeOperationsUtil {

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
     * @return {@link CheckedFuture}
     */
    public static DOMRpcResult invokeRpcViaMountPoint(final DOMMountPoint mountPoint, final NormalizedNode<?, ?> data,
            final SchemaPath schemaPath) {
        final Optional<DOMRpcService> mountPointService = mountPoint.getService(DOMRpcService.class);
        if (mountPointService.isPresent()) {
            final CheckedFuture<DOMRpcResult, DOMRpcException> rpc = mountPointService.get().invokeRpc(schemaPath,
                    data);
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
     * @return {@link CheckedFuture}
     */
    public static DOMRpcResult invokeRpc(final NormalizedNode<?, ?> data, final SchemaPath schemaPath,
            final RpcServiceHandler rpcServiceHandler) {
        final DOMRpcService rpcService = rpcServiceHandler.get();
        if (rpcService == null) {
            throw new RestconfDocumentedException(Status.SERVICE_UNAVAILABLE);
        }

        final CheckedFuture<DOMRpcResult, DOMRpcException> rpc = rpcService.invokeRpc(schemaPath, data);
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
            LOG.debug("RpcError message", response.getErrors());
            throw new RestconfDocumentedException("RPCerror message ", null, response.getErrors());
        } catch (final CancellationException e) {
            final String errMsg = "The operation was cancelled while executing.";
            LOG.debug("Cancel RpcExecution: " + errMsg, e);
            throw new RestconfDocumentedException(errMsg, ErrorType.RPC, ErrorTag.PARTIAL_OPERATION);
        }
    }

    private static DOMRpcResult prepareResult(final CheckedFuture<DOMRpcResult, DOMRpcException> rpc) {
        final RpcResultFactory dataFactory = new RpcResultFactory();
        FutureCallbackTx.addCallback(rpc, RestconfDataServiceConstant.PostData.POST_TX_TYPE, dataFactory);
        return dataFactory.build();
    }
}
