/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.utils;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.ListenableFuture;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.netconf.api.NetconfDataTreeService;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.context.NormalizedNodeContext;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.restconf.nb.rfc8040.references.SchemaContextRef;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.TransactionVarsWrapper;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.ParserIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.OrderedLeafSetNode;
import org.opendaylight.yangtools.yang.data.api.schema.OrderedMapNode;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

/**
 * Util class to post data to DS.
 *
 */
public final class PostDataTransactionUtil {
    private PostDataTransactionUtil() {
        // Hidden on purpose
    }

    /**
     * Check mount point and prepare variables for post data. Close {@link DOMTransactionChain} inside of object
     * {@link TransactionVarsWrapper} provided as a parameter.
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
     * @return {@link Response}
     */
    public static Response postData(final UriInfo uriInfo, final NormalizedNodeContext payload,
                                    final TransactionVarsWrapper transactionNode,
                                    final SchemaContextRef schemaContextRef, final String insert,
                                    final String point) {
        final FluentFuture<? extends CommitInfo> future = submitData(
                payload.getInstanceIdentifierContext().getInstanceIdentifier(), payload.getData(),
                transactionNode, schemaContextRef.get(), insert, point);
        final URI location = resolveLocation(uriInfo, transactionNode, schemaContextRef, payload.getData());
        final ResponseFactory dataFactory = new ResponseFactory(Status.CREATED).location(location);
        //This method will close transactionChain
        FutureCallbackTx.addCallback(future, RestconfDataServiceConstant.PostData.POST_TX_TYPE, dataFactory,
                transactionNode.getTransactionChain());
        return dataFactory.build();
    }

