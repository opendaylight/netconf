/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.restful.utils;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import java.net.URI;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadWriteTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMTransactionChain;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.context.NormalizedNodeContext;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.references.SchemaContextRef;
import org.opendaylight.restconf.restful.transaction.TransactionVarsWrapper;
import org.opendaylight.restconf.utils.parser.ParserIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.OrderedLeafSetNode;
import org.opendaylight.yangtools.yang.data.api.schema.OrderedMapNode;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Util class to post data to DS.
 *
 * @deprecated move to splitted module restconf-nb-rfc8040
 */
@Deprecated
public final class PostDataTransactionUtil {

    private static final Logger LOG = LoggerFactory.getLogger(PostDataTransactionUtil.class);

    private PostDataTransactionUtil() {
        throw new UnsupportedOperationException("Util class.");
    }

    /**
     * Check mount point and prepare variables for post data.
     *
     * @param uriInfo
     *
     * @param payload
     *             data
     * @param transactionNode
     *             wrapper for transaction data
     * @param schemaContextRef
     *             reference to actual {@link SchemaContext}
     * @param point
     *             point
     * @param insert
     *             insert
     * @return {@link CheckedFuture}
     */
    public static Response postData(final UriInfo uriInfo, final NormalizedNodeContext payload,
            final TransactionVarsWrapper transactionNode, final SchemaContextRef schemaContextRef, final String insert,
            final String point) {
        final CheckedFuture<Void, TransactionCommitFailedException> future = submitData(
                payload.getInstanceIdentifierContext().getInstanceIdentifier(), payload.getData(),
                transactionNode, schemaContextRef.get(), insert, point);
        final URI location = PostDataTransactionUtil.resolveLocation(uriInfo, transactionNode, schemaContextRef);
        final ResponseFactory dataFactory = new ResponseFactory(null, location);
        FutureCallbackTx.addCallback(future, RestconfDataServiceConstant.PostData.POST_TX_TYPE, dataFactory);
        return dataFactory.build();
    }

