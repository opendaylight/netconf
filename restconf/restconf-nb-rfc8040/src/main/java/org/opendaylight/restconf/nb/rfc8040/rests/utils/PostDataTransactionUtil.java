/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.utils;

import com.google.common.util.concurrent.FluentFuture;
import java.net.URI;
import java.util.Collection;
import java.util.Optional;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.context.NormalizedNodeContext;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorTag;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorType;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.RestconfStrategy;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.RestconfTransaction;
import org.opendaylight.restconf.nb.rfc8040.rests.utils.RestconfDataServiceConstant.PostPutQueryParameters.Insert;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Util class to post data to DS.
 *
 */
public final class PostDataTransactionUtil {
    private static final Logger LOG = LoggerFactory.getLogger(PostDataTransactionUtil.class);
    // FIXME: why is this being reused from other places?
    static final String POST_TX_TYPE = "POST";

    private PostDataTransactionUtil() {
        // Hidden on purpose
    }

    /**
     * Check mount point and prepare variables for post data. Close {@link DOMTransactionChain} if any inside of object
     * {@link RestconfStrategy} provided as a parameter.
     *
     * @param uriInfo       uri info
     * @param payload       data
     * @param strategy      Object that perform the actual DS operations
     * @param schemaContext reference to actual {@link EffectiveModelContext}
     * @param point         point
     * @param insert        insert
     * @return {@link Response}
     */
    public static Response postData(final UriInfo uriInfo, final NormalizedNodeContext payload,
                                    final RestconfStrategy strategy,
                                    final EffectiveModelContext schemaContext, final Insert insert,
                                    final String point) {
        final YangInstanceIdentifier path = payload.getInstanceIdentifierContext().getInstanceIdentifier();
        final FluentFuture<? extends CommitInfo> future = submitData(path, payload.getData(),
                strategy, schemaContext, insert, point);
        final URI location = resolveLocation(uriInfo, path, schemaContext, payload.getData());
        final ResponseFactory dataFactory = new ResponseFactory(Status.CREATED).location(location);
        //This method will close transactionChain if any
        FutureCallbackTx.addCallback(future, POST_TX_TYPE, dataFactory, strategy);
        return dataFactory.build();
    }

