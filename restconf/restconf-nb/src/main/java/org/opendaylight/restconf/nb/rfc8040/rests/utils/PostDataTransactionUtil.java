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
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.restconf.api.query.PointParam;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.WriteDataParams;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.RestconfStrategy;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.RestconfTransaction;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.YangInstanceIdentifierDeserializer;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNodeContainer;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextTree;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.LeafListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Util class to post data to DS.
 */
public final class PostDataTransactionUtil {
    private static final Logger LOG = LoggerFactory.getLogger(PostDataTransactionUtil.class);

    private PostDataTransactionUtil() {
        // Hidden on purpose
    }

    /**
     * Check mount point and prepare variables for post data. Close {@link DOMTransactionChain} if any inside of object
     * {@link RestconfStrategy} provided as a parameter.
     *
     * @param path          path
     * @param data          data
     * @param strategy      Object that perform the actual DS operations
     * @param schemaContext reference to actual {@link EffectiveModelContext}
     * @param params        {@link WriteDataParams}
     */
    public static void postData(final YangInstanceIdentifier path, final NormalizedNode data,
            final RestconfStrategy strategy, final EffectiveModelContext schemaContext, final WriteDataParams params) {
        TransactionUtil.syncCommit(submitData(path, data, strategy, schemaContext, params), "POST", path);
    }

    public static DataSchemaNode checkListAndOrderedType(final EffectiveModelContext ctx,
            final YangInstanceIdentifier path) {
        final var dataSchemaNode = DataSchemaContextTree.from(ctx).findChild(path).orElseThrow().dataSchemaNode();

        final String message;
        if (dataSchemaNode instanceof ListSchemaNode listSchema) {
            if (listSchema.isUserOrdered()) {
                return listSchema;
            }
            message = "Insert parameter can be used only with ordered-by user list.";
        } else if (dataSchemaNode instanceof LeafListSchemaNode leafListSchema) {
            if (leafListSchema.isUserOrdered()) {
                return leafListSchema;
            }
            message = "Insert parameter can be used only with ordered-by user leaf-list.";
        } else {
            message = "Insert parameter can be used only with list or leaf-list";
        }
        throw new RestconfDocumentedException(message, ErrorType.PROTOCOL, ErrorTag.BAD_ELEMENT);
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
    private static ListenableFuture<? extends CommitInfo> submitData(final YangInstanceIdentifier path,
            final NormalizedNode data, final RestconfStrategy strategy, final EffectiveModelContext schemaContext,
            final WriteDataParams params) {
        final var transaction = strategy.prepareWriteExecution();
        final var insert = params.insert();
        if (insert == null) {
            return makePost(path, data, schemaContext, transaction);
        }

        final var parentPath = path.coerceParent();
        checkListAndOrderedType(schemaContext, parentPath);
        final var grandParentPath = parentPath.coerceParent();

        return switch (insert) {
            case FIRST -> {
                final var readData = transaction.readList(grandParentPath);
                if (readData == null || readData.isEmpty()) {
                    transaction.replace(path, data, schemaContext);
                } else {
                    checkItemDoesNotExists(strategy.exists(LogicalDatastoreType.CONFIGURATION, path), path);
                    transaction.remove(grandParentPath);
                    transaction.replace(path, data, schemaContext);
                    transaction.replace(grandParentPath, readData, schemaContext);
                }
                yield transaction.commit();
            }
            case LAST -> makePost(path, data, schemaContext, transaction);
            case BEFORE -> {
                final var readData = transaction.readList(grandParentPath);
                if (readData == null || readData.isEmpty()) {
                    transaction.replace(path, data, schemaContext);
                } else {
                    checkItemDoesNotExists(strategy.exists(LogicalDatastoreType.CONFIGURATION, path), path);
                    insertWithPointPost(path, data, schemaContext, params.getPoint(), readData, true, transaction);
                }
                yield transaction.commit();
            }
            case AFTER -> {
                final var readData = transaction.readList(grandParentPath);
                if (readData == null || readData.isEmpty()) {
                    transaction.replace(path, data, schemaContext);
                } else {
                    checkItemDoesNotExists(strategy.exists(LogicalDatastoreType.CONFIGURATION, path), path);
                    insertWithPointPost(path, data, schemaContext, params.getPoint(), readData, false, transaction);
                }
                yield transaction.commit();
            }
        };
    }

    private static void insertWithPointPost(final YangInstanceIdentifier path, final NormalizedNode data,
                                            final EffectiveModelContext schemaContext, final PointParam point,
                                            final NormalizedNodeContainer<?> readList, final boolean before,
                                            final RestconfTransaction transaction) {
        final YangInstanceIdentifier parent = path.coerceParent().coerceParent();
        transaction.remove(parent);
        final var pointArg = YangInstanceIdentifierDeserializer.create(schemaContext, point.value()).path
            .getLastPathArgument();
        int lastItemPosition = 0;
        for (var nodeChild : readList.body()) {
            if (nodeChild.name().equals(pointArg)) {
                break;
            }
            lastItemPosition++;
        }
        if (!before) {
            lastItemPosition++;
        }
        int lastInsertedPosition = 0;
        final var emptySubtree = ImmutableNodes.fromInstanceId(schemaContext, parent);
        transaction.merge(YangInstanceIdentifier.of(emptySubtree.name()), emptySubtree);
        for (var nodeChild : readList.body()) {
            if (lastInsertedPosition == lastItemPosition) {
                transaction.replace(path, data, schemaContext);
            }
            final YangInstanceIdentifier childPath = parent.node(nodeChild.name());
            transaction.replace(childPath, nodeChild, schemaContext);
            lastInsertedPosition++;
        }
    }

    private static ListenableFuture<? extends CommitInfo> makePost(final YangInstanceIdentifier path,
            final NormalizedNode data, final EffectiveModelContext schemaContext,
            final RestconfTransaction transaction) {
        try {
            transaction.create(path, data, schemaContext);
        } catch (RestconfDocumentedException e) {
            // close transaction if any and pass exception further
            transaction.cancel();
            throw e;
        }

        return transaction.commit();
    }

    /**
     * Check if items do NOT already exists at specified {@code path}.
     *
     * @param existsFuture if checked data exists
     * @param path         Path to be checked
     * @throws RestconfDocumentedException if data already exists.
     */
    public static void checkItemDoesNotExists(final ListenableFuture<Boolean> existsFuture,
                                              final YangInstanceIdentifier path) {
        if (TransactionUtil.syncAccess(existsFuture, path)) {
            LOG.trace("Operation via Restconf was not executed because data at {} already exists", path);
            throw new RestconfDocumentedException("Data already exists", ErrorType.PROTOCOL, ErrorTag.DATA_EXISTS,
                path);
        }
    }
}
