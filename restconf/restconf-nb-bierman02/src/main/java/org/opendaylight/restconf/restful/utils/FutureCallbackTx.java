/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.restful.utils;

import com.google.common.util.concurrent.CheckedFuture;
import java.util.ArrayList;
import java.util.List;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcException;
import org.opendaylight.controller.md.sal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Add callback for future objects and result set to the data factory.
 *
 */
final class FutureCallbackTx {

    private static final Logger LOG = LoggerFactory.getLogger(FutureCallbackTx.class);

    private FutureCallbackTx() {
        throw new UnsupportedOperationException("Util class");
    }

    /**
     * Add callback to the future object.
     *
     * @param listenableFuture
     *             future object
     * @param txType
     *             type of operation (READ, POST, PUT, DELETE)
     * @param dataFactory
     *             factory setting result
     * @throws RestconfDocumentedException
     *             if the Future throws an exception
     */
    @SuppressWarnings("checkstyle:IllegalCatch")
    static <T, X extends Exception> void addCallback(final CheckedFuture<T, X> listenableFuture, final String txType,
            final FutureDataFactory<T> dataFactory) throws RestconfDocumentedException {

        try {
            final T result = listenableFuture.checkedGet();
            dataFactory.setResult(result);
            LOG.trace("Transaction({}) SUCCESSFUL", txType);
        } catch (Exception e) {
            dataFactory.setFailureStatus();
            LOG.warn("Transaction({}) FAILED!", txType, e);
            if (e instanceof DOMRpcException) {
                final List<RpcError> rpcErrorList = new ArrayList<>();
                rpcErrorList.add(
                        RpcResultBuilder.newError(RpcError.ErrorType.RPC, "operation-failed", e.getMessage()));
                dataFactory.setResult((T) new DefaultDOMRpcResult(rpcErrorList));
            } else {
                throw new RestconfDocumentedException(
                        "Transaction(" + txType + ") not committed correctly", e);
            }
        }
    }
}
