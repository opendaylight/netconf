/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.utils;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.FluentFuture;
import java.util.ArrayList;
import java.util.List;
import javax.ws.rs.core.Response.Status;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorTag;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorType;
import org.opendaylight.restconf.common.patch.PatchContext;
import org.opendaylight.restconf.common.patch.PatchEntity;
import org.opendaylight.restconf.common.patch.PatchStatusContext;
import org.opendaylight.restconf.common.patch.PatchStatusEntity;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.RestconfStrategy;
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
        throw new UnsupportedOperationException("Util class.");
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
        final List<PatchStatusEntity> editCollection = new ArrayList<>();
        boolean noError = true;
        strategy.prepareReadWriteExecution();

        for (final PatchEntity patchEntity : context.getData()) {
            if (noError) {
                switch (patchEntity.getOperation()) {
                    case CREATE:
                        try {
                            createDataWithinTransaction(patchEntity.getTargetNode(), patchEntity.getNode(),
                                schemaContext, strategy);
                            editCollection.add(new PatchStatusEntity(patchEntity.getEditId(), true, null));
                        } catch (final RestconfDocumentedException e) {
                            editCollection.add(new PatchStatusEntity(patchEntity.getEditId(),
                                    false, Lists.newArrayList(e.getErrors())));
                            noError = false;
                        }
                        break;
                    case DELETE:
                        try {
                            deleteDataWithinTransaction(patchEntity.getTargetNode(), strategy);
                            editCollection.add(new PatchStatusEntity(patchEntity.getEditId(), true, null));
                        } catch (final RestconfDocumentedException e) {
                            editCollection.add(new PatchStatusEntity(patchEntity.getEditId(),
                                    false, Lists.newArrayList(e.getErrors())));
                            noError = false;
                        }
                        break;
                    case MERGE:
                        try {
                            mergeDataWithinTransaction(patchEntity.getTargetNode(), patchEntity.getNode(),
                                schemaContext, strategy);
                            editCollection.add(new PatchStatusEntity(patchEntity.getEditId(), true, null));
                        } catch (final RestconfDocumentedException e) {
                            editCollection.add(new PatchStatusEntity(patchEntity.getEditId(),
                                    false, Lists.newArrayList(e.getErrors())));
                            noError = false;
                        }
                        break;
                    case REPLACE:
                        try {
                            replaceDataWithinTransaction(patchEntity.getTargetNode(), patchEntity.getNode(),
                                schemaContext, strategy);
                            editCollection.add(new PatchStatusEntity(patchEntity.getEditId(), true, null));
                        } catch (final RestconfDocumentedException e) {
                            editCollection.add(new PatchStatusEntity(patchEntity.getEditId(),
                                    false, Lists.newArrayList(e.getErrors())));
                            noError = false;
                        }
                        break;
                    case REMOVE:
                        try {
                            removeDataWithinTransaction(patchEntity.getTargetNode(), strategy);
                            editCollection.add(new PatchStatusEntity(patchEntity.getEditId(), true, null));
                        } catch (final RestconfDocumentedException e) {
                            editCollection.add(new PatchStatusEntity(patchEntity.getEditId(),
                                    false, Lists.newArrayList(e.getErrors())));
                            noError = false;
                        }
                        break;
                    default:
                        editCollection.add(new PatchStatusEntity(patchEntity.getEditId(),
                                false, Lists.newArrayList(new RestconfError(ErrorType.PROTOCOL,
                                ErrorTag.OPERATION_NOT_SUPPORTED, "Not supported Yang Patch operation"))));
                        noError = false;
                        break;
                }
            } else {
                break;
            }
        }

        // if no errors then submit transaction, otherwise cancel
        if (noError) {
            final ResponseFactory response = new ResponseFactory(Status.OK);
            final FluentFuture<? extends CommitInfo> future = strategy.commit();

            try {
                //This method will close transactionChain if any
                FutureCallbackTx.addCallback(future, PATCH_TX_TYPE, response, strategy.getTransactionChain());
            } catch (final RestconfDocumentedException e) {
                // if errors occurred during transaction commit then patch failed and global errors are reported
                return new PatchStatusContext(context.getPatchId(), ImmutableList.copyOf(editCollection), false,
                        Lists.newArrayList(e.getErrors()));
            }

            return new PatchStatusContext(context.getPatchId(), ImmutableList.copyOf(editCollection), true, null);
        } else {
            strategy.cancel();
            return new PatchStatusContext(context.getPatchId(), ImmutableList.copyOf(editCollection), false, null);
        }
    }

    /**
     * Create data within one transaction, return error if already exists.
     *
     * @param path          Path for data to be created
     * @param payload       Data to be created
     * @param strategy      Object that perform the actual DS operations
     */
    private static void createDataWithinTransaction(final YangInstanceIdentifier path,
                                                    final NormalizedNode<?, ?> payload,
                                                    final EffectiveModelContext schemaContext,
                                                    final RestconfStrategy strategy) {
        LOG.trace("POST {} within Restconf Patch: {} with payload {}", LogicalDatastoreType.CONFIGURATION.name(),
            path, payload);
        createData(payload, path, strategy, schemaContext, true);
    }

    /**
     * Remove data within one transaction.
     *
     * @param path     Path for data to be deleted
     * @param strategy Object that perform the actual DS operations
     */
    private static void deleteDataWithinTransaction(final YangInstanceIdentifier path,
                                                    final RestconfStrategy strategy) {
        LOG.trace("Delete {} within Restconf Patch: {}", LogicalDatastoreType.CONFIGURATION.name(), path);
        strategy.delete(LogicalDatastoreType.CONFIGURATION, path);
    }

    /**
     * Merge data within one transaction.
     *
     * @param path     Path for data to be merged
     * @param payload  Data to be merged
     * @param strategy Object that perform the actual DS operations
     */
    private static void mergeDataWithinTransaction(final YangInstanceIdentifier path,
                                                   final NormalizedNode<?, ?> payload,
                                                   final EffectiveModelContext schemaContext,
                                                   final RestconfStrategy strategy) {
        LOG.trace("Merge {} within Restconf Patch: {} with payload {}", LogicalDatastoreType.CONFIGURATION.name(),
            path, payload);
        TransactionUtil.ensureParentsByMerge(path, schemaContext, strategy);
        strategy.merge(LogicalDatastoreType.CONFIGURATION, path, payload);
    }

    /**
     * Do NOT check if data exists and remove it within one transaction.
     *
     * @param path     Path for data to be deleted
     * @param strategy Object that perform the actual DS operations
     */
    private static void removeDataWithinTransaction(final YangInstanceIdentifier path,
                                                    final RestconfStrategy strategy) {
        LOG.trace("Remove {} within Restconf Patch: {}", LogicalDatastoreType.CONFIGURATION.name(), path);
        strategy.remove(LogicalDatastoreType.CONFIGURATION, path);
    }

    /**
     * Create data within one transaction, replace if already exists.
     *
     * @param path          Path for data to be created
     * @param payload       Data to be created
     * @param strategy      Object that perform the actual DS operations
     */
    private static void replaceDataWithinTransaction(final YangInstanceIdentifier path,
                                                     final NormalizedNode<?, ?> payload,
                                                     final EffectiveModelContext schemaContext,
                                                     final RestconfStrategy strategy) {
        LOG.trace("PUT {} within Restconf Patch: {} with payload {}",
            LogicalDatastoreType.CONFIGURATION.name(), path, payload);
        createData(payload, path, strategy, schemaContext, false);
    }

    /**
     * Create data within one transaction. If {@code errorIfExists} is set to {@code true} then data will be checked
     * for existence before created, otherwise they will be overwritten.
     *
     * @param data          Data to be created
     * @param path          Path for data to be created
     * @param strategy      Object that perform the actual DS operations
     * @param errorIfExists Enable checking for existence of data (throws error if already exists)
     */
    private static void createData(final NormalizedNode<?, ?> data,
                                   final YangInstanceIdentifier path,
                                   final RestconfStrategy strategy,
                                   final EffectiveModelContext schemaContext,
                                   final boolean errorIfExists) {
        if (errorIfExists) {
            strategy.create(LogicalDatastoreType.CONFIGURATION, path, data, schemaContext);
        } else {
            strategy.replace(LogicalDatastoreType.CONFIGURATION, path, data, schemaContext, true);
        }
    }
}
