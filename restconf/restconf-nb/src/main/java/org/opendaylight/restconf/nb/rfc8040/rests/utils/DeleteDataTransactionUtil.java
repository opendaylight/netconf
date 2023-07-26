/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.utils;

import javax.ws.rs.core.Response;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.RestconfStrategy;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.RestconfTransaction;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;

/**
 * Util class for delete specific data in config DS.
 */
public final class DeleteDataTransactionUtil {
    private DeleteDataTransactionUtil() {
        // Hidden on purpose
    }

    /**
     * Delete data from DS via transaction.
     *
     * @param strategy object that perform the actual DS operations
     * @return {@link Response}
     */
    public static Response deleteData(final RestconfStrategy strategy, final YangInstanceIdentifier path) {
        final RestconfTransaction transaction = strategy.prepareWriteExecution();
        try {
            transaction.delete(path);
        } catch (RestconfDocumentedException e) {
            // close transaction if any and pass exception further
            transaction.cancel();
            throw e;
        }

        TransactionUtil.syncCommit(transaction.commit(), "DELETE", path);
        return Response.noContent().build();
    }
}
