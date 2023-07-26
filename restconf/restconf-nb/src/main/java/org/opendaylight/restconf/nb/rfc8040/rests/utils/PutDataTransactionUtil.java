/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.utils;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.ExecutionException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.restconf.api.query.InsertParam;
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
 *
 */
public final class PutDataTransactionUtil {
    private static final String PUT_TX_TYPE = "PUT";

    private PutDataTransactionUtil() {
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
        final var existsFuture = strategy.exists(LogicalDatastoreType.CONFIGURATION, path);
        final boolean exists;
        try {
            exists = existsFuture.get();
        } catch (ExecutionException e) {
            throw new RestconfDocumentedException("Failed to access " + path, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RestconfDocumentedException("Interrupted while accessing " + path, e);
        }

        TransactionUtil.syncCommit(submitData(path, schemaContext, strategy, data, params), PUT_TX_TYPE, path);
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
        final RestconfTransaction transaction = strategy.prepareWriteExecution();
        final InsertParam insert = params.insert();
        if (insert == null) {
            return makePut(path, schemaContext, transaction, data);
        }

        checkListAndOrderedType(schemaContext, path);
        final NormalizedNode readData;
        switch (insert) {
            case FIRST:
                readData = readList(strategy, path.getParent());
                if (readData == null || ((NormalizedNodeContainer<?>) readData).isEmpty()) {
                    return makePut(path, schemaContext, transaction, data);
                }
                transaction.remove(path.getParent());
                transaction.replace(path, data, schemaContext);
                transaction.replace(path.getParent(), readData, schemaContext);
                return transaction.commit();
            case LAST:
                return makePut(path, schemaContext, transaction, data);
            case BEFORE:
                readData = readList(strategy, path.getParent());
                if (readData == null || ((NormalizedNodeContainer<?>) readData).isEmpty()) {
                    return makePut(path, schemaContext, transaction, data);
                }
                insertWithPointPut(transaction, path, data, schemaContext, params.getPoint(),
                    (NormalizedNodeContainer<?>) readData, true);
                return transaction.commit();
            case AFTER:
                readData = readList(strategy, path.getParent());
                if (readData == null || ((NormalizedNodeContainer<?>) readData).isEmpty()) {
                    return makePut(path, schemaContext, transaction, data);
                }
                insertWithPointPut(transaction, path, data, schemaContext, params.getPoint(),
                    (NormalizedNodeContainer<?>) readData, false);
                return transaction.commit();
            default:
                throw new RestconfDocumentedException(
                        "Used bad value of insert parameter. Possible values are first, last, before or after, "
                                + "but was: " + insert, ErrorType.PROTOCOL, ErrorTag.BAD_ATTRIBUTE);
        }
    }

    // FIXME: this method is only called from a context where we are modifying data. This should be part of strategy,
    //        requiring an already-open transaction. It also must return a future, so it can be properly composed.
    static NormalizedNode readList(final RestconfStrategy strategy, final YangInstanceIdentifier path) {
        return ReadDataTransactionUtil.readDataViaTransaction(strategy, LogicalDatastoreType.CONFIGURATION, path);
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
        final var dataSchemaNode = DataSchemaContextTree.from(ctx).findChild(path.getParent())
            .orElseThrow()
            .dataSchemaNode();

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