    /**
     * Post data by type.
     *
     * @param path
     *             path
     * @param data
     *             data
     * @param transactionNode
     *             wrapper for data to transaction
     * @param schemaContext
     *             schema context of data
     * @param point
     *             query parameter
     * @param insert
     *             query parameter
     * @return {@link CheckedFuture}
     */
    private static CheckedFuture<Void, TransactionCommitFailedException> submitData(final YangInstanceIdentifier path,
            final NormalizedNode<?, ?> data, final TransactionVarsWrapper transactionNode,
            final SchemaContext schemaContext, final String insert, final String point) {
        final DOMTransactionChain domTransactionChain = transactionNode.getTransactionChain();
        final DOMDataReadWriteTransaction newReadWriteTransaction = domTransactionChain.newReadWriteTransaction();
        if (insert == null) {
            makePost(path, data, schemaContext, domTransactionChain, newReadWriteTransaction);
            return newReadWriteTransaction.submit();
        } else {
            final DataSchemaNode schemaNode = PutDataTransactionUtil.checkListAndOrderedType(schemaContext, path);
            switch (insert) {
                case "first":
                    if (schemaNode instanceof ListSchemaNode) {
                        final NormalizedNode<?, ?> readData =
                                PutDataTransactionUtil.readList(path.getParent(), schemaContext, domTransactionChain,
                                        schemaNode);
                        final OrderedMapNode readList = (OrderedMapNode) readData;
                        if ((readList == null) || readList.getValue().isEmpty()) {
                            makePost(path, data, schemaContext, domTransactionChain, newReadWriteTransaction);
                            return newReadWriteTransaction.submit();
                        } else {
                            newReadWriteTransaction.delete(LogicalDatastoreType.CONFIGURATION,
                                    path.getParent().getParent());
                            simplePost(newReadWriteTransaction, LogicalDatastoreType.CONFIGURATION, path, data,
                                    schemaContext, domTransactionChain);
                            makePost(path, readData, schemaContext, domTransactionChain,
                                    newReadWriteTransaction);
                            return newReadWriteTransaction.submit();
                        }
                    } else {
                        final NormalizedNode<?, ?> readData = PutDataTransactionUtil
                                        .readList(path.getParent(), schemaContext, domTransactionChain, schemaNode);

                        final OrderedLeafSetNode<?> readLeafList = (OrderedLeafSetNode<?>) readData;
                        if ((readLeafList == null) || readLeafList.getValue().isEmpty()) {
                            makePost(path, data, schemaContext, domTransactionChain, newReadWriteTransaction);
                            return newReadWriteTransaction.submit();
                        } else {
                            newReadWriteTransaction.delete(LogicalDatastoreType.CONFIGURATION,
                                    path.getParent().getParent());
                            simplePost(newReadWriteTransaction, LogicalDatastoreType.CONFIGURATION, path, data,
                                    schemaContext, domTransactionChain);
                            makePost(path, readData, schemaContext, domTransactionChain, newReadWriteTransaction);
                            return newReadWriteTransaction.submit();
                        }
                    }
                case "last":
                    makePost(path, data, schemaContext, domTransactionChain, newReadWriteTransaction);
                    return newReadWriteTransaction.submit();
                case "before":
                    if (schemaNode instanceof ListSchemaNode) {
                        final NormalizedNode<?, ?> readData =
                                PutDataTransactionUtil.readList(path.getParent(), schemaContext, domTransactionChain,
                                        schemaNode);
                        final OrderedMapNode readList = (OrderedMapNode) readData;
                        if ((readList == null) || readList.getValue().isEmpty()) {
                            makePost(path, data, schemaContext, domTransactionChain, newReadWriteTransaction);
                            return newReadWriteTransaction.submit();
                        } else {
                            insertWithPointListPost(newReadWriteTransaction, LogicalDatastoreType.CONFIGURATION, path,
                                    data, schemaContext, point, readList, true, domTransactionChain);
                            return newReadWriteTransaction.submit();
                        }
                    } else {
                        final NormalizedNode<?, ?> readData =
                                PutDataTransactionUtil.readList(path.getParent(), schemaContext, domTransactionChain,
                                        schemaNode);

                        final OrderedLeafSetNode<?> readLeafList = (OrderedLeafSetNode<?>) readData;
                        if ((readLeafList == null) || readLeafList.getValue().isEmpty()) {
                            makePost(path, data, schemaContext, domTransactionChain, newReadWriteTransaction);
                            return newReadWriteTransaction.submit();
                        } else {
                            insertWithPointLeafListPost(newReadWriteTransaction, LogicalDatastoreType.CONFIGURATION,
                                    path, data, schemaContext, point, readLeafList, true, domTransactionChain);
                            return newReadWriteTransaction.submit();
                        }
                    }
                case "after":
                    if (schemaNode instanceof ListSchemaNode) {
                        final NormalizedNode<?, ?> readData =
                                PutDataTransactionUtil.readList(path.getParent(), schemaContext, domTransactionChain,
                                        schemaNode);
                        final OrderedMapNode readList = (OrderedMapNode) readData;
                        if ((readList == null) || readList.getValue().isEmpty()) {
                            makePost(path, data, schemaContext, domTransactionChain, newReadWriteTransaction);
                            return newReadWriteTransaction.submit();
                        } else {
                            insertWithPointListPost(newReadWriteTransaction, LogicalDatastoreType.CONFIGURATION, path,
                                    data, schemaContext, point, readList, false, domTransactionChain);
                            return newReadWriteTransaction.submit();
                        }
                    } else {
                        final NormalizedNode<?, ?> readData =
                                PutDataTransactionUtil.readList(path.getParent(), schemaContext, domTransactionChain,
                                        schemaNode);

                        final OrderedLeafSetNode<?> readLeafList = (OrderedLeafSetNode<?>) readData;
                        if ((readLeafList == null) || readLeafList.getValue().isEmpty()) {
                            makePost(path, data, schemaContext, domTransactionChain, newReadWriteTransaction);
                            return newReadWriteTransaction.submit();
                        } else {
                            insertWithPointLeafListPost(newReadWriteTransaction, LogicalDatastoreType.CONFIGURATION,
                                    path, data, schemaContext, point, readLeafList, true, domTransactionChain);
                            return newReadWriteTransaction.submit();
                        }
                    }
                default:
                    throw new RestconfDocumentedException(
                            "Used bad value of insert parameter. Possible values are first, last, before or after, "
                                    + "but was: " + insert);
            }
        }
    }

