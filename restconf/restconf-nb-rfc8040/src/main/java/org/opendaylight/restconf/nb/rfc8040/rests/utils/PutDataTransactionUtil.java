/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.utils;

import com.google.common.util.concurrent.FluentFuture;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
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
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNodeContainer;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextNode;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextTree;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.LeafListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;

/**
 * Util class for put data to DS.
 *
 */
public final class PutDataTransactionUtil {
    private static final String PUT_TX_TYPE = "PUT";

    private PutDataTransactionUtil() {
    }

    /**
     * Valid input data with {@link SchemaNode}.
     *
     * @param schemaNode {@link SchemaNode}
     * @param payload    input data
     */
    public static void validInputData(final SchemaNode schemaNode, final NormalizedNodeContext payload) {
        if (schemaNode != null && payload.getData() == null) {
            throw new RestconfDocumentedException("Input is required.", ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE);
        } else if (schemaNode == null && payload.getData() != null) {
            throw new RestconfDocumentedException("No input expected.", ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE);
        }
    }

    /**
     * Valid top level node name.
     *
     * @param path    path of node
     * @param payload data
     */
    public static void validTopLevelNodeName(final YangInstanceIdentifier path, final NormalizedNodeContext payload) {
        final String payloadName = payload.getData().getNodeType().getLocalName();

        if (path.isEmpty()) {
            if (!payload.getData().getNodeType().equals(RestconfDataServiceConstant.NETCONF_BASE_QNAME)) {
                throw new RestconfDocumentedException("Instance identifier has to contain at least one path argument",
                        ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE);
            }
        } else {
            final String identifierName = path.getLastPathArgument().getNodeType().getLocalName();
            if (!payloadName.equals(identifierName)) {
                throw new RestconfDocumentedException(
                        "Payload name (" + payloadName + ") is different from identifier name (" + identifierName + ")",
                        ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE);
            }
        }
    }

    /**
     * Validates whether keys in {@code payload} are equal to values of keys in
     * {@code iiWithData} for list schema node.
     *
     * @throws RestconfDocumentedException if key values or key count in payload and URI isn't equal
     */
    public static void validateListKeysEqualityInPayloadAndUri(final NormalizedNodeContext payload) {
        final InstanceIdentifierContext<?> iiWithData = payload.getInstanceIdentifierContext();
        final PathArgument lastPathArgument = iiWithData.getInstanceIdentifier().getLastPathArgument();
        final SchemaNode schemaNode = iiWithData.getSchemaNode();
        final NormalizedNode<?, ?> data = payload.getData();
        if (schemaNode instanceof ListSchemaNode) {
            final List<QName> keyDefinitions = ((ListSchemaNode) schemaNode).getKeyDefinition();
            if (lastPathArgument instanceof NodeIdentifierWithPredicates && data instanceof MapEntryNode) {
                final Map<QName, Object> uriKeyValues = ((NodeIdentifierWithPredicates) lastPathArgument).asMap();
                isEqualUriAndPayloadKeyValues(uriKeyValues, (MapEntryNode) data, keyDefinitions);
            }
        }
    }

