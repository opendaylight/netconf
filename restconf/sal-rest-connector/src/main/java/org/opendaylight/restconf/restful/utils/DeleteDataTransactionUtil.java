/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.restful.utils;

import com.google.common.util.concurrent.CheckedFuture;
import javax.ws.rs.core.Response;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadWriteTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMTransactionChain;
import org.opendaylight.restconf.restful.transaction.TransactionVarsWrapper;
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
        final CheckedFuture<Void, TransactionCommitFailedException> future = submitData(
                transactionNode.getTransactionChain(), transactionNode.getTransactionChain().newReadWriteTransaction(),
                transactionNode.getInstanceIdentifier().getInstanceIdentifier());
        final ResponseFactory response = new ResponseFactory();
        FutureCallbackTx.addCallback(future, RestconfDataServiceConstant.DeleteData.DELETE_TX_TYPE, response);
        return response.build();
    }

    /**
     * Delete data via transaction. Return error if data to delete does not exist.
     *
     * @param transactionChain
     *             transaction chain
     * @param readWriteTx
     *             read and write transaction
     * @param path
     *             path of data to delete
     * @return {@link CheckedFuture}
     */
    private static CheckedFuture<Void, TransactionCommitFailedException> submitData(
            final DOMTransactionChain transactionChain, final DOMDataReadWriteTransaction readWriteTx,
            final YangInstanceIdentifier path) {
        TransactionUtil.checkItemExists(transactionChain, readWriteTx, LogicalDatastoreType.CONFIGURATION, path,
                RestconfDataServiceConstant.DeleteData.DELETE_TX_TYPE);
        readWriteTx.delete(LogicalDatastoreType.CONFIGURATION, path);
        return readWriteTx.submit();
    }
}