    private static void insertWithPointLeafListPost(final DOMDataReadWriteTransaction rwTransaction,
            final LogicalDatastoreType datastore, final YangInstanceIdentifier path, final NormalizedNode<?, ?> payload,
            final SchemaContext schemaContext, final String point, final OrderedLeafSetNode<?> readLeafList,
            final boolean before, final DOMTransactionChain domTransactionChain) {
        rwTransaction.delete(datastore, path.getParent().getParent());
        final InstanceIdentifierContext<?> instanceIdentifier =
                ParserIdentifier.toInstanceIdentifier(point, schemaContext, Optional.absent());
        int lastItemPosition = 0;
        for (final LeafSetEntryNode<?> nodeChild : readLeafList.getValue()) {
            if (nodeChild.getIdentifier().equals(instanceIdentifier.getInstanceIdentifier().getLastPathArgument())) {
                break;
            }
            lastItemPosition++;
        }
        if (!before) {
            lastItemPosition++;
        }
        int lastInsertedPosition = 0;
        final NormalizedNode<?, ?> emptySubtree =
                ImmutableNodes.fromInstanceId(schemaContext, path.getParent().getParent());
        rwTransaction.merge(datastore, YangInstanceIdentifier.create(emptySubtree.getIdentifier()), emptySubtree);
        for (final LeafSetEntryNode<?> nodeChild : readLeafList.getValue()) {
            if (lastInsertedPosition == lastItemPosition) {
                TransactionUtil.checkItemDoesNotExists(domTransactionChain, rwTransaction, datastore, path,
                        RestconfDataServiceConstant.PostData.POST_TX_TYPE);
                rwTransaction.put(datastore, path, payload);
            }
            final YangInstanceIdentifier childPath = path.getParent().getParent().node(nodeChild.getIdentifier());
            TransactionUtil.checkItemDoesNotExists(domTransactionChain, rwTransaction, datastore, childPath,
                    RestconfDataServiceConstant.PostData.POST_TX_TYPE);
            rwTransaction.put(datastore, childPath, nodeChild);
            lastInsertedPosition++;
        }
    }

    private static void insertWithPointListPost(final DOMDataReadWriteTransaction rwTransaction,
            final LogicalDatastoreType datastore, final YangInstanceIdentifier path, final NormalizedNode<?, ?> payload,
            final SchemaContext schemaContext, final String point, final MapNode readList, final boolean before,
            final DOMTransactionChain domTransactionChain) {
        rwTransaction.delete(datastore, path.getParent().getParent());
        final InstanceIdentifierContext<?> instanceIdentifier =
                ParserIdentifier.toInstanceIdentifier(point, schemaContext, Optional.absent());
        int lastItemPosition = 0;
        for (final MapEntryNode mapEntryNode : readList.getValue()) {
            if (mapEntryNode.getIdentifier().equals(instanceIdentifier.getInstanceIdentifier().getLastPathArgument())) {
                break;
            }
            lastItemPosition++;
        }
        if (!before) {
            lastItemPosition++;
        }
        int lastInsertedPosition = 0;
        final NormalizedNode<?, ?> emptySubtree =
                ImmutableNodes.fromInstanceId(schemaContext, path.getParent().getParent());
        rwTransaction.merge(datastore, YangInstanceIdentifier.create(emptySubtree.getIdentifier()), emptySubtree);
        for (final MapEntryNode mapEntryNode : readList.getValue()) {
            if (lastInsertedPosition == lastItemPosition) {
                TransactionUtil.checkItemDoesNotExists(domTransactionChain, rwTransaction, datastore, path,
                        RestconfDataServiceConstant.PostData.POST_TX_TYPE);
                rwTransaction.put(datastore, path, payload);
            }
            final YangInstanceIdentifier childPath = path.getParent().getParent().node(mapEntryNode.getIdentifier());
            TransactionUtil.checkItemDoesNotExists(domTransactionChain, rwTransaction, datastore, childPath,
                    RestconfDataServiceConstant.PostData.POST_TX_TYPE);
            rwTransaction.put(datastore, childPath, mapEntryNode);
            lastInsertedPosition++;
        }
    }

