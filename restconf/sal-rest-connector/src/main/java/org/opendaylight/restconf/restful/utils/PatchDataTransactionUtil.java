/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.restful.utils;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.ws.rs.NotSupportedException;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadWriteTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMDataWriteTransaction;
import org.opendaylight.netconf.sal.restconf.impl.BrokerFacade;
import org.opendaylight.netconf.sal.restconf.impl.PATCHContext;
import org.opendaylight.netconf.sal.restconf.impl.PATCHEditOperation;
import org.opendaylight.netconf.sal.restconf.impl.PATCHEntity;
import org.opendaylight.netconf.sal.restconf.impl.PATCHStatusContext;
import org.opendaylight.netconf.sal.restconf.impl.PATCHStatusEntity;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorTag;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorType;
import org.opendaylight.restconf.common.references.SchemaContextRef;
import org.opendaylight.restconf.restful.transaction.TransactionVarsWrapper;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PatchDataTransactionUtil {
    private final static Logger LOG = LoggerFactory.getLogger(BrokerFacade.class);

    private PatchDataTransactionUtil() {
        throw new UnsupportedOperationException("Util class.");
    }

    public static PATCHStatusContext patchData(final PATCHContext context, final TransactionVarsWrapper transactionNode,
                                               final SchemaContextRef schemaContextRef)
    {
        final List<PATCHStatusEntity> editCollection = new ArrayList<>();
        final List<RestconfError> errors = new ArrayList<>();

        for (PATCHEntity patchEntity : context.getData()) {
            final PATCHEditOperation operation = PATCHEditOperation.valueOf(patchEntity.getOperation());

            switch (operation) {
                case CREATE:
                    createDataWithinTransaction(transactionNode.getLogicalDatastoreType(),
                            patchEntity.getTargetNode(), patchEntity.getNode(),
                            transactionNode.getTransaction(), schemaContextRef);
                    break;
                case DELETE:
                    deleteDataWithinTransaction(transactionNode.getLogicalDatastoreType(),
                            patchEntity.getTargetNode(), transactionNode.getTransaction());
                case INSERT:
                    throw new NotSupportedException("Not yet supported");
                case MERGE:
                    mergeDataWithinTransaction(transactionNode.getLogicalDatastoreType(),
                            patchEntity.getTargetNode(), patchEntity.getNode(), transactionNode.getTransaction(),
                            schemaContextRef);
                case MOVE:
                    throw new NotSupportedException("Not yet supported");
                case REPLACE:
                    replaceDataWithinTransaction(transactionNode.getLogicalDatastoreType(), patchEntity.getTargetNode(),
                            patchEntity.getNode(), schemaContextRef, transactionNode.getTransaction());
                case REMOVE:
                    removeDataWithinTransaction(transactionNode.getLogicalDatastoreType(),
                            patchEntity.getTargetNode(), transactionNode.getTransaction());
                default:
                    break;
            }
        }

        // close transaction?
        return new PATCHStatusContext(context.getPatchId(), ImmutableList.copyOf(editCollection),
                errors.isEmpty(), errors);
    }

    /**
     * Cretae data, return error if already exists.
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
        checkItemExists(readWriteTransaction, dataStore, path);
        readWriteTransaction.delete(dataStore, path);
    }

    private static void mergeDataWithinTransaction(final LogicalDatastoreType dataStore,
                                                   final YangInstanceIdentifier path,
                                                   final NormalizedNode<?, ?> payload,
                                                   final DOMDataReadWriteTransaction writeTransaction,
                                                   final SchemaContextRef schemaContextRef) {
        LOG.trace("Merge {} within Restconf PATCH: {} with payload {}", dataStore.name(), path, payload);
        ensureParentsByMerge(dataStore, path, writeTransaction, schemaContextRef.get());

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

    private static void replaceDataWithinTransaction(final LogicalDatastoreType dataStore,
                                                     final YangInstanceIdentifier path,
                                                     final NormalizedNode<?, ?> payload,
                                                     final SchemaContextRef schemaContextRef,
                                                     final DOMDataReadWriteTransaction rWTransaction) {
        LOG.trace("PUT {} within Restconf PATCH: {} with payload {}", dataStore.name(), path, payload);
        createData(payload, schemaContextRef.get(), path, rWTransaction, dataStore, false);
    }

    private static void createData(final NormalizedNode<?, ?> payload, final SchemaContext schemaContext,
                                   final YangInstanceIdentifier path, final DOMDataReadWriteTransaction rWTransaction,
                                   final LogicalDatastoreType dataStore, final boolean errorIfExists) {
        if(payload instanceof MapNode) {
            final NormalizedNode<?, ?> emptySubtree = ImmutableNodes.fromInstanceId(schemaContext, path);
            rWTransaction.merge(dataStore, YangInstanceIdentifier.create(emptySubtree.getIdentifier()), emptySubtree);
            ensureParentsByMerge(dataStore, path, rWTransaction, schemaContext);
            for(final MapEntryNode child : ((MapNode) payload).getValue()) {
                final YangInstanceIdentifier childPath = path.node(child.getIdentifier());

                if (errorIfExists) {
                    checkItemDoesNotExists(rWTransaction, dataStore, childPath);
                }

                rWTransaction.put(dataStore, childPath, child);
            }
        } else {
            if (errorIfExists) {
                checkItemDoesNotExists(rWTransaction, dataStore, path);
            }

            ensureParentsByMerge(dataStore, path, rWTransaction, schemaContext);
            rWTransaction.put(dataStore, path, payload);
        }
    }

    private static void ensureParentsByMerge(final LogicalDatastoreType store, final YangInstanceIdentifier normalizedPath,
                                             final DOMDataReadWriteTransaction rwTx, final SchemaContext schemaContext) {
        final List<PathArgument> normalizedPathWithoutChildArgs = new ArrayList<>();
        YangInstanceIdentifier rootNormalizedPath = null;

        final Iterator<PathArgument> it = normalizedPath.getPathArguments().iterator();

        while(it.hasNext()) {
            final PathArgument pathArgument = it.next();
            if(rootNormalizedPath == null) {
                rootNormalizedPath = YangInstanceIdentifier.create(pathArgument);
            }

            // Skip last element, its not a parent
            if(it.hasNext()) {
                normalizedPathWithoutChildArgs.add(pathArgument);
            }
        }

        // No parent structure involved, no need to ensure parents
        if(normalizedPathWithoutChildArgs.isEmpty()) {
            return;
        }

        Preconditions.checkArgument(rootNormalizedPath != null, "Empty path received");

        final NormalizedNode<?, ?> parentStructure =
                ImmutableNodes.fromInstanceId(schemaContext, YangInstanceIdentifier.create(normalizedPathWithoutChildArgs));
        rwTx.merge(store, rootNormalizedPath, parentStructure);
    }

    private static void checkItemExists(final DOMDataReadWriteTransaction rWTransaction,
                                        final LogicalDatastoreType store, final YangInstanceIdentifier path) {
        final ListenableFuture<Boolean> futureDatastoreData = rWTransaction.exists(store, path);
        try {
            if (!futureDatastoreData.get()) {
                final String errMsg = "Operation via Restconf was not executed because data does not exist";
                LOG.trace("{}:{}", errMsg, path);
                rWTransaction.cancel();
                throw new RestconfDocumentedException("Data does not exist for path: " + path, ErrorType.PROTOCOL,
                        ErrorTag.DATA_MISSING);
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.warn("It wasn't possible to get data loaded from datastore at path {}", path, e);
        }
    }

    private static void checkItemDoesNotExists(final DOMDataReadWriteTransaction rWTransaction,
                                               final LogicalDatastoreType store, final YangInstanceIdentifier path) {
        final ListenableFuture<Boolean> futureDatastoreData = rWTransaction.exists(store, path);
        try {
            if (futureDatastoreData.get()) {
                final String errMsg = "Operation via Restconf was not executed because data already exists";
                LOG.trace("{}:{}", errMsg, path);
                rWTransaction.cancel();
                throw new RestconfDocumentedException("Data already exists for path: " + path, ErrorType.PROTOCOL,
                        ErrorTag.DATA_EXISTS);
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.warn("It wasn't possible to get data loaded from datastore at path {}", path, e);
        }
    }
}
