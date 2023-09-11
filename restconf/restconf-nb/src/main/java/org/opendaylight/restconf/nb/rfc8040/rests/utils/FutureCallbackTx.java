/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.utils;

import com.google.common.base.Throwables;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.opendaylight.mdsal.common.api.TransactionCommitFailedException;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.api.NetconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Add callback for future objects and result set to the data factory.
 */
final class FutureCallbackTx {
    private static final Logger LOG = LoggerFactory.getLogger(FutureCallbackTx.class);

    private FutureCallbackTx() {
        // Hidden on purpose
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
    // FIXME: this is a *synchronous operation* and has to die
    static <T> void addCallback(final ListenableFuture<T> listenableFuture, final String txType,
                                final FutureDataFactory<? super T> dataFactory) throws RestconfDocumentedException {
        addCallback(listenableFuture, txType, dataFactory, null, null);
    }

    /**
     * Add callback to the future object and close transaction chain.
     *
     * @param listenableFuture
     *             future object
     * @param txType
     *             type of operation (READ, POST, PUT, DELETE)
     * @param dataFactory
     *             factory setting result
     * @param path unique identifier of a particular node instance in the data tree
     * @throws RestconfDocumentedException
     *             if the Future throws an exception
     */
    // FIXME: this is a *synchronous operation* and has to die
    static <T> void addCallback(final ListenableFuture<T> listenableFuture, final String txType,
            final FutureDataFactory<? super T> dataFactory, final YangInstanceIdentifier path,
            final EffectiveModelContext context) throws RestconfDocumentedException {
        try {
            final T result = listenableFuture.get();
            dataFactory.setResult(result);
            LOG.trace("Transaction({}) SUCCESSFUL", txType);
        } catch (InterruptedException e) {
            dataFactory.setFailureStatus();
            LOG.warn("Transaction({}) FAILED!", txType, e);
            throw new RestconfDocumentedException("Transaction failed", e);
        } catch (ExecutionException e) {
            dataFactory.setFailureStatus();
            LOG.warn("Transaction({}) FAILED!", txType, e);

            final Throwable cause = e.getCause();
            if (cause instanceof TransactionCommitFailedException) {
                /* If device send some error message we want this message to get to client
                   and not just to throw it away or override it with new generic message.
                   We search for NetconfDocumentedException that was send from netconfSB
                   and we create RestconfDocumentedException accordingly.
                */
                final List<Throwable> causalChain = Throwables.getCausalChain(cause);
                for (Throwable error : causalChain) {
                    if (error instanceof DocumentedException) {
                        final ErrorTag errorTag = ((DocumentedException) error).getErrorTag();
                        if (errorTag.equals(ErrorTag.DATA_EXISTS)) {
                            LOG.trace("Operation via Restconf was not executed because data at {} already exists",
                                path);
                            throw new RestconfDocumentedException(e, new RestconfError(ErrorType.PROTOCOL,
                                ErrorTag.DATA_EXISTS, "Data already exists", path), context);
                        } else if (errorTag.equals(ErrorTag.DATA_MISSING)) {
                            LOG.trace("Operation via Restconf was not executed because data at {} does not exist",
                                path);
                            throw new RestconfDocumentedException(e, new RestconfError(ErrorType.PROTOCOL,
                                ErrorTag.DATA_MISSING, "Data does not exist", path), context);
                        }
                    }
                    if (error instanceof NetconfDocumentedException) {
                        throw new RestconfDocumentedException(error.getMessage(),
                                ((NetconfDocumentedException) error).getErrorType(),
                                ((NetconfDocumentedException) error).getErrorTag(), e);
                    }
                }

                throw new RestconfDocumentedException("Transaction(" + txType + ") not committed correctly", e);
            } else {
                throw new RestconfDocumentedException("Transaction failed", e);
            }
        }
    }
}
