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
import org.opendaylight.restconf.common.patch.PatchEntity;
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
    // FIXME: why is this used from other contexts?
    static final String PATCH_TX_TYPE = "Patch";

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
        boolean noError = true;
        final RestconfTransaction transaction = strategy.prepareWriteExecution();

        for (final PatchEntity patchEntity : context.getData()) {
            if (noError) {
                switch (patchEntity.getOperation()) {
                    case CREATE:
                        try {
                            createDataWithinTransaction(patchEntity.getTargetNode(), patchEntity.getNode(),
                                schemaContext, transaction);
                            editCollection.add(new PatchStatusEntity(patchEntity.getEditId(), true, null));
                        } catch (final RestconfDocumentedException e) {
                            editCollection.add(new PatchStatusEntity(patchEntity.getEditId(), false, e.getErrors()));
                            noError = false;
                        }
                        break;
                    case DELETE:
                        try {
                            deleteDataWithinTransaction(patchEntity.getTargetNode(), transaction);
                            editCollection.add(new PatchStatusEntity(patchEntity.getEditId(), true, null));
                        } catch (final RestconfDocumentedException e) {
                            editCollection.add(new PatchStatusEntity(patchEntity.getEditId(), false, e.getErrors()));
                            noError = false;
                        }
                        break;
                    case MERGE:
                        try {
                            mergeDataWithinTransaction(patchEntity.getTargetNode(), patchEntity.getNode(),
                                schemaContext, transaction);
                            editCollection.add(new PatchStatusEntity(patchEntity.getEditId(), true, null));
                        } catch (final RestconfDocumentedException e) {
                            editCollection.add(new PatchStatusEntity(patchEntity.getEditId(), false, e.getErrors()));
                            noError = false;
                        }
                        break;
                    case REPLACE:
                        try {
                            replaceDataWithinTransaction(patchEntity.getTargetNode(), patchEntity.getNode(),
                                schemaContext, transaction);
                            editCollection.add(new PatchStatusEntity(patchEntity.getEditId(), true, null));
                        } catch (final RestconfDocumentedException e) {
                            editCollection.add(new PatchStatusEntity(patchEntity.getEditId(), false, e.getErrors()));
                            noError = false;
                        }
                        break;
                    case REMOVE:
                        try {
                            removeDataWithinTransaction(patchEntity.getTargetNode(), transaction);
                            editCollection.add(new PatchStatusEntity(patchEntity.getEditId(), true, null));
                        } catch (final RestconfDocumentedException e) {
                            editCollection.add(new PatchStatusEntity(patchEntity.getEditId(), false, e.getErrors()));
                            noError = false;
                        }
                        break;
                    default:
                        editCollection.add(new PatchStatusEntity(patchEntity.getEditId(), false,
                            List.of(new RestconfError(ErrorType.PROTOCOL, ErrorTag.OPERATION_NOT_SUPPORTED,
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
                TransactionUtil.syncCommit(transaction.commit(), PATCH_TX_TYPE, null);
            } catch (RestconfDocumentedException e) {
                // if errors occurred during transaction commit then patch failed and global errors are reported
                return new PatchStatusContext(context.getPatchId(), List.copyOf(editCollection), false, e.getErrors());
            }

            return new PatchStatusContext(context.getPatchId(), List.copyOf(editCollection), true, null);
        } else {
            transaction.cancel();
            return new PatchStatusContext(context.getPatchId(), List.copyOf(editCollection), false, null);
        }
    }

    /**
     * Create data within one transaction, return error if already exists.
     *
     * @param path          Path for data to be created
     * @param payload       Data to be created
     * @param transaction   A handle to a set of DS operations
     */
    private static void createDataWithinTransaction(final YangInstanceIdentifier path, final NormalizedNode payload,
                                                    final EffectiveModelContext schemaContext,
                                                    final RestconfTransaction transaction) {
        LOG.trace("POST {} within Restconf Patch: {} with payload {}", LogicalDatastoreType.CONFIGURATION, path,
            payload);
        transaction.create(path, payload, schemaContext);
    }

    /**
     * Remove data within one transaction.
     *
     * @param path     Path for data to be deleted
     * @param transaction   A handle to a set of DS operations
     */
    private static void deleteDataWithinTransaction(final YangInstanceIdentifier path,
                                                    final RestconfTransaction transaction) {
        LOG.trace("Delete {} within Restconf Patch: {}", LogicalDatastoreType.CONFIGURATION, path);
        transaction.delete(path);
    }

    /**
     * Merge data within one transaction.
     *
     * @param path     Path for data to be merged
     * @param payload  Data to be merged
     * @param transaction   A handle to a set of DS operations
     */
    private static void mergeDataWithinTransaction(final YangInstanceIdentifier path, final NormalizedNode payload,
                                                   final EffectiveModelContext schemaContext,
                                                   final RestconfTransaction transaction) {
        LOG.trace("Merge {} within Restconf Patch: {} with payload {}", LogicalDatastoreType.CONFIGURATION, path,
            payload);
        TransactionUtil.ensureParentsByMerge(path, schemaContext, transaction);
        transaction.merge(path, payload);
    }

    /**
     * Do NOT check if data exists and remove it within one transaction.
     *
     * @param path     Path for data to be deleted
     * @param transaction   A handle to a set of DS operations
     */
    private static void removeDataWithinTransaction(final YangInstanceIdentifier path,
                                                    final RestconfTransaction transaction) {
        LOG.trace("Remove {} within Restconf Patch: {}", LogicalDatastoreType.CONFIGURATION, path);
        transaction.remove(path);
    }

    /**
     * Create data within one transaction, replace if already exists.
     *
     * @param path          Path for data to be created
     * @param payload       Data to be created
     * @param transaction   A handle to a set of DS operations
     */
    private static void replaceDataWithinTransaction(final YangInstanceIdentifier path, final NormalizedNode payload,
                                                     final EffectiveModelContext schemaContext,
                                                     final RestconfTransaction transaction) {
        LOG.trace("PUT {} within Restconf Patch: {} with payload {}", LogicalDatastoreType.CONFIGURATION, path,
            payload);
        transaction.replace(path, payload, schemaContext);
    }
}
