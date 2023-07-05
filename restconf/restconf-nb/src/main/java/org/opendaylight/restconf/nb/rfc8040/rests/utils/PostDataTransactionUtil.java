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
import java.util.Optional;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.restconf.api.query.InsertParam;
import org.opendaylight.restconf.api.query.PointParam;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.WriteDataParams;
import org.opendaylight.restconf.nb.rfc8040.legacy.NormalizedNodePayload;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.RestconfStrategy;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.RestconfTransaction;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.IdentifierCodec;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.ParserIdentifier;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNodeContainer;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Util class to post data to DS.
 */
public final class PostDataTransactionUtil {
    private static final Logger LOG = LoggerFactory.getLogger(PostDataTransactionUtil.class);
    private static final String POST_TX_TYPE = "POST";

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
     * @param params        {@link WriteDataParams}
     * @return {@link Response}
     */
    public static Response postData(final UriInfo uriInfo, final NormalizedNodePayload payload,
                                    final RestconfStrategy strategy,
                                    final EffectiveModelContext schemaContext, final WriteDataParams params) {
        final YangInstanceIdentifier path = payload.getInstanceIdentifierContext().getInstanceIdentifier();
        final FluentFuture<? extends CommitInfo> future = submitData(path, payload.getData(),
                strategy, schemaContext, params);
        final URI location = resolveLocation(uriInfo, path, schemaContext, payload.getData());
        final ResponseFactory dataFactory = new ResponseFactory(Status.CREATED).location(location);
        //This method will close transactionChain if any
        FutureCallbackTx.addCallback(future, POST_TX_TYPE, dataFactory, path);
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
                                                                 final NormalizedNode data,
                                                                 final RestconfStrategy strategy,
                                                                 final EffectiveModelContext schemaContext,
                                                                 final WriteDataParams params) {
        final RestconfTransaction transaction = strategy.prepareWriteExecution();
        final InsertParam insert = params.insert();
        if (insert == null) {
            makePost(path, data, schemaContext, transaction);
            return transaction.commit();
        }

        PutDataTransactionUtil.checkListAndOrderedType(schemaContext, path);
        final NormalizedNode readData;
        switch (insert) {
            case FIRST:
                readData = PutDataTransactionUtil.readList(strategy, path.getParent().getParent());
                if (readData == null || ((NormalizedNodeContainer<?>) readData).isEmpty()) {
                    transaction.replace(path, data, schemaContext);
                    return transaction.commit();
                }
                checkItemDoesNotExists(strategy.exists(LogicalDatastoreType.CONFIGURATION, path), path);
                transaction.remove(path.getParent().getParent());
                transaction.replace(path, data, schemaContext);
                transaction.replace(path.getParent().getParent(), readData, schemaContext);
                return transaction.commit();
            case LAST:
                makePost(path, data, schemaContext, transaction);
                return transaction.commit();
            case BEFORE:
                readData = PutDataTransactionUtil.readList(strategy, path.getParent().getParent());
                if (readData == null || ((NormalizedNodeContainer<?>) readData).isEmpty()) {
                    transaction.replace(path, data, schemaContext);
                    return transaction.commit();
                }
                checkItemDoesNotExists(strategy.exists(LogicalDatastoreType.CONFIGURATION, path), path);
                insertWithPointPost(path, data, schemaContext, params.getPoint(),
                    (NormalizedNodeContainer<?>) readData, true, transaction);
                return transaction.commit();
            case AFTER:
                readData = PutDataTransactionUtil.readList(strategy, path.getParent().getParent());
                if (readData == null || ((NormalizedNodeContainer<?>) readData).isEmpty()) {
                    transaction.replace(path, data, schemaContext);
                    return transaction.commit();
                }
                checkItemDoesNotExists(strategy.exists(LogicalDatastoreType.CONFIGURATION, path), path);
                insertWithPointPost(path, data, schemaContext, params.getPoint(),
                    (NormalizedNodeContainer<?>) readData, false, transaction);
                return transaction.commit();
            default:
                throw new RestconfDocumentedException(
                    "Used bad value of insert parameter. Possible values are first, last, before or after, but was: "
                        + insert, ErrorType.PROTOCOL, ErrorTag.BAD_ATTRIBUTE);
        }
    }

    private static void insertWithPointPost(final YangInstanceIdentifier path, final NormalizedNode data,
                                            final EffectiveModelContext schemaContext, final PointParam point,
                                            final NormalizedNodeContainer<?> readList, final boolean before,
                                            final RestconfTransaction transaction) {
        final YangInstanceIdentifier parent = path.getParent().getParent();
        transaction.remove(parent);
        final InstanceIdentifierContext instanceIdentifier =
            // FIXME: Point should be able to give us this method
            ParserIdentifier.toInstanceIdentifier(point.value(), schemaContext, Optional.empty());
        int lastItemPosition = 0;
        for (final NormalizedNode nodeChild : readList.body()) {
            if (nodeChild.name().equals(instanceIdentifier.getInstanceIdentifier().getLastPathArgument())) {
                break;
            }
            lastItemPosition++;
        }
        if (!before) {
            lastItemPosition++;
        }
        int lastInsertedPosition = 0;
        final NormalizedNode emptySubtree = ImmutableNodes.fromInstanceId(schemaContext, parent);
        transaction.merge(YangInstanceIdentifier.of(emptySubtree.name()), emptySubtree);
        for (final NormalizedNode nodeChild : readList.body()) {
            if (lastInsertedPosition == lastItemPosition) {
                transaction.replace(path, data, schemaContext);
            }
            final YangInstanceIdentifier childPath = parent.node(nodeChild.name());
            transaction.replace(childPath, nodeChild, schemaContext);
            lastInsertedPosition++;
        }
    }

    private static void makePost(final YangInstanceIdentifier path, final NormalizedNode data,
                                 final EffectiveModelContext schemaContext, final RestconfTransaction transaction) {
        try {
            transaction.create(path, data, schemaContext);
        } catch (RestconfDocumentedException e) {
            // close transaction if any and pass exception further
            transaction.cancel();
            throw e;
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
                                       final EffectiveModelContext schemaContext, final NormalizedNode data) {
        if (uriInfo == null) {
            return null;
        }

        YangInstanceIdentifier path = initialPath;
        if (data instanceof MapNode mapData) {
            final var children = mapData.body();
            if (!children.isEmpty()) {
                path = path.node(children.iterator().next().name());
            }
        }

        return uriInfo.getBaseUriBuilder().path("data").path(IdentifierCodec.serialize(path, schemaContext)).build();
    }

    /**
     * Check if items do NOT already exists at specified {@code path}. Throws {@link RestconfDocumentedException} if
     * data already exists.
     *
     * @param isExistsFuture if checked data exists
     * @param path           Path to be checked
     */
    public static void checkItemDoesNotExists(final FluentFuture<Boolean> isExistsFuture,
                                              final YangInstanceIdentifier path) {
        final FutureDataFactory<Boolean> response = new FutureDataFactory<>();
        FutureCallbackTx.addCallback(isExistsFuture, POST_TX_TYPE, response);

        if (response.result) {
            LOG.trace("Operation via Restconf was not executed because data at {} already exists", path);
            throw new RestconfDocumentedException(
                "Data already exists", ErrorType.PROTOCOL, ErrorTag.DATA_EXISTS, path);
        }
    }
}
