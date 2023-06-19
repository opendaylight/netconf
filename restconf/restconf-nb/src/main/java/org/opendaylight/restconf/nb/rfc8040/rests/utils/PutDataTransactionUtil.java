/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.utils;

import com.google.common.util.concurrent.FluentFuture;
import java.util.Optional;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
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
import org.opendaylight.restconf.nb.rfc8040.utils.parser.ParserIdentifier;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNodeContainer;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContext;
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
     * @param payload       data to put
     * @param schemaContext reference to {@link EffectiveModelContext}
     * @param strategy      object that perform the actual DS operations
     * @param params        {@link WriteDataParams}
     * @return {@link Response}
     */
    public static Response putData(final NormalizedNodePayload payload, final EffectiveModelContext schemaContext,
                                   final RestconfStrategy strategy, final WriteDataParams params) {
        final YangInstanceIdentifier path = payload.getInstanceIdentifierContext().getInstanceIdentifier();

        final FluentFuture<Boolean> existsFuture = strategy.exists(LogicalDatastoreType.CONFIGURATION, path);
        final FutureDataFactory<Boolean> existsResponse = new FutureDataFactory<>();
        FutureCallbackTx.addCallback(existsFuture, PUT_TX_TYPE, existsResponse);

        final ResponseFactory responseFactory =
            new ResponseFactory(existsResponse.result ? Status.NO_CONTENT : Status.CREATED);
        final FluentFuture<? extends CommitInfo> submitData = submitData(path, schemaContext, strategy,
            payload.getData(), params);
        //This method will close transactionChain if any
        FutureCallbackTx.addCallback(submitData, PUT_TX_TYPE, responseFactory, path);
        return responseFactory.build();
    }

    /**
     * Put data to DS.
     *
     * @param path          path of data
     * @param schemaContext {@link SchemaContext}
     * @param strategy      object that perform the actual DS operations
     * @param data          data
     * @param params        {@link WriteDataParams}
     * @return {@link FluentFuture}
     */
    private static FluentFuture<? extends CommitInfo> submitData(final YangInstanceIdentifier path,
                                                                 final EffectiveModelContext schemaContext,
                                                                 final RestconfStrategy strategy,
                                                                 final NormalizedNode data,
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
        final InstanceIdentifierContext instanceIdentifier =
            // FIXME: Point should be able to give us this method
            ParserIdentifier.toInstanceIdentifier(point.value(), schemaContext, Optional.empty());
        int lastItemPosition = 0;
        for (final NormalizedNode nodeChild : readList.body()) {
            if (nodeChild.getIdentifier().equals(instanceIdentifier.getInstanceIdentifier().getLastPathArgument())) {
                break;
            }
            lastItemPosition++;
        }
        if (!before) {
            lastItemPosition++;
        }
        int lastInsertedPosition = 0;
        final NormalizedNode emptySubtree = ImmutableNodes.fromInstanceId(schemaContext, path.getParent());
        transaction.merge(YangInstanceIdentifier.create(emptySubtree.getIdentifier()), emptySubtree);
        for (final NormalizedNode nodeChild : readList.body()) {
            if (lastInsertedPosition == lastItemPosition) {
                transaction.replace(path, data, schemaContext);
            }
            final YangInstanceIdentifier childPath = path.getParent().node(nodeChild.getIdentifier());
            transaction.replace(childPath, nodeChild, schemaContext);
            lastInsertedPosition++;
        }
    }

    private static FluentFuture<? extends CommitInfo> makePut(final YangInstanceIdentifier path,
                                                              final EffectiveModelContext schemaContext,
                                                              final RestconfTransaction transaction,
                                                              final NormalizedNode data) {
        transaction.replace(path, data, schemaContext);
        return transaction.commit();
    }

    public static DataSchemaNode checkListAndOrderedType(final EffectiveModelContext ctx,
            final YangInstanceIdentifier path) {
        final YangInstanceIdentifier parent = path.getParent();
        final DataSchemaContext node = DataSchemaContextTree.from(ctx).findChild(parent).orElseThrow();
        final DataSchemaNode dataSchemaNode = node.dataSchemaNode();

        if (dataSchemaNode instanceof ListSchemaNode) {
            if (!((ListSchemaNode) dataSchemaNode).isUserOrdered()) {
                throw new RestconfDocumentedException("Insert parameter can be used only with ordered-by user list.",
                        ErrorType.PROTOCOL, ErrorTag.BAD_ELEMENT);
            }
            return dataSchemaNode;
        }
        if (dataSchemaNode instanceof LeafListSchemaNode) {
            if (!((LeafListSchemaNode) dataSchemaNode).isUserOrdered()) {
                throw new RestconfDocumentedException(
                        "Insert parameter can be used only with ordered-by user leaf-list.",
                        ErrorType.PROTOCOL, ErrorTag.BAD_ELEMENT);
            }
            return dataSchemaNode;
        }
        throw new RestconfDocumentedException("Insert parameter can be used only with list or leaf-list",
                ErrorType.PROTOCOL, ErrorTag.BAD_ELEMENT);
    }
}
