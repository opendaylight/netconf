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
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.RestconfStrategy;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;

/**
 * Util class for delete specific data in config DS.
 */
public final class DeleteDataTransactionUtil {

    private DeleteDataTransactionUtil() {
        throw new UnsupportedOperationException("Util class.");
    }

    /**
     * Delete data from DS via transaction.
     *
     * @param strategy object that perform the actual DS operations
     * @return {@link Response}
     */
    public static Response deleteData(final RestconfStrategy strategy) {
        strategy.prepareExecution();
        final YangInstanceIdentifier path = strategy.getInstanceIdentifier().getInstanceIdentifier();
        TransactionUtil.checkItemExists(strategy, LogicalDatastoreType.CONFIGURATION, path,
                RestconfDataServiceConstant.DeleteData.DELETE_TX_TYPE);
        strategy.delete(LogicalDatastoreType.CONFIGURATION, path);
        final FluentFuture<? extends CommitInfo> future = strategy.commit();
        final ResponseFactory response = new ResponseFactory(Status.NO_CONTENT);
        //This method will close transactionChain if any
        FutureCallbackTx.addCallback(future, RestconfDataServiceConstant.DeleteData.DELETE_TX_TYPE, response,
                strategy.getTransactionChain());
        return response.build();
    }
}
