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
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.RestconfStrategy;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.ParserIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNodeContainer;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

/**
 * Util class to post data to DS.
 */
public final class PostDataTransactionUtil {
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
                                    final EffectiveModelContext schemaContext, final String insert,
                                    final String point) {
        final YangInstanceIdentifier path = payload.getInstanceIdentifierContext().getInstanceIdentifier();
        final FluentFuture<? extends CommitInfo> future = submitData(path, payload.getData(), strategy, schemaContext,
            insert, point);
        final URI location = resolveLocation(uriInfo, strategy.getInstanceIdentifier(), schemaContext,
            payload.getData());
        final ResponseFactory dataFactory = new ResponseFactory(Status.CREATED).location(location);
        //This method will close transactionChain if any
        FutureCallbackTx.addCallback(future, RestconfDataServiceConstant.PostData.POST_TX_TYPE, dataFactory,
            strategy.getTransactionChain(), path);
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
                                                                 final String insert, final String point) {
        strategy.prepareReadWriteExecution();
        if (insert == null) {
            makePost(path, data, strategy);
            return strategy.commit();
        }

        final DataSchemaNode schemaNode = PutDataTransactionUtil.checkListAndOrderedType(schemaContext, path);
        final NormalizedNode<?, ?> readData;
        switch (insert) {
            case "first":
                readData = PutDataTransactionUtil.readList(path.getParent(), schemaContext, strategy, schemaNode);
                if (readData == null || ((NormalizedNodeContainer<?, ?, ?>) readData).getValue().isEmpty()) {
                    strategy.replace(LogicalDatastoreType.CONFIGURATION, path, data, true);
                    return strategy.commit();
                }
                TransactionUtil.checkItemDoesNotExists(strategy, LogicalDatastoreType.CONFIGURATION, path,
                    RestconfDataServiceConstant.PostData.POST_TX_TYPE);
                strategy.remove(LogicalDatastoreType.CONFIGURATION, path.getParent().getParent());
                strategy.replace(LogicalDatastoreType.CONFIGURATION, path, data, true);
                for (final NormalizedNode<?, ?> entry : ((NormalizedNodeContainer<?, ?, ?>) readData).getValue()) {
                    final YangInstanceIdentifier childPath = path.getParent().getParent().node(entry.getIdentifier());
                    strategy.replace(LogicalDatastoreType.CONFIGURATION, childPath, entry, false);
                }
                return strategy.commit();
            case "last":
                makePost(path, data, strategy);
                return strategy.commit();
            case "before":
                readData = PutDataTransactionUtil.readList(path.getParent(), schemaContext, strategy, schemaNode);
                if (readData == null || ((NormalizedNodeContainer<?, ?, ?>) readData).getValue().isEmpty()) {
                    strategy.replace(LogicalDatastoreType.CONFIGURATION, path, data, true);
                    return strategy.commit();
                }
                TransactionUtil.checkItemDoesNotExists(strategy, LogicalDatastoreType.CONFIGURATION, path,
                    RestconfDataServiceConstant.PostData.POST_TX_TYPE);
                insertWithPointPost(path, data, schemaContext, point,
                    (NormalizedNodeContainer<?, ?, NormalizedNode<?, ?>>) readData, true, strategy);
                return strategy.commit();
            case "after":
                readData = PutDataTransactionUtil.readList(path.getParent(), schemaContext, strategy, schemaNode);
                if (readData == null || ((NormalizedNodeContainer<?, ?, ?>) readData).getValue().isEmpty()) {
                    strategy.replace(LogicalDatastoreType.CONFIGURATION, path, data, true);
                    return strategy.commit();
                }
                TransactionUtil.checkItemDoesNotExists(strategy, LogicalDatastoreType.CONFIGURATION, path,
                    RestconfDataServiceConstant.PostData.POST_TX_TYPE);
                insertWithPointPost(path, data, schemaContext, point,
                    (NormalizedNodeContainer<?, ?, NormalizedNode<?, ?>>) readData, false, strategy);
                return strategy.commit();
            default:
                throw new RestconfDocumentedException(
                    "Used bad value of insert parameter. Possible values are first, last, before or after, but was: "
                        + insert, RestconfError.ErrorType.PROTOCOL, RestconfError.ErrorTag.BAD_ATTRIBUTE);
        }
    }

    private static void insertWithPointPost(final YangInstanceIdentifier path,
                                            final NormalizedNode<?, ?> data,
                                            final EffectiveModelContext schemaContext, final String point,
                                            final NormalizedNodeContainer<?, ?, NormalizedNode<?, ?>> readList,
                                            final boolean before, final RestconfStrategy strategy) {
        final YangInstanceIdentifier parent = path.getParent().getParent();
        strategy.remove(LogicalDatastoreType.CONFIGURATION, parent);
        final InstanceIdentifierContext<?> instanceIdentifier =
            ParserIdentifier.toInstanceIdentifier(point, schemaContext, Optional.empty());
        int lastItemPosition = 0;
        for (final NormalizedNode<?, ?> nodeChild : readList.getValue()) {
            if (nodeChild.getIdentifier().equals(instanceIdentifier.getInstanceIdentifier().getLastPathArgument())) {
                break;
            }
            lastItemPosition++;
        }
        if (!before) {
            lastItemPosition++;
        }
        int lastInsertedPosition = 0;
        final NormalizedNode<?, ?> emptySubtree = ImmutableNodes.fromInstanceId(schemaContext, parent);
        strategy.merge(LogicalDatastoreType.CONFIGURATION,
            YangInstanceIdentifier.create(emptySubtree.getIdentifier()), emptySubtree);
        for (final NormalizedNode<?, ?> nodeChild : readList.getValue()) {
            if (lastInsertedPosition == lastItemPosition) {
                strategy.replace(LogicalDatastoreType.CONFIGURATION, path, data, true);
            }
            final YangInstanceIdentifier childPath = parent.node(nodeChild.getIdentifier());
            strategy.replace(LogicalDatastoreType.CONFIGURATION, childPath, nodeChild, false);
            lastInsertedPosition++;
        }
    }

    private static void makePost(final YangInstanceIdentifier path, final NormalizedNode<?, ?> data,
                                 final RestconfStrategy strategy) {
        try {
            strategy.create(LogicalDatastoreType.CONFIGURATION, path, data);
        } catch (RestconfDocumentedException e) {
            // close transaction if any and pass exception further
            strategy.cancel();
            throw e;
        }
    }

    /**
     * Get location from {@link YangInstanceIdentifier} and {@link UriInfo}.
     *
     * @param uriInfo                uri info
     * @param yangInstanceIdentifier reference to {@link InstanceIdentifierContext}
     * @param schemaContext          reference to {@link SchemaContext}
     * @return {@link URI}
     */
    private static URI resolveLocation(final UriInfo uriInfo, final InstanceIdentifierContext<?> yangInstanceIdentifier,
                                       final EffectiveModelContext schemaContext, final NormalizedNode<?, ?> data) {
        if (uriInfo == null) {
            return null;
        }

        YangInstanceIdentifier path = yangInstanceIdentifier.getInstanceIdentifier();

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
}
