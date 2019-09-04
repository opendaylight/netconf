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
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteTransaction;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.context.NormalizedNodeContext;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorTag;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorType;
import org.opendaylight.restconf.nb.rfc8040.handlers.TransactionChainHandler;
import org.opendaylight.restconf.nb.rfc8040.references.SchemaContextRef;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.TransactionVarsWrapper;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.ParserIdentifier;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.OrderedLeafSetNode;
import org.opendaylight.yangtools.yang.data.api.schema.OrderedMapNode;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextNode;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextTree;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;

/**
 * Util class for put data to DS.
 *
 */
public final class PutDataTransactionUtil {

    private PutDataTransactionUtil() {

    }

    /**
     * Valid input data with {@link SchemaNode}.
     *
     * @param schemaNode
     *             {@link SchemaNode}
     * @param payload
     *             input data
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
     * @param path
     *             path of node
     * @param payload
     *             data
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
     * @throws RestconfDocumentedException
     *             if key values or key count in payload and URI isn't equal
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
     * Check mount point and prepare variables for put data to DS.
     *
     * @param payload
     *             data to put
     * @param schemaCtxRef
     *             reference to {@link SchemaContext}
     * @param transactionNode
     *             wrapper of variables for transaction
     * @param point
     *             query parameter
     * @param insert
     *             query parameter
     * @return {@link Response}
     */
    public static Response putData(final NormalizedNodeContext payload, final SchemaContextRef schemaCtxRef,
                               final TransactionVarsWrapper transactionNode, final String insert, final String point) {
        final YangInstanceIdentifier path = payload.getInstanceIdentifierContext().getInstanceIdentifier();
        final SchemaContext schemaContext = schemaCtxRef.get();

        final DOMDataTreeReadWriteTransaction readWriteTransaction =
                transactionNode.getTransactionChain().newReadWriteTransaction();

        final FluentFuture<Boolean> existsFuture = readWriteTransaction.exists(LogicalDatastoreType.CONFIGURATION,
            path);
        final FutureDataFactory<Boolean> existsResponse = new FutureDataFactory<>();
        FutureCallbackTx.addCallback(existsFuture, RestconfDataServiceConstant.PutData.PUT_TX_TYPE, existsResponse);

        final ResponseFactory responseFactory =
                new ResponseFactory(existsResponse.result ? Status.NO_CONTENT : Status.CREATED);
        final FluentFuture<? extends CommitInfo> submitData = submitData(path, schemaContext,
                transactionNode.getTransactionChainHandler(), readWriteTransaction, payload.getData(), insert, point);
        FutureCallbackTx.addCallback(submitData, RestconfDataServiceConstant.PutData.PUT_TX_TYPE, responseFactory);
        return responseFactory.build();
    }

