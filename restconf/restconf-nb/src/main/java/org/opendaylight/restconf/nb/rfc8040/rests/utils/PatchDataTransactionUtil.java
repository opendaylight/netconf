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
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.restconf.common.patch.PatchContext;
import org.opendaylight.restconf.common.patch.PatchStatusContext;
import org.opendaylight.restconf.common.patch.PatchStatusEntity;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.RestconfStrategy;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.RestconfTransaction;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PatchDataTransactionUtil {
    private static final Logger LOG = LoggerFactory.getLogger(PatchDataTransactionUtil.class);

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
                            createDataWithinTransaction(tx, targetNode, patchEntity.getNode(), schemaContext);
                            editCollection.add(new PatchStatusEntity(editId, true, null));
                        } catch (RestconfDocumentedException e) {
                            editCollection.add(new PatchStatusEntity(editId, false, e.getErrors()));
                            noError = false;
                        }
                        break;
                    case Delete:
                        try {
                            deleteDataWithinTransaction(tx, targetNode);
                            editCollection.add(new PatchStatusEntity(editId, true, null));
                        } catch (RestconfDocumentedException e) {
                            editCollection.add(new PatchStatusEntity(editId, false, e.getErrors()));
                            noError = false;
                        }
                        break;
                    case Merge:
                        try {
                            mergeDataWithinTransaction(tx, targetNode, patchEntity.getNode(), schemaContext);
                            editCollection.add(new PatchStatusEntity(editId, true, null));
                        } catch (RestconfDocumentedException e) {
                            editCollection.add(new PatchStatusEntity(editId, false, e.getErrors()));
                            noError = false;
                        }
                        break;
                    case Replace:
                        try {
                            replaceDataWithinTransaction(tx, targetNode, patchEntity.getNode(), schemaContext);
                            editCollection.add(new PatchStatusEntity(editId, true, null));
                        } catch (RestconfDocumentedException e) {
                            editCollection.add(new PatchStatusEntity(editId, false, e.getErrors()));
                            noError = false;
                        }
                        break;
                    case Remove:
                        try {
                            removeDataWithinTransaction(tx, targetNode);
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

    /**
     * Create data within one transaction, return error if already exists.
     *
     * @param tx      A handle to a set of DS operations
     * @param path    Path for data to be created
     * @param payload Data to be created
     */
    private static void createDataWithinTransaction(final RestconfTransaction tx, final YangInstanceIdentifier path,
            final NormalizedNode payload, final EffectiveModelContext schemaContext) {
        LOG.trace("POST {} within Restconf Patch: {} with payload {}", LogicalDatastoreType.CONFIGURATION, path,
            payload);
        tx.create(path, payload, schemaContext);
    }

    /**
     * Remove data within one transaction.
     *
     * @param tx   A handle to a set of DS operations
     * @param path Path for data to be deleted
     */
    private static void deleteDataWithinTransaction(final RestconfTransaction tx, final YangInstanceIdentifier path) {
        LOG.trace("Delete {} within Restconf Patch: {}", LogicalDatastoreType.CONFIGURATION, path);
        tx.delete(path);
    }

    /**
     * Merge data within one transaction.
     *
     * @param tx      A handle to a set of DS operations
     * @param path    Path for data to be merged
     * @param payload Data to be merged
     */
    private static void mergeDataWithinTransaction(final RestconfTransaction tx, final YangInstanceIdentifier path,
            final NormalizedNode payload, final EffectiveModelContext schemaContext) {
        LOG.trace("Merge {} within Restconf Patch: {} with payload {}", LogicalDatastoreType.CONFIGURATION, path,
            payload);
        TransactionUtil.ensureParentsByMerge(path, schemaContext, tx);
        tx.merge(path, payload);
    }

    /**
     * Do NOT check if data exists and remove it within one transaction.
     *
     * @param tx   A handle to a set of DS operations
     * @param path Path for data to be deleted
     */
    private static void removeDataWithinTransaction(final RestconfTransaction tx, final YangInstanceIdentifier path) {
        LOG.trace("Remove {} within Restconf Patch: {}", LogicalDatastoreType.CONFIGURATION, path);
        tx.remove(path);
    }

    /**
     * Create data within one transaction, replace if already exists.
     *
     * @param tx      A handle to a set of DS operations
     * @param path    Path for data to be created
     * @param payload Data to be created
     */
    private static void replaceDataWithinTransaction(final RestconfTransaction tx, final YangInstanceIdentifier path,
            final NormalizedNode payload, final EffectiveModelContext schemaContext) {
        LOG.trace("PUT {} within Restconf Patch: {} with payload {}", LogicalDatastoreType.CONFIGURATION, path,
            payload);
        tx.replace(path, payload, schemaContext);
    }
}