    /**
     * Post data by type.
     *
     * @param path            path
     * @param data            data
     * @param transactionNode wrapper for data to transaction
     * @param schemaContext   schema context of data
     * @param point           query parameter
     * @param insert          query parameter
     * @return {@link FluentFuture}
     */
    private static FluentFuture<? extends CommitInfo> submitData(final YangInstanceIdentifier path,
                                                                 final NormalizedNode<?, ?> data,
                                                                 final TransactionVarsWrapper transactionNode,
                                                                 final EffectiveModelContext schemaContext,
                                                                 final String insert, final String point) {
        final NetconfDataTreeService netconfService = transactionNode.getNetconfDataTreeService();
        final DOMTransactionChain transactionChain = transactionNode.getTransactionChain();
        if (insert == null) {
            if (netconfService != null) {
                final List<ListenableFuture<? extends DOMRpcResult>> resultsFutures = netconfService.lock();
                makePost(path, data, schemaContext, netconfService, resultsFutures);
                return FluentFuture.from(netconfService.commit(resultsFutures));
            } else {
                final DOMDataTreeReadWriteTransaction newReadWriteTransaction =
                        transactionChain.newReadWriteTransaction();
                makePost(path, data, schemaContext, transactionChain, newReadWriteTransaction);
                return newReadWriteTransaction.commit();
            }
        }

        final DataSchemaNode schemaNode = PutDataTransactionUtil.checkListAndOrderedType(schemaContext, path);
        switch (insert) {
            case "first":
                if (schemaNode instanceof ListSchemaNode) {
                    final NormalizedNode<?, ?> readData = PutDataTransactionUtil.readList(path.getParent(),
                            schemaContext, transactionNode, schemaNode);
                    final OrderedMapNode readList = (OrderedMapNode) readData;
                    if (readList == null || readList.getValue().isEmpty()) {
                        if (netconfService != null) {
                            final List<ListenableFuture<? extends DOMRpcResult>> resultsFutures = netconfService.lock();
                            makePost(path, data, schemaContext, netconfService, resultsFutures);
                            return FluentFuture.from(netconfService.commit(resultsFutures));
                        } else {
                            final DOMDataTreeReadWriteTransaction newReadWriteTransaction =
                                    transactionChain.newReadWriteTransaction();
                            makePost(path, data, schemaContext, transactionChain, newReadWriteTransaction);
                            return newReadWriteTransaction.commit();
                        }
                    }

                    if (netconfService != null) {
                        final List<ListenableFuture<? extends DOMRpcResult>> resultsFutures = netconfService.lock();
                        resultsFutures.add(netconfService.delete(LogicalDatastoreType.CONFIGURATION,
                                path.getParent().getParent()));
                        simplePost(LogicalDatastoreType.CONFIGURATION, path, data, schemaContext, netconfService,
                                resultsFutures);
                        makePost(path, readData, schemaContext, netconfService, resultsFutures);
                        return FluentFuture.from(netconfService.commit(resultsFutures));
                    } else {
                        final DOMDataTreeReadWriteTransaction newReadWriteTransaction =
                                transactionChain.newReadWriteTransaction();
                        newReadWriteTransaction.delete(LogicalDatastoreType.CONFIGURATION,
                                path.getParent().getParent());
                        simplePost(newReadWriteTransaction, LogicalDatastoreType.CONFIGURATION, path, data,
                                schemaContext, transactionChain);
                        makePost(path, readData, schemaContext, transactionChain, newReadWriteTransaction);
                        return newReadWriteTransaction.commit();
                    }
                } else {
                    final NormalizedNode<?, ?> readData = PutDataTransactionUtil.readList(path.getParent(),
                            schemaContext, transactionNode, schemaNode);

                    final OrderedLeafSetNode<?> readLeafList = (OrderedLeafSetNode<?>) readData;
                    if (readLeafList == null || readLeafList.getValue().isEmpty()) {
                        if (netconfService != null) {
                            final List<ListenableFuture<? extends DOMRpcResult>> resultsFutures = netconfService.lock();
                            makePost(path, data, schemaContext, netconfService, resultsFutures);
                            return FluentFuture.from(netconfService.commit(resultsFutures));
                        } else {
                            final DOMDataTreeReadWriteTransaction newReadWriteTransaction =
                                    transactionChain.newReadWriteTransaction();
                            makePost(path, data, schemaContext, transactionChain, newReadWriteTransaction);
                            return newReadWriteTransaction.commit();
                        }
                    }

                    if (netconfService != null) {
                        final List<ListenableFuture<? extends DOMRpcResult>> resultsFutures = netconfService.lock();
                        resultsFutures.add(netconfService.delete(LogicalDatastoreType.CONFIGURATION,
                                path.getParent().getParent()));
                        simplePost(LogicalDatastoreType.CONFIGURATION, path, data, schemaContext, netconfService,
                                resultsFutures);
                        makePost(path, readData, schemaContext, netconfService, resultsFutures);
                        return FluentFuture.from(netconfService.commit(resultsFutures));
                    } else {
                        final DOMDataTreeReadWriteTransaction newReadWriteTransaction =
                                transactionChain.newReadWriteTransaction();
                        newReadWriteTransaction.delete(LogicalDatastoreType.CONFIGURATION,
                                path.getParent().getParent());
                        simplePost(newReadWriteTransaction, LogicalDatastoreType.CONFIGURATION, path, data,
                                schemaContext, transactionChain);
                        makePost(path, readData, schemaContext, transactionChain, newReadWriteTransaction);
                        return newReadWriteTransaction.commit();
                    }
                }
            case "last":
                if (netconfService != null) {
                    final List<ListenableFuture<? extends DOMRpcResult>> resultsFutures = netconfService.lock();
                    makePost(path, data, schemaContext, netconfService, resultsFutures);
                    return FluentFuture.from(netconfService.commit(resultsFutures));
                } else {
                    final DOMDataTreeReadWriteTransaction newReadWriteTransaction =
                            transactionChain.newReadWriteTransaction();
                    makePost(path, data, schemaContext, transactionChain, newReadWriteTransaction);
                    return newReadWriteTransaction.commit();
                }
            case "before":
                if (schemaNode instanceof ListSchemaNode) {
                    final NormalizedNode<?, ?> readData = PutDataTransactionUtil.readList(path.getParent(),
                            schemaContext, transactionNode, schemaNode);
                    final OrderedMapNode readList = (OrderedMapNode) readData;
                    if (readList == null || readList.getValue().isEmpty()) {
                        if (netconfService != null) {
                            final List<ListenableFuture<? extends DOMRpcResult>> resultsFutures = netconfService.lock();
                            makePost(path, data, schemaContext, netconfService, resultsFutures);
                            return FluentFuture.from(netconfService.commit(resultsFutures));
                        } else {
                            final DOMDataTreeReadWriteTransaction newReadWriteTransaction =
                                    transactionChain.newReadWriteTransaction();
                            makePost(path, data, schemaContext, transactionChain, newReadWriteTransaction);
                            return newReadWriteTransaction.commit();
                        }
                    }

                    if (netconfService != null) {
                        final List<ListenableFuture<? extends DOMRpcResult>> resultsFutures = netconfService.lock();
                        insertWithPointListPost(LogicalDatastoreType.CONFIGURATION, path, data, schemaContext, point,
                                readList, true, netconfService, resultsFutures);
                        return FluentFuture.from(netconfService.commit(resultsFutures));
                    } else {
                        final DOMDataTreeReadWriteTransaction newReadWriteTransaction =
                                transactionChain.newReadWriteTransaction();
                        insertWithPointListPost(newReadWriteTransaction, LogicalDatastoreType.CONFIGURATION, path,
                                data, schemaContext, point, readList, true, transactionChain);
                        return newReadWriteTransaction.commit();
                    }
                } else {
                    final NormalizedNode<?, ?> readData = PutDataTransactionUtil.readList(path.getParent(),
                            schemaContext, transactionNode, schemaNode);

                    final OrderedLeafSetNode<?> readLeafList = (OrderedLeafSetNode<?>) readData;
                    if (readLeafList == null || readLeafList.getValue().isEmpty()) {
                        if (netconfService != null) {
                            final List<ListenableFuture<? extends DOMRpcResult>> resultsFutures = netconfService.lock();
                            makePost(path, data, schemaContext, netconfService, resultsFutures);
                            return FluentFuture.from(netconfService.commit(resultsFutures));
                        } else {
                            final DOMDataTreeReadWriteTransaction newReadWriteTransaction =
                                    transactionChain.newReadWriteTransaction();
                            makePost(path, data, schemaContext, transactionChain, newReadWriteTransaction);
                            return newReadWriteTransaction.commit();
                        }
                    }
                    if (netconfService != null) {
                        final List<ListenableFuture<? extends DOMRpcResult>> resultsFutures = netconfService.lock();
                        insertWithPointLeafListPost(LogicalDatastoreType.CONFIGURATION,
                                path, data, schemaContext, point, readLeafList, true, netconfService,
                                resultsFutures);
                        return FluentFuture.from(netconfService.commit(resultsFutures));
                    } else {
                        final DOMDataTreeReadWriteTransaction newReadWriteTransaction =
                                transactionChain.newReadWriteTransaction();
                        insertWithPointLeafListPost(newReadWriteTransaction, LogicalDatastoreType.CONFIGURATION,
                                path, data, schemaContext, point, readLeafList, true, transactionChain);
                        return newReadWriteTransaction.commit();
                    }

                }
            case "after":
                if (schemaNode instanceof ListSchemaNode) {
                    final NormalizedNode<?, ?> readData = PutDataTransactionUtil.readList(path.getParent(),
                            schemaContext, transactionNode, schemaNode);
                    final OrderedMapNode readList = (OrderedMapNode) readData;
                    if (readList == null || readList.getValue().isEmpty()) {
                        if (netconfService != null) {
                            final List<ListenableFuture<? extends DOMRpcResult>> resultsFutures = netconfService.lock();
                            makePost(path, data, schemaContext, netconfService, resultsFutures);
                            return FluentFuture.from(netconfService.commit(resultsFutures));
                        } else {
                            final DOMDataTreeReadWriteTransaction newReadWriteTransaction =
                                    transactionChain.newReadWriteTransaction();
                            makePost(path, data, schemaContext, transactionChain, newReadWriteTransaction);
                            return newReadWriteTransaction.commit();
                        }
                    }

                    if (netconfService != null) {
                        final List<ListenableFuture<? extends DOMRpcResult>> resultsFutures = netconfService.lock();
                        insertWithPointListPost(LogicalDatastoreType.CONFIGURATION, path, data, schemaContext, point,
                                readList, false, netconfService, resultsFutures);
                        return FluentFuture.from(netconfService.commit(resultsFutures));
                    } else {
                        final DOMDataTreeReadWriteTransaction newReadWriteTransaction =
                                transactionChain.newReadWriteTransaction();
                        insertWithPointListPost(newReadWriteTransaction, LogicalDatastoreType.CONFIGURATION, path,
                                data, schemaContext, point, readList, false, transactionChain);
                        return newReadWriteTransaction.commit();
                    }
                } else {
                    final NormalizedNode<?, ?> readData = PutDataTransactionUtil.readList(path.getParent(),
                            schemaContext, transactionNode, schemaNode);

                    final OrderedLeafSetNode<?> readLeafList = (OrderedLeafSetNode<?>) readData;
                    if (readLeafList == null || readLeafList.getValue().isEmpty()) {
                        if (netconfService != null) {
                            final List<ListenableFuture<? extends DOMRpcResult>> resultsFutures = netconfService.lock();
                            makePost(path, data, schemaContext, netconfService, resultsFutures);
                            return FluentFuture.from(netconfService.commit(resultsFutures));
                        } else {
                            final DOMDataTreeReadWriteTransaction newReadWriteTransaction =
                                    transactionChain.newReadWriteTransaction();
                            makePost(path, data, schemaContext, transactionChain, newReadWriteTransaction);
                            return newReadWriteTransaction.commit();
                        }
                    }
                    if (netconfService != null) {
                        final List<ListenableFuture<? extends DOMRpcResult>> resultsFutures = netconfService.lock();
                        insertWithPointLeafListPost(LogicalDatastoreType.CONFIGURATION,
                                path, data, schemaContext, point, readLeafList, true,
                                netconfService, resultsFutures);
                        return FluentFuture.from(netconfService.commit(resultsFutures));
                    } else {
                        final DOMDataTreeReadWriteTransaction newReadWriteTransaction =
                                transactionChain.newReadWriteTransaction();
                        insertWithPointLeafListPost(newReadWriteTransaction, LogicalDatastoreType.CONFIGURATION,
                                path, data, schemaContext, point, readLeafList, true, transactionChain);
                        return newReadWriteTransaction.commit();
                    }

                }
            default:
                throw new RestconfDocumentedException(
                    "Used bad value of insert parameter. Possible values are first, last, before or after, but was: "
                            + insert, RestconfError.ErrorType.PROTOCOL, RestconfError.ErrorTag.BAD_ATTRIBUTE);
        }
    }

