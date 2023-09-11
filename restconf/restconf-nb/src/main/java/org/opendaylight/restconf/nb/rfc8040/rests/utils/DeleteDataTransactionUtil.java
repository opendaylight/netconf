/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.utils;

import com.google.common.util.concurrent.FluentFuture;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.RestconfStrategy;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.RestconfTransaction;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Util class for delete specific data in config DS.
 */
public final class DeleteDataTransactionUtil {
    private static final Logger LOG = LoggerFactory.getLogger(DeleteDataTransactionUtil.class);
    public static final String DELETE_TX_TYPE = "DELETE";

    private DeleteDataTransactionUtil() {
        // Hidden on purpose
    }

    /**
     * Delete data from DS via transaction.
     *
     * @param strategy object that perform the actual DS operations
     * @return {@link Response}
     */
    public static Response deleteData(final RestconfStrategy strategy, final YangInstanceIdentifier path,
            final EffectiveModelContext context) {
        final RestconfTransaction transaction = strategy.prepareWriteExecution();
        try {
            transaction.delete(path);
        } catch (RestconfDocumentedException e) {
            // close transaction if any and pass exception further
            transaction.cancel();
            throw e;
        }
        final FluentFuture<? extends CommitInfo> future = transaction.commit();
        final ResponseFactory response = new ResponseFactory(Status.NO_CONTENT);
        //This method will close transactionChain if any
        FutureCallbackTx.addCallback(future, DELETE_TX_TYPE, response, path, context);
        return response.build();
    }

    /**
     * Check if items already exists at specified {@code path}. Throws {@link RestconfDocumentedException} if
     * data does NOT already exists.
     *
     * @param isExistsFuture if checked data exists
     * @param path           Path to be checked
     * @param operationType  Type of operation (READ, POST, PUT, DELETE...)
     */
    public static void checkItemExists(final FluentFuture<Boolean> isExistsFuture,
                                       final YangInstanceIdentifier path,
                                       final String operationType) {
        final FutureDataFactory<Boolean> response = new FutureDataFactory<>();
        FutureCallbackTx.addCallback(isExistsFuture, operationType, response);

        if (!response.result) {
            LOG.trace("Operation via Restconf was not executed because data at {} does not exist", path);
            throw new RestconfDocumentedException(
                "Data does not exist", ErrorType.PROTOCOL, ErrorTag.DATA_MISSING, path);
        }
    }
}
