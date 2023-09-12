/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.utils;

import java.util.ArrayList;
import java.util.List;
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.restconf.common.patch.PatchContext;
import org.opendaylight.restconf.common.patch.PatchStatusContext;
import org.opendaylight.restconf.common.patch.PatchStatusEntity;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.RestconfStrategy;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

public final class PatchDataTransactionUtil {
    private PatchDataTransactionUtil() {
        // Hidden on purpose
    }

    /**
     * Process edit operations of one {@link PatchContext}. Close {@link DOMTransactionChain} if any inside of object
     * {@link RestconfStrategy} provided as a parameter.
     *
     * @param context       Patch context to be processed
     * @param strategy      object that perform the actual DS operations
     * @param schemaContext Global schema context
     * @return {@link PatchStatusContext}
     */
    public static PatchStatusContext patchData(final PatchContext context, final RestconfStrategy strategy,
            final EffectiveModelContext schemaContext) {
        final var editCollection = new ArrayList<PatchStatusEntity>();
        final var tx = strategy.prepareWriteExecution();

        boolean noError = true;
        for (var patchEntity : context.getData()) {
            if (noError) {
                final var targetNode = patchEntity.getTargetNode();
                final var editId = patchEntity.getEditId();

                switch (patchEntity.getOperation()) {
                    case Create:
                        try {
                            tx.create(targetNode, patchEntity.getNode(), schemaContext);
                            editCollection.add(new PatchStatusEntity(editId, true, null));
                        } catch (RestconfDocumentedException e) {
                            editCollection.add(new PatchStatusEntity(editId, false, e.getErrors()));
                            noError = false;
                        }
                        break;
                    case Delete:
                        try {
                            tx.delete(targetNode);
                            editCollection.add(new PatchStatusEntity(editId, true, null));
                        } catch (RestconfDocumentedException e) {
                            editCollection.add(new PatchStatusEntity(editId, false, e.getErrors()));
                            noError = false;
                        }
                        break;
                    case Merge:
                        try {
                            TransactionUtil.ensureParentsByMerge(targetNode, schemaContext, tx);
                            tx.merge(targetNode, patchEntity.getNode());
                            editCollection.add(new PatchStatusEntity(editId, true, null));
                        } catch (RestconfDocumentedException e) {
                            editCollection.add(new PatchStatusEntity(editId, false, e.getErrors()));
                            noError = false;
                        }
                        break;
                    case Replace:
                        try {
                            tx.replace(targetNode, patchEntity.getNode(), schemaContext);
                            editCollection.add(new PatchStatusEntity(editId, true, null));
                        } catch (RestconfDocumentedException e) {
                            editCollection.add(new PatchStatusEntity(editId, false, e.getErrors()));
                            noError = false;
                        }
                        break;
                    case Remove:
                        try {
                            tx.remove(targetNode);
                            editCollection.add(new PatchStatusEntity(editId, true, null));
                        } catch (RestconfDocumentedException e) {
                            editCollection.add(new PatchStatusEntity(editId, false, e.getErrors()));
                            noError = false;
                        }
                        break;
                    default:
                        editCollection.add(new PatchStatusEntity(editId, false, List.of(
                            new RestconfError(ErrorType.PROTOCOL, ErrorTag.OPERATION_NOT_SUPPORTED,
                                "Not supported Yang Patch operation"))));
                        noError = false;
                        break;
                }
            } else {
                break;
            }
        }

        // if no errors then submit transaction, otherwise cancel
        if (noError) {
            try {
                TransactionUtil.syncCommit(tx.commit(), "PATCH", null);
            } catch (RestconfDocumentedException e) {
                // if errors occurred during transaction commit then patch failed and global errors are reported
                return new PatchStatusContext(context.getPatchId(), List.copyOf(editCollection), false, e.getErrors());
            }

            return new PatchStatusContext(context.getPatchId(), List.copyOf(editCollection), true, null);
        } else {
            tx.cancel();
            return new PatchStatusContext(context.getPatchId(), List.copyOf(editCollection), false, null);
        }
    }
}
