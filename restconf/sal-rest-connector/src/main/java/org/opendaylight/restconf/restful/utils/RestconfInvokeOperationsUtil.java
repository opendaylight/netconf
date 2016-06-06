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
import java.util.concurrent.ExecutionException;
import javax.ws.rs.core.Response.Status;
import org.opendaylight.controller.md.sal.dom.api.DOMMountPoint;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcException;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcResult;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorTag;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorType;
import org.opendaylight.restconf.common.handlers.api.RpcServiceHandler;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Util class for rpc
 *
 */
public class RestconfInvokeOperationsUtil {

    private static final Logger LOG = LoggerFactory.getLogger(RestconfInvokeOperationsUtil.class);

    private RestconfInvokeOperationsUtil() {
        throw new UnsupportedOperationException("Util class");
    }

    /**
     * Invoking rpc via mount point
     *
     * @param mountPoint
     *            - mount point
     * @param data
     *            - input data
     * @param schemaPath
     *            - schema path of data
     * @return {@link CheckedFuture}
     */
    public static CheckedFuture<DOMRpcResult, DOMRpcException> invokeRpcViaMountPoint(final DOMMountPoint mountPoint,
            final NormalizedNode<?, ?> data,
            final SchemaPath schemaPath) {
        final Optional<DOMRpcService> mountPointService = mountPoint.getService(DOMRpcService.class);
        if (mountPointService.isPresent()) {
            return mountPointService.get().invokeRpc(schemaPath, data);
        }
        final String errmsg = "RPC service is missing.";
        LOG.debug(errmsg);
        throw new RestconfDocumentedException(errmsg);
    }

    /**
     * Invoke rpc
     *
     * @param data
     *            - input data
     * @param schemaPath
     *            - schema path of data
     * @param rpcServiceHandler
     *            - rpc service handler to invoke rpc
     * @return {@link CheckedFuture}
     */
    public static CheckedFuture<DOMRpcResult, DOMRpcException> invokeRpc(final NormalizedNode<?, ?> data,
            final SchemaPath schemaPath,
            final RpcServiceHandler rpcServiceHandler) {
        final DOMRpcService rpcService = rpcServiceHandler.getRpcService();
        if (rpcService == null) {
            throw new RestconfDocumentedException(Status.SERVICE_UNAVAILABLE);
        }

        return rpcService.invokeRpc(schemaPath, data);

    }

    /**
     * Check the validity of the result
     *
     * @param response
     *            - response of rpc
     * @return {@link DOMRpcResult} result
     */
    public static DOMRpcResult checkResponse(final CheckedFuture<DOMRpcResult, DOMRpcException> response) {
        if (response == null) {
            return null;
        }
        try {
            final DOMRpcResult result = response.get();
            if ((result.getErrors() == null) || result.getErrors().isEmpty()) {
                return result;
            }
            LOG.debug("RpcError message", result.getErrors());
            throw new RestconfDocumentedException("RPCerror message ", null, result.getErrors());
        } catch (final InterruptedException e) {
            final String errMsg = "The operation was interrupted while executing and did not complete.";
            LOG.debug(errMsg);
            throw new RestconfDocumentedException(errMsg, ErrorType.RPC, ErrorTag.PARTIAL_OPERATION);
        } catch (final ExecutionException e) {
            final String errMsg = "The operation encountered an unexpected error while executing";
            LOG.debug(errMsg, e);
            throw new RestconfDocumentedException(errMsg, e);
        } catch (final CancellationException e) {
            final String errMsg = "The operation was cancelled while executing.";
            LOG.debug("Cancel RpcExecution: " + errMsg, e);
            throw new RestconfDocumentedException(errMsg, ErrorType.RPC, ErrorTag.PARTIAL_OPERATION);
        }
    }

}