    private static void makePost(final YangInstanceIdentifier path, final NormalizedNode<?, ?> data,
            final SchemaContext schemaContext, final DOMTransactionChain transactionChain,
            final DOMDataReadWriteTransaction transaction) {
        if (data instanceof MapNode) {
            boolean merge = false;
            for (final MapEntryNode child : ((MapNode) data).getValue()) {
                final YangInstanceIdentifier childPath = path.node(child.getIdentifier());
                TransactionUtil.checkItemDoesNotExists(
                        transactionChain, transaction, LogicalDatastoreType.CONFIGURATION, childPath,
                        RestconfDataServiceConstant.PostData.POST_TX_TYPE);
                if (!merge) {
                    merge = true;
                    TransactionUtil.ensureParentsByMerge(path, schemaContext, transaction);
                    final NormalizedNode<?, ?> emptySubTree = ImmutableNodes.fromInstanceId(schemaContext, path);
                    transaction.merge(LogicalDatastoreType.CONFIGURATION,
                            YangInstanceIdentifier.create(emptySubTree.getIdentifier()), emptySubTree);
                }
                transaction.put(LogicalDatastoreType.CONFIGURATION, childPath, child);
            }
        } else {
            TransactionUtil.checkItemDoesNotExists(
                    transactionChain, transaction, LogicalDatastoreType.CONFIGURATION, path,
                    RestconfDataServiceConstant.PostData.POST_TX_TYPE);

            TransactionUtil.ensureParentsByMerge(path, schemaContext, transaction);
            transaction.put(LogicalDatastoreType.CONFIGURATION, path, data);
        }
    }

    /**
     * Get location from {@link YangInstanceIdentifier} and {@link UriInfo}.
     *
     * @param uriInfo
     *             uri info
     * @param transactionNode
     *             wrapper for data of transaction
     * @param schemaContextRef
     *            reference to {@link SchemaContext}
     * @return {@link URI}
     */
    private static URI resolveLocation(final UriInfo uriInfo, final TransactionVarsWrapper transactionNode,
            final SchemaContextRef schemaContextRef) {
        if (uriInfo == null) {
            return null;
        }

        final UriBuilder uriBuilder = uriInfo.getBaseUriBuilder();
        uriBuilder.path("data");
        uriBuilder.path(ParserIdentifier
                .stringFromYangInstanceIdentifier(transactionNode.getInstanceIdentifier().getInstanceIdentifier(),
                schemaContextRef.get()));

        return uriBuilder.build();
    }

    private static void simplePost(final DOMDataReadWriteTransaction rwTransaction,
            final LogicalDatastoreType datastore, final YangInstanceIdentifier path, final NormalizedNode<?, ?> payload,
            final SchemaContext schemaContext, final DOMTransactionChain transactionChain) {
        TransactionUtil.checkItemDoesNotExists(transactionChain, rwTransaction, datastore, path,
                RestconfDataServiceConstant.PostData.POST_TX_TYPE);
        TransactionUtil.ensureParentsByMerge(path, schemaContext, rwTransaction);
        rwTransaction.put(datastore, path, payload);
    }
}
