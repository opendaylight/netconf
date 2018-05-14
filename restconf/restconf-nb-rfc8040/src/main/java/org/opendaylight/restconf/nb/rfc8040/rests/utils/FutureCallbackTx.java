/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.utils;

import com.google.common.base.Throwables;
import com.google.common.util.concurrent.CheckedFuture;
import java.util.ArrayList;
import java.util.List;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcException;
import org.opendaylight.controller.md.sal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.netconf.api.NetconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError;
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
            } else if (e instanceof TransactionCommitFailedException) {
                /* If device send some error message we want this message to get to client
                   and not just to throw it away or override it with new generic message.
                   We search for NetconfDocumentedException that was send from netconfSB
                   and we create RestconfDocumentedException accordingly.
                */
                final List<Throwable> causalChain = Throwables.getCausalChain(e);
                for (Throwable error : causalChain) {
                    if (error instanceof NetconfDocumentedException) {
                        throw new RestconfDocumentedException(error.getMessage(),
                                RestconfError.ErrorType.valueOfCaseInsensitive(
                                        ((NetconfDocumentedException) error).getErrorType().getTypeValue()),
                                RestconfError.ErrorTag.valueOfCaseInsensitive(
                                        ((NetconfDocumentedException) error).getErrorTag().getTagValue()), e);
                    }
                }

                throw new RestconfDocumentedException("Transaction(" + txType + ") not committed correctly", e);
            } else {
                throw new RestconfDocumentedException("Transaction failed", e);
            }
        }
    }
}
