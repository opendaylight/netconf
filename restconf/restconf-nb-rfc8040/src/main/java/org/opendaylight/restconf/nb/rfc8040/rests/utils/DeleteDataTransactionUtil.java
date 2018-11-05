/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.utils;

import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.FluentFuture;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.restconf.nb.rfc8040.handlers.TransactionChainHandler;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.TransactionVarsWrapper;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;

/**
 * Util class for delete specific data in config DS.
 *
 */
public final class DeleteDataTransactionUtil {

    private DeleteDataTransactionUtil() {
        throw new UnsupportedOperationException("Util class.");
    }

    /**
     * Delete data from DS via transaction.
     *
     * @param transactionNode
     *             Wrapper for data of transaction
     * @return {@link Response}
     */
    public static Response deleteData(final TransactionVarsWrapper transactionNode) {
        final FluentFuture<? extends CommitInfo> future = submitData(transactionNode.getTransactionChainHandler(),
                transactionNode.getInstanceIdentifier().getInstanceIdentifier());
        final ResponseFactory response = new ResponseFactory(Status.NO_CONTENT);
        FutureCallbackTx.addCallback(future, RestconfDataServiceConstant.DeleteData.DELETE_TX_TYPE, response);
        return response.build();
    }

    /**
     * Delete data via transaction. Return error if data to delete does not exist.
     *
     * @param transactionChainHandler
     *             transaction chain handler
     * @param readWriteTx
     *             read and write transaction
     * @param path
     *             path of data to delete
     * @return {@link CheckedFuture}
     */
    private static FluentFuture<? extends CommitInfo> submitData(
            final TransactionChainHandler transactionChainHandler, final YangInstanceIdentifier path) {
        final DOMDataTreeReadWriteTransaction readWriteTx = transactionChainHandler.get().newReadWriteTransaction();
        TransactionUtil.checkItemExists(transactionChainHandler, readWriteTx, LogicalDatastoreType.CONFIGURATION, path,
                RestconfDataServiceConstant.DeleteData.DELETE_TX_TYPE);
        readWriteTx.delete(LogicalDatastoreType.CONFIGURATION, path);
        return readWriteTx.commit();
    }
}