    /**
     * Put data to DS.
     *
     * @param path
     *             path of data
     * @param schemaContext
     *             {@link SchemaContext}
     * @param transactionChainHandler
     *             write transaction
     * @param data
     *             data
     * @param point
     *             query parameter
     * @param insert
     *             query parameter
     * @return {@link FluentFuture}
     */
    private static FluentFuture<? extends CommitInfo> submitData(final YangInstanceIdentifier path,
            final SchemaContext schemaContext, final TransactionChainHandler transactionChainHandler,
            final DOMDataTreeReadWriteTransaction readWriteTransaction,
            final NormalizedNode<?, ?> data, final String insert, final String point) {
        if (insert == null) {
            return makePut(path, schemaContext, readWriteTransaction, data);
        }

        final DataSchemaNode schemaNode = checkListAndOrderedType(schemaContext, path);
        switch (insert) {
            case "first":
                if (schemaNode instanceof ListSchemaNode) {
                    final NormalizedNode<?, ?> readData =
                            readList(path, schemaContext, transactionChainHandler, schemaNode);
                    final OrderedMapNode readList = (OrderedMapNode) readData;
                    if (readList == null || readList.getValue().isEmpty()) {
                        return makePut(path, schemaContext, readWriteTransaction, data);
                    } else {
                        readWriteTransaction.delete(LogicalDatastoreType.CONFIGURATION, path.getParent());
                        simplePut(LogicalDatastoreType.CONFIGURATION, path, readWriteTransaction,
                            schemaContext, data);
                        listPut(LogicalDatastoreType.CONFIGURATION, path.getParent(), readWriteTransaction,
                            schemaContext, readList);
                        return readWriteTransaction.commit();
                    }
                } else {
                    final NormalizedNode<?, ?> readData =
                            readList(path, schemaContext, transactionChainHandler, schemaNode);

                    final OrderedLeafSetNode<?> readLeafList = (OrderedLeafSetNode<?>) readData;
                    if (readLeafList == null || readLeafList.getValue().isEmpty()) {
                        return makePut(path, schemaContext, readWriteTransaction, data);
                    } else {
                        readWriteTransaction.delete(LogicalDatastoreType.CONFIGURATION, path.getParent());
                        simplePut(LogicalDatastoreType.CONFIGURATION, path, readWriteTransaction,
                            schemaContext, data);
                        listPut(LogicalDatastoreType.CONFIGURATION, path.getParent(), readWriteTransaction,
                            schemaContext, readLeafList);
                        return readWriteTransaction.commit();
                    }
                }
            case "last":
                return makePut(path, schemaContext, readWriteTransaction, data);
            case "before":
                if (schemaNode instanceof ListSchemaNode) {
                    final NormalizedNode<?, ?> readData =
                            readList(path, schemaContext, transactionChainHandler, schemaNode);
                    final OrderedMapNode readList = (OrderedMapNode) readData;
                    if (readList == null || readList.getValue().isEmpty()) {
                        return makePut(path, schemaContext, readWriteTransaction, data);
                    } else {
                        insertWithPointListPut(readWriteTransaction, LogicalDatastoreType.CONFIGURATION, path,
                            data, schemaContext, point, readList, true);
                        return readWriteTransaction.commit();
                    }
                } else {
                    final NormalizedNode<?, ?> readData =
                            readList(path, schemaContext, transactionChainHandler, schemaNode);

                    final OrderedLeafSetNode<?> readLeafList = (OrderedLeafSetNode<?>) readData;
                    if (readLeafList == null || readLeafList.getValue().isEmpty()) {
                        return makePut(path, schemaContext, readWriteTransaction, data);
                    } else {
                        insertWithPointLeafListPut(readWriteTransaction, LogicalDatastoreType.CONFIGURATION,
                            path, data, schemaContext, point, readLeafList, true);
                        return readWriteTransaction.commit();
                    }
                }
            case "after":
                if (schemaNode instanceof ListSchemaNode) {
                    final NormalizedNode<?, ?> readData =
                            readList(path, schemaContext, transactionChainHandler, schemaNode);
                    final OrderedMapNode readList = (OrderedMapNode) readData;
                    if (readList == null || readList.getValue().isEmpty()) {
                        return makePut(path, schemaContext, readWriteTransaction, data);
                    } else {
                        insertWithPointListPut(readWriteTransaction, LogicalDatastoreType.CONFIGURATION,
                            path, data, schemaContext, point, readList, false);
                        return readWriteTransaction.commit();
                    }
                } else {
                    final NormalizedNode<?, ?> readData =
                            readList(path, schemaContext, transactionChainHandler, schemaNode);

                    final OrderedLeafSetNode<?> readLeafList = (OrderedLeafSetNode<?>) readData;
                    if (readLeafList == null || readLeafList.getValue().isEmpty()) {
                        return makePut(path, schemaContext, readWriteTransaction, data);
                    } else {
                        insertWithPointLeafListPut(readWriteTransaction, LogicalDatastoreType.CONFIGURATION,
                            path, data, schemaContext, point, readLeafList, true);
                        return readWriteTransaction.commit();
                    }
                }
            default:
                throw new RestconfDocumentedException(
                        "Used bad value of insert parameter. Possible values are first, last, before or after, "
                                + "but was: " + insert, RestconfError.ErrorType.PROTOCOL, ErrorTag.BAD_ATTRIBUTE);
        }
    }

    public static NormalizedNode<?, ?> readList(final YangInstanceIdentifier path, final SchemaContext schemaContext,
            final TransactionChainHandler transactionChainHandler, final DataSchemaNode schemaNode) {
        final InstanceIdentifierContext<?> iid = new InstanceIdentifierContext<SchemaNode>(
                path.getParent(), schemaNode, null, schemaContext);
        final TransactionVarsWrapper transactionNode = new TransactionVarsWrapper(iid, null, transactionChainHandler);
        final NormalizedNode<?, ?> readData = ReadDataTransactionUtil
                .readData(RestconfDataServiceConstant.ReadData.CONFIG, transactionNode, schemaContext);
        return readData;
    }