    private static void isEqualUriAndPayloadKeyValues(final Map<QName, Object> uriKeyValues, final MapEntryNode payload,
                                                      final List<QName> keyDefinitions) {
        final Map<QName, Object> mutableCopyUriKeyValues = new HashMap<>(uriKeyValues);
        for (final QName keyDefinition : keyDefinitions) {
            final Object uriKeyValue = RestconfDocumentedException.throwIfNull(
                    mutableCopyUriKeyValues.remove(keyDefinition), ErrorType.PROTOCOL, ErrorTag.DATA_MISSING,
                    "Missing key %s in URI.", keyDefinition);

            final Object dataKeyValue = payload.getIdentifier().getValue(keyDefinition);

            if (!uriKeyValue.equals(dataKeyValue)) {
                final String errMsg = "The value '" + uriKeyValue + "' for key '" + keyDefinition.getLocalName()
                        + "' specified in the URI doesn't match the value '" + dataKeyValue
                        + "' specified in the message body. ";
                throw new RestconfDocumentedException(errMsg, ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
            }
        }
    }

    /**
     * Check mount point and prepare variables for put data to DS. Close {@link DOMTransactionChain} if any
     * inside of object {@link RestconfStrategy} provided as a parameter if any.
     *
     * @param payload       data to put
     * @param schemaContext reference to {@link EffectiveModelContext}
     * @param strategy      object that perform the actual DS operations
     * @param point         query parameter
     * @param insert        query parameter
     * @return {@link Response}
     */
    public static Response putData(final NormalizedNodeContext payload, final EffectiveModelContext schemaContext,
                                   final RestconfStrategy strategy, final Insert insert, final String point) {
        final YangInstanceIdentifier path = payload.getInstanceIdentifierContext().getInstanceIdentifier();

        final FluentFuture<Boolean> existsFuture = strategy.exists(LogicalDatastoreType.CONFIGURATION, path);
        final FutureDataFactory<Boolean> existsResponse = new FutureDataFactory<>();
        FutureCallbackTx.addCallback(existsFuture, PUT_TX_TYPE, existsResponse);

        final ResponseFactory responseFactory =
            new ResponseFactory(existsResponse.result ? Status.NO_CONTENT : Status.CREATED);
        final FluentFuture<? extends CommitInfo> submitData = submitData(path, schemaContext, strategy,
            payload.getData(), insert, point);
        //This method will close transactionChain if any
        FutureCallbackTx.addCallback(submitData, PUT_TX_TYPE, responseFactory, strategy, path);
        return responseFactory.build();
    }

    /**
     * Put data to DS.
     *
     * @param path          path of data
     * @param schemaContext {@link SchemaContext}
     * @param strategy      object that perform the actual DS operations
     * @param data          data
     * @param point         query parameter
     * @param insert        query parameter
     * @return {@link FluentFuture}
     */
    private static FluentFuture<? extends CommitInfo> submitData(final YangInstanceIdentifier path,
                                                                 final EffectiveModelContext schemaContext,
                                                                 final RestconfStrategy strategy,
                                                                 final NormalizedNode<?, ?> data,
                                                                 final Insert insert, final String point) {
        final RestconfTransaction transaction = strategy.prepareWriteExecution();
        if (insert == null) {
            return makePut(path, schemaContext, transaction, data);
        }

        checkListAndOrderedType(schemaContext, path);
        final NormalizedNode<?, ?> readData;
        switch (insert) {
            case FIRST:
                readData = readList(strategy, path.getParent());
                if (readData == null || ((NormalizedNodeContainer<?, ?, ?>) readData).getValue().isEmpty()) {
                    return makePut(path, schemaContext, transaction, data);
                }
                transaction.remove(LogicalDatastoreType.CONFIGURATION, path.getParent());
                transaction.replace(LogicalDatastoreType.CONFIGURATION, path, data, schemaContext);
                transaction.replace(LogicalDatastoreType.CONFIGURATION, path.getParent(), readData, schemaContext);
                return transaction.commit();
            case LAST:
                return makePut(path, schemaContext, transaction, data);
            case BEFORE:
                readData = readList(strategy, path.getParent());
                if (readData == null || ((NormalizedNodeContainer<?, ?, ?>) readData).getValue().isEmpty()) {
                    return makePut(path, schemaContext, transaction, data);
                }
                insertWithPointPut(transaction, path, data, schemaContext, point,
                    (NormalizedNodeContainer<?, ?, NormalizedNode<?, ?>>) readData, true);
                return transaction.commit();
            case AFTER:
                readData = readList(strategy, path.getParent());
                if (readData == null || ((NormalizedNodeContainer<?, ?, ?>) readData).getValue().isEmpty()) {
                    return makePut(path, schemaContext, transaction, data);
                }
                insertWithPointPut(transaction, path, data, schemaContext, point,
                    (NormalizedNodeContainer<?, ?, NormalizedNode<?, ?>>) readData, false);
                return transaction.commit();
            default:
                throw new RestconfDocumentedException(
                        "Used bad value of insert parameter. Possible values are first, last, before or after, "
                                + "but was: " + insert, RestconfError.ErrorType.PROTOCOL, ErrorTag.BAD_ATTRIBUTE);
        }
    }

    // FIXME: this method is only called from a context where we are modifying data. This should be part of strategy,
    //        requiring an already-open transaction. It also must return a future, so it can be properly composed.
    static NormalizedNode<?, ?> readList(final RestconfStrategy strategy, final YangInstanceIdentifier path) {
        return ReadDataTransactionUtil.readDataViaTransaction(strategy, LogicalDatastoreType.CONFIGURATION,
            path,false);
    }

    private static void insertWithPointPut(final RestconfTransaction transaction,
                                           final YangInstanceIdentifier path,
                                           final NormalizedNode<?, ?> data,
                                           final EffectiveModelContext schemaContext, final String point,
                                           final NormalizedNodeContainer<?, ?, NormalizedNode<?, ?>> readList,
                                           final boolean before) {
        transaction.remove(LogicalDatastoreType.CONFIGURATION, path.getParent());
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
        final NormalizedNode<?, ?> emptySubtree = ImmutableNodes.fromInstanceId(schemaContext, path.getParent());
        transaction.merge(LogicalDatastoreType.CONFIGURATION,
            YangInstanceIdentifier.create(emptySubtree.getIdentifier()),
            emptySubtree);
        for (final NormalizedNode<?, ?> nodeChild : readList.getValue()) {
            if (lastInsertedPosition == lastItemPosition) {
                transaction.replace(LogicalDatastoreType.CONFIGURATION, path, data, schemaContext);
            }
            final YangInstanceIdentifier childPath = path.getParent().node(nodeChild.getIdentifier());
            transaction.replace(LogicalDatastoreType.CONFIGURATION, childPath, nodeChild, schemaContext);
            lastInsertedPosition++;
        }
    }

    private static FluentFuture<? extends CommitInfo> makePut(final YangInstanceIdentifier path,
                                                              final SchemaContext schemaContext,
                                                              final RestconfTransaction transaction,
                                                              final NormalizedNode<?, ?> data) {
        transaction.replace(LogicalDatastoreType.CONFIGURATION, path, data, schemaContext);
        return transaction.commit();
    }

    public static DataSchemaNode checkListAndOrderedType(final EffectiveModelContext ctx,
            final YangInstanceIdentifier path) {
        final YangInstanceIdentifier parent = path.getParent();
        final DataSchemaContextNode<?> node = DataSchemaContextTree.from(ctx).findChild(parent).orElseThrow();
        final DataSchemaNode dataSchemaNode = node.getDataSchemaNode();

        if (dataSchemaNode instanceof ListSchemaNode) {
            if (!((ListSchemaNode) dataSchemaNode).isUserOrdered()) {
                throw new RestconfDocumentedException("Insert parameter can be used only with ordered-by user list.",
                        RestconfError.ErrorType.PROTOCOL, ErrorTag.BAD_ELEMENT);
            }
            return dataSchemaNode;
        }
        if (dataSchemaNode instanceof LeafListSchemaNode) {
            if (!((LeafListSchemaNode) dataSchemaNode).isUserOrdered()) {
                throw new RestconfDocumentedException(
                        "Insert parameter can be used only with ordered-by user leaf-list.",
                        RestconfError.ErrorType.PROTOCOL, ErrorTag.BAD_ELEMENT);
            }
            return dataSchemaNode;
        }
        throw new RestconfDocumentedException("Insert parameter can be used only with list or leaf-list",
                RestconfError.ErrorType.PROTOCOL, ErrorTag.BAD_ELEMENT);
    }
}