    /**
     * Post data by type.
     *
     * @param path          path
     * @param data          data
     * @param strategy      object that perform the actual DS operations
     * @param schemaContext schema context of data
     * @param point         query parameter
     * @param insert        query parameter
     * @return {@link FluentFuture}
     */
    private static FluentFuture<? extends CommitInfo> submitData(final YangInstanceIdentifier path,
                                                                 final NormalizedNode<?, ?> data,
                                                                 final RestconfStrategy strategy,
                                                                 final EffectiveModelContext schemaContext,
                                                                 final Insert insert, final String point) {
        final RestconfTransaction transaction = strategy.prepareWriteExecution();
        if (insert == null) {
            makePost(path, data, schemaContext, strategy, transaction);
            return transaction.commit();
        }

        final DataSchemaNode schemaNode = PutDataTransactionUtil.checkListAndOrderedType(schemaContext, path);
        switch (insert) {
            case FIRST:
                if (schemaNode instanceof ListSchemaNode) {
                    final NormalizedNode<?, ?> readData = PutDataTransactionUtil.readList(strategy, path.getParent());
                    final OrderedMapNode readList = (OrderedMapNode) readData;
                    if (readList == null || readList.getValue().isEmpty()) {
                        makePost(path, data, schemaContext, strategy, transaction);
                        return transaction.commit();
                    }

                    transaction.delete(LogicalDatastoreType.CONFIGURATION, path.getParent().getParent());
                    simplePost(LogicalDatastoreType.CONFIGURATION, path, data, schemaContext, strategy, transaction);
                    makePost(path, readData, schemaContext, strategy, transaction);
                    return transaction.commit();
                } else {
                    final NormalizedNode<?, ?> readData = PutDataTransactionUtil.readList(strategy, path.getParent());

                    final OrderedLeafSetNode<?> readLeafList = (OrderedLeafSetNode<?>) readData;
                    if (readLeafList == null || readLeafList.getValue().isEmpty()) {
                        makePost(path, data, schemaContext, strategy, transaction);
                        return transaction.commit();
                    }

                    transaction.delete(LogicalDatastoreType.CONFIGURATION, path.getParent().getParent());
                    simplePost(LogicalDatastoreType.CONFIGURATION, path, data, schemaContext, strategy, transaction);
                    makePost(path, readData, schemaContext, strategy, transaction);
                    return transaction.commit();
                }
            case LAST:
                makePost(path, data, schemaContext, strategy, transaction);
                return transaction.commit();
            case BEFORE:
                if (schemaNode instanceof ListSchemaNode) {
                    final NormalizedNode<?, ?> readData = PutDataTransactionUtil.readList(strategy, path.getParent());
                    final OrderedMapNode readList = (OrderedMapNode) readData;
                    if (readList == null || readList.getValue().isEmpty()) {
                        makePost(path, data, schemaContext, strategy, transaction);
                        return transaction.commit();
                    }

                    insertWithPointListPost(LogicalDatastoreType.CONFIGURATION, path,
                            data, schemaContext, point, readList, true, strategy, transaction);
                    return transaction.commit();
                } else {
                    final NormalizedNode<?, ?> readData = PutDataTransactionUtil.readList(strategy, path.getParent());

                    final OrderedLeafSetNode<?> readLeafList = (OrderedLeafSetNode<?>) readData;
                    if (readLeafList == null || readLeafList.getValue().isEmpty()) {
                        makePost(path, data, schemaContext, strategy, transaction);
                        return transaction.commit();
                    }

                    insertWithPointLeafListPost(LogicalDatastoreType.CONFIGURATION,
                            path, data, schemaContext, point, readLeafList, true, strategy, transaction);
                    return transaction.commit();
                }
            case AFTER:
                if (schemaNode instanceof ListSchemaNode) {
                    final NormalizedNode<?, ?> readData = PutDataTransactionUtil.readList(strategy, path.getParent());
                    final OrderedMapNode readList = (OrderedMapNode) readData;
                    if (readList == null || readList.getValue().isEmpty()) {
                        makePost(path, data, schemaContext, strategy, transaction);
                        return transaction.commit();
                    }

                    insertWithPointListPost(LogicalDatastoreType.CONFIGURATION, path,
                            data, schemaContext, point, readList, false, strategy, transaction);
                    return transaction.commit();
                } else {
                    final NormalizedNode<?, ?> readData = PutDataTransactionUtil.readList(strategy, path.getParent());
                    final OrderedLeafSetNode<?> readLeafList = (OrderedLeafSetNode<?>) readData;
                    if (readLeafList == null || readLeafList.getValue().isEmpty()) {
                        makePost(path, data, schemaContext, strategy, transaction);
                        return transaction.commit();
                    }

                    insertWithPointLeafListPost(LogicalDatastoreType.CONFIGURATION,
                            path, data, schemaContext, point, readLeafList, true, strategy, transaction);
                    return transaction.commit();
                }
            default:
                throw new RestconfDocumentedException(
                    "Used bad value of insert parameter. Possible values are first, last, before or after, but was: "
                            + insert, RestconfError.ErrorType.PROTOCOL, RestconfError.ErrorTag.BAD_ATTRIBUTE);
        }
    }

    private static void insertWithPointLeafListPost(final LogicalDatastoreType datastore,
                                                    final YangInstanceIdentifier path,
                                                    final NormalizedNode<?, ?> payload,
                                                    final EffectiveModelContext schemaContext, final String point,
                                                    final OrderedLeafSetNode<?> readLeafList,
                                                    final boolean before, final RestconfStrategy strategy,
                                                    final RestconfTransaction transaction) {
        transaction.delete(datastore, path.getParent().getParent());
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
        transaction.merge(datastore, YangInstanceIdentifier.create(emptySubtree.getIdentifier()), emptySubtree);
        for (final LeafSetEntryNode<?> nodeChild : readLeafList.getValue()) {
            if (lastInsertedPosition == lastItemPosition) {
                checkItemDoesNotExists(strategy, transaction, datastore, path);
                transaction.create(datastore, path, payload);
            }
            final YangInstanceIdentifier childPath = path.getParent().getParent().node(nodeChild.getIdentifier());
            checkItemDoesNotExists(strategy, transaction, datastore, childPath);
            transaction.create(datastore, childPath, nodeChild);
            lastInsertedPosition++;
        }
    }