    private static void insertWithPointLeafListPost(
            final LogicalDatastoreType datastore, final YangInstanceIdentifier path, final NormalizedNode<?, ?> payload,
            final EffectiveModelContext schemaContext, final String point, final OrderedLeafSetNode<?> readLeafList,
            final boolean before, final NetconfDataTreeService netconfService,
            final List<ListenableFuture<? extends DOMRpcResult>> resultsFutures) {
        resultsFutures.add(netconfService.delete(datastore, path.getParent().getParent()));
        final InstanceIdentifierContext<?> instanceIdentifier =
                ParserIdentifier.toInstanceIdentifier(point, schemaContext, Optional.empty());
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
        resultsFutures.add(netconfService.merge(datastore,
                YangInstanceIdentifier.create(emptySubtree.getIdentifier()), emptySubtree, Optional.empty()));
        for (final LeafSetEntryNode<?> nodeChild : readLeafList.getValue()) {
            if (lastInsertedPosition == lastItemPosition) {
                TransactionUtil.checkItemDoesNotExists(netconfService, datastore, path,
                        RestconfDataServiceConstant.PostData.POST_TX_TYPE);
                resultsFutures.add(netconfService.create(datastore, path, payload, Optional.empty()));
            }
            final YangInstanceIdentifier childPath = path.getParent().getParent().node(nodeChild.getIdentifier());
            TransactionUtil.checkItemDoesNotExists(netconfService, datastore, childPath,
                    RestconfDataServiceConstant.PostData.POST_TX_TYPE);
            resultsFutures.add(netconfService.create(datastore, childPath, nodeChild, Optional.empty()));
            lastInsertedPosition++;
        }
    }