    private static void insertWithPointLeafListPut(final DOMDataTreeReadWriteTransaction rwTransaction,
            final LogicalDatastoreType datastore, final YangInstanceIdentifier path,
            final NormalizedNode<?, ?> data, final SchemaContext schemaContext, final String point,
            final OrderedLeafSetNode<?> readLeafList, final boolean before) {
        rwTransaction.delete(datastore, path.getParent());
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
        final NormalizedNode<?, ?> emptySubtree = ImmutableNodes.fromInstanceId(schemaContext, path.getParent());
        rwTransaction.merge(datastore, YangInstanceIdentifier.create(emptySubtree.getIdentifier()), emptySubtree);
        for (final LeafSetEntryNode<?> nodeChild : readLeafList.getValue()) {
            if (lastInsertedPosition == lastItemPosition) {
                simplePut(datastore, path, rwTransaction, schemaContext, data);
            }
            final YangInstanceIdentifier childPath = path.getParent().node(nodeChild.getIdentifier());
            rwTransaction.put(datastore, childPath, nodeChild);
            lastInsertedPosition++;
        }
    }

    private static void insertWithPointListPut(final DOMDataTreeReadWriteTransaction writeTx,
            final LogicalDatastoreType datastore, final YangInstanceIdentifier path,
            final NormalizedNode<?, ?> data, final SchemaContext schemaContext, final String point,
            final OrderedMapNode readList, final boolean before) {
        writeTx.delete(datastore, path.getParent());
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
        final NormalizedNode<?, ?> emptySubtree = ImmutableNodes.fromInstanceId(schemaContext, path.getParent());
        writeTx.merge(datastore, YangInstanceIdentifier.create(emptySubtree.getIdentifier()), emptySubtree);
        for (final MapEntryNode mapEntryNode : readList.getValue()) {
            if (lastInsertedPosition == lastItemPosition) {
                simplePut(datastore, path, writeTx, schemaContext, data);
            }
            final YangInstanceIdentifier childPath = path.getParent().node(mapEntryNode.getIdentifier());
            writeTx.put(datastore, childPath, mapEntryNode);
            lastInsertedPosition++;
        }
    }

    private static void listPut(final LogicalDatastoreType datastore, final YangInstanceIdentifier path,
            final DOMDataTreeWriteTransaction writeTx, final SchemaContext schemaContext,
            final OrderedLeafSetNode<?> payload) {
        final NormalizedNode<?, ?> emptySubtree = ImmutableNodes.fromInstanceId(schemaContext, path);
        writeTx.merge(datastore, YangInstanceIdentifier.create(emptySubtree.getIdentifier()), emptySubtree);
        TransactionUtil.ensureParentsByMerge(path, schemaContext, writeTx);
        for (final LeafSetEntryNode<?> child : ((LeafSetNode<?>) payload).getValue()) {
            final YangInstanceIdentifier childPath = path.node(child.getIdentifier());
            writeTx.put(datastore, childPath, child);
        }
    }

    private static void listPut(final LogicalDatastoreType datastore, final YangInstanceIdentifier path,
            final DOMDataTreeWriteTransaction writeTx, final SchemaContext schemaContext,
            final OrderedMapNode payload) {
        final NormalizedNode<?, ?> emptySubtree = ImmutableNodes.fromInstanceId(schemaContext, path);
        writeTx.merge(datastore, YangInstanceIdentifier.create(emptySubtree.getIdentifier()), emptySubtree);
        TransactionUtil.ensureParentsByMerge(path, schemaContext, writeTx);
        for (final MapEntryNode child : payload.getValue()) {
            final YangInstanceIdentifier childPath = path.node(child.getIdentifier());
            writeTx.put(datastore, childPath, child);
        }
    }

    private static void simplePut(final LogicalDatastoreType configuration, final YangInstanceIdentifier path,
            final DOMDataTreeWriteTransaction writeTx, final SchemaContext schemaContext,
            final NormalizedNode<?, ?> data) {
        TransactionUtil.ensureParentsByMerge(path, schemaContext, writeTx);
        writeTx.put(LogicalDatastoreType.CONFIGURATION, path, data);
    }

    private static FluentFuture<? extends CommitInfo> makePut(final YangInstanceIdentifier path,
            final SchemaContext schemaContext, final DOMDataTreeWriteTransaction writeTx,
            final NormalizedNode<?, ?> data) {
        TransactionUtil.ensureParentsByMerge(path, schemaContext, writeTx);
        writeTx.put(LogicalDatastoreType.CONFIGURATION, path, data);
        return writeTx.commit();
    }

    public static DataSchemaNode checkListAndOrderedType(final SchemaContext ctx, final YangInstanceIdentifier path) {
        final YangInstanceIdentifier parent = path.getParent();
        final DataSchemaContextNode<?> node = DataSchemaContextTree.from(ctx).getChild(parent);
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
