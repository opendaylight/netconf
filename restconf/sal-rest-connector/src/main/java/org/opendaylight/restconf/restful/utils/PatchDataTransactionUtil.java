/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.restful.utils;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.CheckedFuture;
import java.util.ArrayList;
import java.util.List;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadWriteTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMDataWriteTransaction;
import org.opendaylight.netconf.sal.restconf.impl.PATCHContext;
import org.opendaylight.netconf.sal.restconf.impl.PATCHEditOperation;
import org.opendaylight.netconf.sal.restconf.impl.PATCHEntity;
import org.opendaylight.netconf.sal.restconf.impl.PATCHStatusContext;
import org.opendaylight.netconf.sal.restconf.impl.PATCHStatusEntity;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError;
import org.opendaylight.restconf.common.references.SchemaContextRef;
import org.opendaylight.restconf.restful.transaction.TransactionVarsWrapper;
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

    public PatchDataTransactionUtil() { throw new UnsupportedOperationException("Util class."); }

    public static PATCHStatusContext patchData(final PATCHContext context, final TransactionVarsWrapper transactionNode,
                                               final SchemaContextRef schemaContextRef) {
        final List<PATCHStatusEntity> editCollection = new ArrayList<>();
        final List<RestconfError> errors = new ArrayList<>();

        for (PATCHEntity patchEntity : context.getData()) {
            final PATCHEditOperation operation = PATCHEditOperation.valueOf(patchEntity.getOperation().toUpperCase());
            transactionNode.setLogicalDatastoreType(LogicalDatastoreType.CONFIGURATION);

            switch (operation) {
                case CREATE:
                    createDataWithinTransaction(transactionNode.getLogicalDatastoreType(),
                            patchEntity.getTargetNode(), patchEntity.getNode(),
                            transactionNode.getTransaction(), schemaContextRef);
                    break;
                case DELETE:
                    deleteDataWithinTransaction(transactionNode.getLogicalDatastoreType(),
                            patchEntity.getTargetNode(), transactionNode.getTransaction());
                    break;
                case INSERT:
                    //throw new UnsupportedFormatException("Not yet supported");
                    break;
                case MERGE:
                    mergeDataWithinTransaction(transactionNode.getLogicalDatastoreType(),
                            patchEntity.getTargetNode(), patchEntity.getNode(), transactionNode.getTransaction(),
                            schemaContextRef);
                    break;
                case MOVE:
                    //throw new NotSupportedException("Not yet supported");
                    break;
                case REPLACE:
                    replaceDataWithinTransaction(transactionNode.getLogicalDatastoreType(), patchEntity.getTargetNode(),
                            patchEntity.getNode(), schemaContextRef, transactionNode.getTransaction());
                    break;
                case REMOVE:
                    removeDataWithinTransaction(transactionNode.getLogicalDatastoreType(),
                            patchEntity.getTargetNode(), transactionNode.getTransaction());
                default:
                    break;
            }
        }


        final CheckedFuture<Void, TransactionCommitFailedException> future = transactionNode.getTransaction().submit();

        final ResponseFactory response = new ResponseFactory();
        FutureCallbackTx.addCallback(future, "PATCH", response);

        // close transaction?
        return new PATCHStatusContext(context.getPatchId(), ImmutableList.copyOf(editCollection),
                errors.isEmpty(), errors);
    }

    /**
     * Create data, return error if already exists.
     * @param dataStore
     * @param path
     * @param payload
     * @param rWTransaction
     * @param schemaContextRef
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
     * Check if data exists and remove it.
     * @param dataStore
     * @param path
     * @param readWriteTransaction
     */
    private static void deleteDataWithinTransaction(final LogicalDatastoreType dataStore,
                                                    final YangInstanceIdentifier path,
                                                    final DOMDataReadWriteTransaction readWriteTransaction) {
        LOG.trace("Delete {} within Restconf PATCH: {}", dataStore.name(), path);
        TransactionUtil.checkItemExists(readWriteTransaction, dataStore, path);
        readWriteTransaction.delete(dataStore, path);
    }

    /**
     * Merge data
     * @param dataStore
     * @param path
     * @param payload
     * @param writeTransaction
     * @param schemaContextRef
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
     * Do NOT check if data exists and remove it.
     * @param dataStore
     * @param path
     * @param writeTransaction
     */
    private static void removeDataWithinTransaction(final LogicalDatastoreType dataStore,
                                                    final YangInstanceIdentifier path,
                                                    final DOMDataWriteTransaction writeTransaction) {
        LOG.trace("Remove {} within Restconf PATCH: {}", dataStore.name(), path);
        writeTransaction.delete(dataStore, path);
    }

    /**
     *
     * @param dataStore
     * @param path
     * @param payload
     * @param schemaContextRef
     * @param rWTransaction
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
     *
     * @param payload
     * @param schemaContext
     * @param path
     * @param rWTransaction
     * @param dataStore
     * @param errorIfExists
     */
    private static void createData(final NormalizedNode<?, ?> payload, final SchemaContext schemaContext,
                                   final YangInstanceIdentifier path, final DOMDataReadWriteTransaction rWTransaction,
                                   final LogicalDatastoreType dataStore, final boolean errorIfExists) {
        if(payload instanceof MapNode) {
            final NormalizedNode<?, ?> emptySubtree = ImmutableNodes.fromInstanceId(schemaContext, path);
            rWTransaction.merge(dataStore, YangInstanceIdentifier.create(emptySubtree.getIdentifier()), emptySubtree);
            TransactionUtil.ensureParentsByMerge(path, schemaContext, rWTransaction);
            for(final MapEntryNode child : ((MapNode) payload).getValue()) {
                final YangInstanceIdentifier childPath = path.node(child.getIdentifier());

                if (errorIfExists) {
                    TransactionUtil.checkItemDoesNotExists(rWTransaction, dataStore, childPath);
                }

                rWTransaction.put(dataStore, childPath, child);
            }
        } else {
            if (errorIfExists) {
                TransactionUtil.checkItemDoesNotExists(rWTransaction, dataStore, path);
            }

            TransactionUtil.ensureParentsByMerge(path, schemaContext, rWTransaction);
            rWTransaction.put(dataStore, path, payload);
        }
    }
}