    private static void insertWithPointLeafListPost(final DOMDataTreeReadWriteTransaction rwTransaction,
                                                    final LogicalDatastoreType datastore,
                                                    final YangInstanceIdentifier path,
                                                    final NormalizedNode<?, ?> payload,
                                                    final EffectiveModelContext schemaContext, final String point,
                                                    final OrderedLeafSetNode<?> readLeafList,
                                                    final boolean before, final DOMTransactionChain transactionChain) {
        rwTransaction.delete(datastore, path.getParent().getParent());
        final InstanceIdentifierContext<?> instanceIdentifier =
                ParserIdentifier.toInstanceIdentifier(point, schemaContext, Optional.empty());
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
                TransactionUtil.checkItemDoesNotExists(transactionChain, rwTransaction, datastore, path,
                        RestconfDataServiceConstant.PostData.POST_TX_TYPE);
                rwTransaction.put(datastore, path, payload);
            }
            final YangInstanceIdentifier childPath = path.getParent().getParent().node(nodeChild.getIdentifier());
            TransactionUtil.checkItemDoesNotExists(transactionChain, rwTransaction, datastore, childPath,
                    RestconfDataServiceConstant.PostData.POST_TX_TYPE);
            rwTransaction.put(datastore, childPath, nodeChild);
            lastInsertedPosition++;
        }
    }

    private static void insertWithPointListPost(final LogicalDatastoreType datastore,
                                                final YangInstanceIdentifier path, final NormalizedNode<?, ?> payload,
                                                final EffectiveModelContext schemaContext, final String point,
                                                final MapNode readList, final boolean before,
                                                final NetconfDataTreeService netconfService,
                                                final List<ListenableFuture<? extends DOMRpcResult>> resultsFutures) {
        resultsFutures.add(netconfService.delete(datastore, path.getParent().getParent()));
        final InstanceIdentifierContext<?> instanceIdentifier =
                ParserIdentifier.toInstanceIdentifier(point, schemaContext, Optional.empty());
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
        final NormalizedNode<?, ?> emptySubtree = ImmutableNodes.fromInstanceId(schemaContext,
                path.getParent().getParent());
        resultsFutures.add(netconfService.merge(datastore,
                YangInstanceIdentifier.create(emptySubtree.getIdentifier()), emptySubtree, Optional.empty()));
        for (final MapEntryNode mapEntryNode : readList.getValue()) {
            if (lastInsertedPosition == lastItemPosition) {
                TransactionUtil.checkItemDoesNotExists(netconfService, datastore, path,
                        RestconfDataServiceConstant.PostData.POST_TX_TYPE);
                resultsFutures.add(netconfService.create(datastore, path, payload, Optional.empty()));
            }
            final YangInstanceIdentifier childPath = path.getParent().getParent().node(mapEntryNode.getIdentifier());
            TransactionUtil.checkItemDoesNotExists(netconfService, datastore, childPath,
                    RestconfDataServiceConstant.PostData.POST_TX_TYPE);
            resultsFutures.add(netconfService.create(datastore, childPath, mapEntryNode, Optional.empty()));
            lastInsertedPosition++;
        }
    }

    private static void insertWithPointListPost(final DOMDataTreeReadWriteTransaction rwTransaction,
                                                final LogicalDatastoreType datastore, final YangInstanceIdentifier path,
                                                final NormalizedNode<?, ?> payload,
                                                final EffectiveModelContext schemaContext, final String point,
                                                final MapNode readList, final boolean before,
                                                final DOMTransactionChain transactionChain) {
        rwTransaction.delete(datastore, path.getParent().getParent());
        final InstanceIdentifierContext<?> instanceIdentifier =
                ParserIdentifier.toInstanceIdentifier(point, schemaContext, Optional.empty());
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
                TransactionUtil.checkItemDoesNotExists(transactionChain, rwTransaction, datastore, path,
                        RestconfDataServiceConstant.PostData.POST_TX_TYPE);
                rwTransaction.put(datastore, path, payload);
            }
            final YangInstanceIdentifier childPath = path.getParent().getParent().node(mapEntryNode.getIdentifier());
            TransactionUtil.checkItemDoesNotExists(transactionChain, rwTransaction, datastore, childPath,
                    RestconfDataServiceConstant.PostData.POST_TX_TYPE);
            rwTransaction.put(datastore, childPath, mapEntryNode);
            lastInsertedPosition++;
        }
    }

    private static List<ListenableFuture<? extends DOMRpcResult>> makePost(
            final YangInstanceIdentifier path,
            final NormalizedNode<?, ?> data,
            final SchemaContext schemaContext,
            final NetconfDataTreeService netconfService,
            final List<ListenableFuture<? extends DOMRpcResult>> resultsFutures) {
        if (data instanceof MapNode) {
            boolean merge = false;
            for (final MapEntryNode child : ((MapNode) data).getValue()) {
                final YangInstanceIdentifier childPath = path.node(child.getIdentifier());
                TransactionUtil.checkItemDoesNotExists(netconfService, LogicalDatastoreType.CONFIGURATION,
                        childPath, RestconfDataServiceConstant.PostData.POST_TX_TYPE);
                if (!merge) {
                    merge = true;
                    TransactionUtil.ensureParentsByMerge(path, schemaContext, netconfService, resultsFutures);
                    final NormalizedNode<?, ?> emptySubTree = ImmutableNodes.fromInstanceId(schemaContext, path);
                    resultsFutures.add(netconfService.merge(LogicalDatastoreType.CONFIGURATION,
                            YangInstanceIdentifier.create(emptySubTree.getIdentifier()), emptySubTree,
                            Optional.empty()));
                }
                resultsFutures.add(netconfService.create(LogicalDatastoreType.CONFIGURATION,
                        childPath, child, Optional.empty()));
            }
        } else {
            TransactionUtil.checkItemDoesNotExists(netconfService, LogicalDatastoreType.CONFIGURATION, path,
                    RestconfDataServiceConstant.PostData.POST_TX_TYPE);

            TransactionUtil.ensureParentsByMerge(path, schemaContext, netconfService, resultsFutures);
            resultsFutures.add(netconfService.create(LogicalDatastoreType.CONFIGURATION, path, data, Optional.empty()));
        }
        return resultsFutures;
    }

    private static void makePost(final YangInstanceIdentifier path, final NormalizedNode<?, ?> data,
                                 final SchemaContext schemaContext, final DOMTransactionChain transactionChain,
                                 final DOMDataTreeReadWriteTransaction transaction) {
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
     * @param uriInfo          uri info
     * @param transactionNode  wrapper for data of transaction
     * @param schemaContextRef reference to {@link SchemaContext}
     * @return {@link URI}
     */
    private static URI resolveLocation(final UriInfo uriInfo, final TransactionVarsWrapper transactionNode,
                                       final SchemaContextRef schemaContextRef, final NormalizedNode<?, ?> data) {
        if (uriInfo == null) {
            return null;
        }

        YangInstanceIdentifier path = transactionNode.getInstanceIdentifier().getInstanceIdentifier();

        if (data instanceof MapNode) {
            final Collection<MapEntryNode> children = ((MapNode) data).getValue();
            if (!children.isEmpty()) {
                path = path.node(children.iterator().next().getIdentifier());
            }
        }

        return uriInfo.getBaseUriBuilder()
                .path("data")
                .path(ParserIdentifier.stringFromYangInstanceIdentifier(path, schemaContextRef.get()))
                .build();
    }

    private static void simplePost(final DOMDataTreeReadWriteTransaction rwTransaction,
                                   final LogicalDatastoreType datastore, final YangInstanceIdentifier path,
                                   final NormalizedNode<?, ?> payload,
                                   final SchemaContext schemaContext, final DOMTransactionChain transactionChain) {
        TransactionUtil.checkItemDoesNotExists(transactionChain, rwTransaction, datastore, path,
                RestconfDataServiceConstant.PostData.POST_TX_TYPE);
        TransactionUtil.ensureParentsByMerge(path, schemaContext, rwTransaction);
        rwTransaction.put(datastore, path, payload);
    }

    private static void simplePost(final LogicalDatastoreType datastore, final YangInstanceIdentifier path,
                                   final NormalizedNode<?, ?> payload,
                                   final SchemaContext schemaContext,
                                   final NetconfDataTreeService netconfService,
                                   List<ListenableFuture<? extends DOMRpcResult>> resultsFutures) {
        TransactionUtil.checkItemDoesNotExists(netconfService, datastore, path,
                RestconfDataServiceConstant.PostData.POST_TX_TYPE);
        TransactionUtil.ensureParentsByMerge(path, schemaContext, netconfService, resultsFutures);
        resultsFutures.add(netconfService.create(datastore, path, payload, Optional.empty()));
    }
}
