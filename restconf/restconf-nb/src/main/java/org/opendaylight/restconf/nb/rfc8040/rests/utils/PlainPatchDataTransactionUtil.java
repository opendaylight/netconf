/*
 * Copyright (c) 2020 Lumina Networks, Inc. and others.  All rights reserved.
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
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.legacy.NormalizedNodePayload;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.RestconfStrategy;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.RestconfTransaction;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Util class for plain patch data to DS.
 */
public final class PlainPatchDataTransactionUtil {

    private static final Logger LOG = LoggerFactory.getLogger(PlainPatchDataTransactionUtil.class);

    private PlainPatchDataTransactionUtil() {
    }

    /**
     * Prepare variables for put data to DS. Close {@link DOMTransactionChain} if any inside of object
     * {@link RestconfStrategy} provided as a parameter if any.
     *
     * @param payload       data to put
     * @param schemaContext reference to {@link EffectiveModelContext}
     * @param strategy      object that perform the actual DS operations
     * @return {@link Response}
     */
    public static Response patchData(final NormalizedNodePayload payload,
                                     final RestconfStrategy strategy,
                                     final EffectiveModelContext schemaContext) {

        final RestconfTransaction transaction = strategy.prepareWriteExecution();
        YangInstanceIdentifier path = payload.getInstanceIdentifierContext().getInstanceIdentifier();
        NormalizedNode data = payload.getData();

        try {
            LOG.trace("Merge CONFIGURATION within Restconf Patch: {} with payload {}", path, data);
            TransactionUtil.ensureParentsByMerge(path, schemaContext, transaction);
            transaction.merge(path, data);
        } catch (final RestconfDocumentedException e) {
            transaction.cancel();
            throw new IllegalArgumentException(e);
        }

        final FluentFuture<? extends CommitInfo> future = transaction.commit();
        final ResponseFactory response = new ResponseFactory(Status.OK);

        // closes transactionChain if any, may throw
        FutureCallbackTx.addCallback(future, PatchDataTransactionUtil.PATCH_TX_TYPE, response, path, schemaContext);
        return response.build();
    }
}
