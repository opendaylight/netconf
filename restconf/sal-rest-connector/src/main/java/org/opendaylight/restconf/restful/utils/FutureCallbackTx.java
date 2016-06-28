/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.restful.utils;

import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import javax.annotation.Nullable;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Add callback for future objects and result set to the data factory.
 *
 */
final class FutureCallbackTx {

    private final static Logger LOG = LoggerFactory.getLogger(FutureCallbackTx.class);

    private FutureCallbackTx() {
        throw new UnsupportedOperationException("Util class");
    }

    /**
     * Add callback to the future object
     *
     * @param listenableFuture
     *            - future object
     * @param txType
     *            - type of operation (READ, POST, PUT, DELETE)
     * @param dataFactory
     *            - factory setting result
     */
    static <T, X extends Exception> void addCallback(final CheckedFuture<T, X> listenableFuture, final String txType,
            final FutureDataFactory<T> dataFactory) {
        Futures.addCallback(listenableFuture, new FutureCallback<T>() {

            @Override
            public void onFailure(final Throwable t) {
                handlingLoggerAndValues(t, txType, null, null);
            }

            @Override
            public void onSuccess(final T result) {
                handlingLoggerAndValues(null, txType, result, dataFactory);
            }

        });
    }

    /**
     * Handling logger and result of callback - on success or on failure
     * <ul>
     * <li>on success - set result to the factory
     * <li>on failure - throw exception
     * </ul>
     *
     * @param t
     *            - exception - if callback is onFailure
     * @param txType
     *            - type of operation (READ, POST, PUT, DELETE)
     * @param result
     *            - result of future - if callback is on Success
     * @param dataFactory
     *            - setter for result - in callback is onSuccess
     */
    protected static <T> void handlingLoggerAndValues(@Nullable final Throwable t, final String txType,
            final T result, final FutureDataFactory<T> dataFactory) {
        if (t != null) {
            LOG.info("Transaction({}) FAILED!", txType, t);
            throw new RestconfDocumentedException("  Transaction(" + txType + ") not committed correctly", t);
        } else {
            LOG.trace("Transaction({}) SUCCESSFUL!", txType);
            dataFactory.setResult(result);
        }
    }
}