    private static void insertWithPointListPost(final LogicalDatastoreType datastore, final YangInstanceIdentifier path,
                                                final NormalizedNode<?, ?> payload,
                                                final EffectiveModelContext schemaContext, final String point,
                                                final MapNode readList, final boolean before,
                                                final RestconfStrategy strategy,
                                                final RestconfTransaction transaction) {
        transaction.delete(datastore, path.getParent().getParent());
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
        transaction.merge(datastore, YangInstanceIdentifier.create(emptySubtree.getIdentifier()), emptySubtree);
        for (final MapEntryNode mapEntryNode : readList.getValue()) {
            if (lastInsertedPosition == lastItemPosition) {
                checkItemDoesNotExists(strategy, transaction, datastore, path);
                transaction.create(datastore, path, payload);
            }
            final YangInstanceIdentifier childPath = path.getParent().getParent().node(mapEntryNode.getIdentifier());
            checkItemDoesNotExists(strategy, transaction, datastore, childPath);
            transaction.create(datastore, childPath, mapEntryNode);
            lastInsertedPosition++;
        }
    }

    private static void makePost(final YangInstanceIdentifier path, final NormalizedNode<?, ?> data,
                                 final SchemaContext schemaContext, final RestconfStrategy strategy,
                                 final RestconfTransaction transaction) {
        if (data instanceof MapNode) {
            boolean merge = false;
            for (final MapEntryNode child : ((MapNode) data).getValue()) {
                final YangInstanceIdentifier childPath = path.node(child.getIdentifier());
                checkItemDoesNotExists(strategy, transaction, LogicalDatastoreType.CONFIGURATION, childPath);
                if (!merge) {
                    merge = true;
                    TransactionUtil.ensureParentsByMerge(path, schemaContext, transaction);
                    final NormalizedNode<?, ?> emptySubTree = ImmutableNodes.fromInstanceId(schemaContext, path);
                    transaction.merge(LogicalDatastoreType.CONFIGURATION,
                            YangInstanceIdentifier.create(emptySubTree.getIdentifier()), emptySubTree);
                }
                transaction.create(LogicalDatastoreType.CONFIGURATION, childPath, child);
            }
        } else {
            checkItemDoesNotExists(strategy, transaction, LogicalDatastoreType.CONFIGURATION, path);

            TransactionUtil.ensureParentsByMerge(path, schemaContext, transaction);
            transaction.create(LogicalDatastoreType.CONFIGURATION, path, data);
        }
    }

    /**
     * Get location from {@link YangInstanceIdentifier} and {@link UriInfo}.
     *
     * @param uriInfo       uri info
     * @param initialPath   data path
     * @param schemaContext reference to {@link SchemaContext}
     * @return {@link URI}
     */
    private static URI resolveLocation(final UriInfo uriInfo, final YangInstanceIdentifier initialPath,
                                       final EffectiveModelContext schemaContext, final NormalizedNode<?, ?> data) {
        if (uriInfo == null) {
            return null;
        }

        YangInstanceIdentifier path = initialPath;
        if (data instanceof MapNode) {
            final Collection<MapEntryNode> children = ((MapNode) data).getValue();
            if (!children.isEmpty()) {
                path = path.node(children.iterator().next().getIdentifier());
            }
        }

        return uriInfo.getBaseUriBuilder()
                .path("data")
                .path(ParserIdentifier.stringFromYangInstanceIdentifier(path, schemaContext))
                .build();
    }

    private static void simplePost(final LogicalDatastoreType datastore, final YangInstanceIdentifier path,
                                   final NormalizedNode<?, ?> payload,
                                   final SchemaContext schemaContext, final RestconfStrategy strategy,
                                   final RestconfTransaction transaction) {
        checkItemDoesNotExists(strategy, transaction, datastore, path);
        TransactionUtil.ensureParentsByMerge(path, schemaContext, transaction);
        transaction.create(datastore, path, payload);
    }


    /**
     * Check if items do NOT already exists at specified {@code path}. Throws {@link RestconfDocumentedException} if
     * data already exists.
     *
     * @param strategy      Strategy for various RESTCONF operations
     * @param transaction   A handle to a set of DS operations
     * @param store         Datastore
     * @param path          Path to be checked
     */
    private static void checkItemDoesNotExists(final RestconfStrategy strategy,
                                               final RestconfTransaction transaction,
                                               final LogicalDatastoreType store, final YangInstanceIdentifier path) {
        final FluentFuture<Boolean> future = strategy.exists(store, path);
        final FutureDataFactory<Boolean> response = new FutureDataFactory<>();

        FutureCallbackTx.addCallback(future, POST_TX_TYPE, response);

        if (response.result) {
            // close transaction
            transaction.cancel();
            // throw error
            LOG.trace("Operation via Restconf was not executed because data at {} already exists", path);
            throw new RestconfDocumentedException(
                    "Data already exists", ErrorType.PROTOCOL, ErrorTag.DATA_EXISTS, path);
        }
    }

}
