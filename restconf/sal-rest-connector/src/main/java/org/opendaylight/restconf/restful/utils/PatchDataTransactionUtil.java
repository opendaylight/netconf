/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.restful.utils;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.CheckedFuture;
import java.util.ArrayList;
import java.util.List;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadWriteTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMDataWriteTransaction;
import org.opendaylight.netconf.sal.restconf.impl.PATCHContext;
import org.opendaylight.netconf.sal.restconf.impl.PATCHEditOperation;
import org.opendaylight.netconf.sal.restconf.impl.PATCHEntity;
import org.opendaylight.netconf.sal.restconf.impl.PATCHStatusContext;
import org.opendaylight.netconf.sal.restconf.impl.PATCHStatusEntity;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorTag;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorType;
import org.opendaylight.restconf.RestConnectorProvider;
import org.opendaylight.restconf.common.references.SchemaContextRef;
import org.opendaylight.restconf.restful.transaction.TransactionVarsWrapper;
import org.opendaylight.restconf.restful.utils.RestconfDataServiceConstant.PatchData;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PatchDataTransactionUtil {
    private static final Logger LOG = LoggerFactory.getLogger(PatchDataTransactionUtil.class);

    public PatchDataTransactionUtil() {
        throw new UnsupportedOperationException("Util class.");
    }

    /**
     * Process edit operations of one {@link PATCHContext}.
     * @param context Patch context to be processed
     * @param transactionNode Wrapper for transaction
     * @param schemaContextRef Soft reference for global schema context
     * @return {@link PATCHStatusContext}
     */
    public static PATCHStatusContext patchData(final PATCHContext context, final TransactionVarsWrapper transactionNode,
                                               final SchemaContextRef schemaContextRef) {
        final List<PATCHStatusEntity> editCollection = new ArrayList<>();
        boolean noError = true;
        final DOMDataReadWriteTransaction tx = transactionNode.getTransactionChain().newReadWriteTransaction();

        for (final PATCHEntity patchEntity : context.getData()) {
            final PATCHEditOperation operation = PATCHEditOperation.valueOf(patchEntity.getOperation().toUpperCase());
            if (noError) {
                switch (operation) {
                    case CREATE:
                        try {
                            createDataWithinTransaction(LogicalDatastoreType.CONFIGURATION,
                                    patchEntity.getTargetNode(), patchEntity.getNode(), tx, schemaContextRef);
                            editCollection.add(new PATCHStatusEntity(patchEntity.getEditId(), true, null));
                        } catch (final RestconfDocumentedException e) {
                            editCollection.add(new PATCHStatusEntity(patchEntity.getEditId(),
                                    false, Lists.newArrayList(e.getErrors())));
                            noError = false;
                        }
                        break;
                    case DELETE:
                        try {
                            deleteDataWithinTransaction(LogicalDatastoreType.CONFIGURATION, patchEntity.getTargetNode(),
                                    tx);
                            editCollection.add(new PATCHStatusEntity(patchEntity.getEditId(), true, null));
                        } catch (final RestconfDocumentedException e) {
                            editCollection.add(new PATCHStatusEntity(patchEntity.getEditId(),
                                    false, Lists.newArrayList(e.getErrors())));
                            noError = false;
                        }
                        break;
                    case MERGE:
                        try {
                            mergeDataWithinTransaction(LogicalDatastoreType.CONFIGURATION,
                                    patchEntity.getTargetNode(), patchEntity.getNode(), tx, schemaContextRef);
                            editCollection.add(new PATCHStatusEntity(patchEntity.getEditId(), true, null));
                        } catch (final RestconfDocumentedException e) {
                            editCollection.add(new PATCHStatusEntity(patchEntity.getEditId(),
                                    false, Lists.newArrayList(e.getErrors())));
                            noError = false;
                        }
                        break;
                    case REPLACE:
                        try {
                            replaceDataWithinTransaction(LogicalDatastoreType.CONFIGURATION,
                                    patchEntity.getTargetNode(), patchEntity.getNode(), schemaContextRef, tx);
                            editCollection.add(new PATCHStatusEntity(patchEntity.getEditId(), true, null));
                        } catch (final RestconfDocumentedException e) {
                            editCollection.add(new PATCHStatusEntity(patchEntity.getEditId(),
                                    false, Lists.newArrayList(e.getErrors())));
                            noError = false;
                        }
                        break;
                    case REMOVE:
                        try {
                            removeDataWithinTransaction(LogicalDatastoreType.CONFIGURATION, patchEntity.getTargetNode(),
                                    tx);
                            editCollection.add(new PATCHStatusEntity(patchEntity.getEditId(), true, null));
                        } catch (final RestconfDocumentedException e) {
                            editCollection.add(new PATCHStatusEntity(patchEntity.getEditId(),
                                    false, Lists.newArrayList(e.getErrors())));
                            noError = false;
                        }
                        break;
                    default:
                        editCollection.add(new PATCHStatusEntity(patchEntity.getEditId(),
                                false, Lists.newArrayList(new RestconfError(ErrorType.PROTOCOL,
                                ErrorTag.OPERATION_NOT_SUPPORTED, "Not supported Yang PATCH operation"))));
                        noError = false;
                        break;
                }
            } else {
                break;
            }
        }

        // if no errors then submit transaction, otherwise cancel
        if (noError) {
            final ResponseFactory response = new ResponseFactory();
            final CheckedFuture<Void, TransactionCommitFailedException> future = tx.submit();

            try {
                FutureCallbackTx.addCallback(future, PatchData.PATCH_TX_TYPE, response);
            } catch (final RestconfDocumentedException e) {
                // if errors occurred during transaction commit then patch failed and global errors are reported
                return new PATCHStatusContext(context.getPatchId(), ImmutableList.copyOf(editCollection), false,
                        Lists.newArrayList(e.getErrors()));
            }

            return new PATCHStatusContext(context.getPatchId(), ImmutableList.copyOf(editCollection), true, null);
        } else {
            tx.cancel();
            RestConnectorProvider.resetTransactionChainForAdapaters(transactionNode.getTransactionChain());
            return new PATCHStatusContext(context.getPatchId(), ImmutableList.copyOf(editCollection),
                    false, null);
        }
    }

    /**
     * Create data within one transaction, return error if already exists.
     * @param dataStore Datastore to write data to
     * @param path Path for data to be created
     * @param payload Data to be created
     * @param rWTransaction Transaction
     * @param schemaContextRef Soft reference for global schema context
     */
    private static void createDataWithinTransaction(final LogicalDatastoreType dataStore,
                                                    final YangInstanceIdentifier path,
                                                    final NormalizedNode<?, ?> payload,
                                                    final DOMDataReadWriteTransaction rWTransaction,
                                                    final SchemaContextRef schemaContextRef) {
        LOG.trace("POST {} within Restconf PATCH: {} with payload {}", dataStore.name(), path, payload);
        createData(payload, schemaContextRef.get(), path, rWTransaction, dataStore, true);
    }

    /**
     * Check if data exists and remove it within one transaction.
     * @param dataStore Datastore to delete data from
     * @param path Path for data to be deleted
     * @param readWriteTransaction Transaction
     */
    private static void deleteDataWithinTransaction(final LogicalDatastoreType dataStore,
                                                    final YangInstanceIdentifier path,
                                                    final DOMDataReadWriteTransaction readWriteTransaction) {
        LOG.trace("Delete {} within Restconf PATCH: {}", dataStore.name(), path);
        checkItemExistsWithinTransaction(readWriteTransaction, dataStore, path);
        readWriteTransaction.delete(dataStore, path);
    }

    /**
     * Merge data within one transaction.
     * @param dataStore Datastore to merge data to
     * @param path Path for data to be merged
     * @param payload Data to be merged
     * @param writeTransaction Transaction
     * @param schemaContextRef Soft reference for global schema context
     */
    private static void mergeDataWithinTransaction(final LogicalDatastoreType dataStore,
                                                   final YangInstanceIdentifier path,
                                                   final NormalizedNode<?, ?> payload,
                                                   final DOMDataReadWriteTransaction writeTransaction,
                                                   final SchemaContextRef schemaContextRef) {
        LOG.trace("Merge {} within Restconf PATCH: {} with payload {}", dataStore.name(), path, payload);
        TransactionUtil.ensureParentsByMerge(path, schemaContextRef.get(), writeTransaction);

        // merging is necessary only for lists otherwise we can call put method
        if (payload instanceof MapNode) {
            writeTransaction.merge(dataStore, path, payload);
        } else {
            writeTransaction.put(dataStore, path, payload);
        }
    }

    /**
     * Do NOT check if data exists and remove it within one transaction.
     * @param dataStore Datastore to delete data from
     * @param path Path for data to be deleted
     * @param writeTransaction Transaction
     */
    private static void removeDataWithinTransaction(final LogicalDatastoreType dataStore,
                                                    final YangInstanceIdentifier path,
                                                    final DOMDataWriteTransaction writeTransaction) {
        LOG.trace("Remove {} within Restconf PATCH: {}", dataStore.name(), path);
        writeTransaction.delete(dataStore, path);
    }

    /**
     * Create data within one transaction, replace if already exists.
     * @param dataStore Datastore to write data to
     * @param path Path for data to be created
     * @param payload Data to be created
     * @param schemaContextRef Soft reference for global schema context
     * @param rWTransaction Transaction
     */
    private static void replaceDataWithinTransaction(final LogicalDatastoreType dataStore,
                                                     final YangInstanceIdentifier path,
                                                     final NormalizedNode<?, ?> payload,
                                                     final SchemaContextRef schemaContextRef,
                                                     final DOMDataReadWriteTransaction rWTransaction) {
        LOG.trace("PUT {} within Restconf PATCH: {} with payload {}", dataStore.name(), path, payload);
        createData(payload, schemaContextRef.get(), path, rWTransaction, dataStore, false);
    }

    /**
     * Create data within one transaction. If {@code errorIfExists} is set to {@code true} then data will be checked
     * for existence before created, otherwise they will be overwritten.
     * @param payload Data to be created
     * @param schemaContext Global schema context
     * @param path Path for data to be created
     * @param rWTransaction Transaction
     * @param dataStore Datastore to write data to
     * @param errorIfExists Enable checking for existence of data (throws error if already exists)
     */
    private static void createData(final NormalizedNode<?, ?> payload, final SchemaContext schemaContext,
                                   final YangInstanceIdentifier path, final DOMDataReadWriteTransaction rWTransaction,
                                   final LogicalDatastoreType dataStore, final boolean errorIfExists) {
        if (payload instanceof MapNode) {
            final NormalizedNode<?, ?> emptySubtree = ImmutableNodes.fromInstanceId(schemaContext, path);
            rWTransaction.merge(dataStore, YangInstanceIdentifier.create(emptySubtree.getIdentifier()), emptySubtree);
            TransactionUtil.ensureParentsByMerge(path, schemaContext, rWTransaction);
            for (final MapEntryNode child : ((MapNode) payload).getValue()) {
                final YangInstanceIdentifier childPath = path.node(child.getIdentifier());

                if (errorIfExists) {
                    checkItemDoesNotExistsWithinTransaction(rWTransaction, dataStore, childPath);
                }

                rWTransaction.put(dataStore, childPath, child);
            }
        } else {
            if (errorIfExists) {
                checkItemDoesNotExistsWithinTransaction(rWTransaction, dataStore, path);
            }

            TransactionUtil.ensureParentsByMerge(path, schemaContext, rWTransaction);
            rWTransaction.put(dataStore, path, payload);
        }
    }

    /**
     * Check if items already exists at specified {@code path}. Throws {@link RestconfDocumentedException} if
     * data does NOT already exists.
     * @param rWTransaction Transaction
     * @param store Datastore
     * @param path Path to be checked
     */
    public static void checkItemExistsWithinTransaction(final DOMDataReadWriteTransaction rWTransaction,
                                                        final LogicalDatastoreType store, final YangInstanceIdentifier path) {
        final CheckedFuture<Boolean, ReadFailedException> future = rWTransaction.exists(store, path);
        final FutureDataFactory<Boolean> response = new FutureDataFactory<>();

        FutureCallbackTx.addCallback(future, PatchData.PATCH_TX_TYPE, response);

        if (!response.result) {
            final String errMsg = "Operation via Restconf was not executed because data does not exist";
            LOG.trace("{}:{}", errMsg, path);
            throw new RestconfDocumentedException(
                    "Data does not exist", ErrorType.PROTOCOL, ErrorTag.DATA_MISSING, path);
        }
    }

    /**
     * Check if items do NOT already exists at specified {@code path}. Throws {@link RestconfDocumentedException} if
     * data already exists.
     * @param rWTransaction Transaction
     * @param store Datastore
     * @param path Path to be checked
     */
    public static void checkItemDoesNotExistsWithinTransaction(final DOMDataReadWriteTransaction rWTransaction,
                                                               final LogicalDatastoreType store, final YangInstanceIdentifier path) {
        final CheckedFuture<Boolean, ReadFailedException> future = rWTransaction.exists(store, path);
        final FutureDataFactory<Boolean> response = new FutureDataFactory<>();

        FutureCallbackTx.addCallback(future, PatchData.PATCH_TX_TYPE, response);

        if (response.result) {
            final String errMsg = "Operation via Restconf was not executed because data already exists";
            LOG.trace("{}:{}", errMsg, path);
            throw new RestconfDocumentedException(
                    "Data already exists", ErrorType.PROTOCOL, ErrorTag.DATA_EXISTS, path);
        }
    }
}