/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.utils;

import com.google.common.util.concurrent.ListenableFuture;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
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
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

/**
 * Util class for put data to DS.
 */
public final class PutDataTransactionUtil {
    private PutDataTransactionUtil() {
        // Hidden on purpose
    }

    /**
     * Check mount point and prepare variables for put data to DS. Close {@link DOMTransactionChain} if any
     * inside of object {@link RestconfStrategy} provided as a parameter if any.
     *
     * @param path          path of data
     * @param data          data
     * @param schemaContext reference to {@link EffectiveModelContext}
     * @param strategy      object that perform the actual DS operations
     * @param params        {@link WriteDataParams}
     * @return {@link Response}
     */
    public static Response putData(final YangInstanceIdentifier path, final NormalizedNode data,
            final EffectiveModelContext schemaContext, final RestconfStrategy strategy, final WriteDataParams params) {
        final var exists = TransactionUtil.syncAccess(strategy.exists(LogicalDatastoreType.CONFIGURATION, path), path);
        TransactionUtil.syncCommit(submitData(path, schemaContext, strategy, data, params), "PUT", path);
        // TODO: Status.CREATED implies a location...
        return exists ? Response.noContent().build() : Response.status(Status.CREATED).build();
    }

    /**
     * Put data to DS.
     *
     * @param path          path of data
     * @param schemaContext {@link SchemaContext}
     * @param strategy      object that perform the actual DS operations
     * @param data          data
     * @param params        {@link WriteDataParams}
     * @return A {@link ListenableFuture}
     */
    private static ListenableFuture<? extends CommitInfo> submitData(final YangInstanceIdentifier path,
            final EffectiveModelContext schemaContext, final RestconfStrategy strategy, final NormalizedNode data,
            final WriteDataParams params) {
        final var transaction = strategy.prepareWriteExecution();
        final var insert = params.insert();
        if (insert == null) {
            return makePut(path, schemaContext, transaction, data);
        }

        final var parentPath = path.coerceParent();
        checkListAndOrderedType(schemaContext, parentPath);

        return switch (insert) {
            case FIRST -> {
                final var readData = transaction.readList(parentPath);
                if (readData == null || readData.isEmpty()) {
                    yield makePut(path, schemaContext, transaction, data);
                }
                transaction.remove(parentPath);
                transaction.replace(path, data, schemaContext);
                transaction.replace(parentPath, readData, schemaContext);
                yield transaction.commit();
            }
            case LAST -> makePut(path, schemaContext, transaction, data);
            case BEFORE -> {
                final var readData = transaction.readList(parentPath);
                if (readData == null || readData.isEmpty()) {
                    yield makePut(path, schemaContext, transaction, data);
                }
                insertWithPointPut(transaction, path, data, schemaContext, params.getPoint(), readData, true);
                yield transaction.commit();
            }
            case AFTER -> {
                final var readData = transaction.readList(parentPath);
                if (readData == null || readData.isEmpty()) {
                    yield makePut(path, schemaContext, transaction, data);
                }
                insertWithPointPut(transaction, path, data, schemaContext, params.getPoint(), readData, false);
                yield transaction.commit();
            }
        };
    }

    private static void insertWithPointPut(final RestconfTransaction transaction,
                                           final YangInstanceIdentifier path,
                                           final NormalizedNode data,
                                           final EffectiveModelContext schemaContext, final PointParam point,
                                           final NormalizedNodeContainer<?> readList, final boolean before) {
        transaction.remove(path.getParent());
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
        final var emptySubtree = ImmutableNodes.fromInstanceId(schemaContext, path.getParent());
        transaction.merge(YangInstanceIdentifier.of(emptySubtree.name()), emptySubtree);
        for (var nodeChild : readList.body()) {
            if (lastInsertedPosition == lastItemPosition) {
                transaction.replace(path, data, schemaContext);
            }
            final YangInstanceIdentifier childPath = path.coerceParent().node(nodeChild.name());
            transaction.replace(childPath, nodeChild, schemaContext);
            lastInsertedPosition++;
        }
    }

    private static ListenableFuture<? extends CommitInfo> makePut(final YangInstanceIdentifier path,
            final EffectiveModelContext schemaContext, final RestconfTransaction transaction,
            final NormalizedNode data) {
        transaction.replace(path, data, schemaContext);
        return transaction.commit();
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
}
