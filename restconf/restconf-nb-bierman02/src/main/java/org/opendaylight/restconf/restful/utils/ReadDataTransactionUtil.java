/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.restful.utils;

import static org.opendaylight.restconf.restful.utils.RestconfStreamsConstants.STREAMS_PATH;
import static org.opendaylight.restconf.restful.utils.RestconfStreamsConstants.STREAM_PATH_PART;

import com.google.common.base.Optional;
import com.google.common.primitives.Ints;
import com.google.common.util.concurrent.CheckedFuture;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.ws.rs.core.UriInfo;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadOnlyTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadWriteTransaction;
import org.opendaylight.netconf.sal.restconf.impl.ControllerContext;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError;
import org.opendaylight.netconf.sal.streams.listeners.NotificationListenerAdapter;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.context.WriterParameters;
import org.opendaylight.restconf.common.context.WriterParameters.WriterParametersBuilder;
import org.opendaylight.restconf.common.references.SchemaContextRef;
import org.opendaylight.restconf.restful.transaction.TransactionVarsWrapper;
import org.opendaylight.restconf.utils.mapping.RestconfMappingNodeUtil;
import org.opendaylight.restconf.utils.parser.ParserFieldsParameter;
import org.opendaylight.yang.gen.v1.urn.sal.restconf.event.subscription.rev140708.NotificationOutputTypeGrouping.NotificationOutputType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.AugmentationIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.AugmentationNode;
import org.opendaylight.yangtools.yang.data.api.schema.ChoiceNode;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.CollectionNodeBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.DataContainerNodeAttrBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.DataContainerNodeBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.NormalizedNodeAttrBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.NormalizedNodeContainerBuilder;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextTree;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.NotificationDefinition;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

/**
 * Util class for read data from data store via transaction.
 * <ul>
 * <li>config
 * <li>state
 * <li>all (config + state)
 * </ul>
 *
 */
public final class ReadDataTransactionUtil {

    private ReadDataTransactionUtil() {
        throw new UnsupportedOperationException("Util class.");
    }

    /**
     * Parse parameters from URI request and check their types and values.
     *
     *
     * @param identifier
     *             {@link InstanceIdentifierContext}
     * @param uriInfo
     *             URI info
     * @param tagged
     *             set tagged for {@link WriterParameters}
     * @return {@link WriterParameters}
     */
    @Nonnull
    public static WriterParameters parseUriParameters(@Nonnull final InstanceIdentifierContext<?> identifier,
            @Nullable final UriInfo uriInfo, final boolean tagged) {
        return parseParams(identifier, uriInfo, tagged);
    }

    /**
     * Parse parameters from URI request and check their types and values.
     *
     *
     * @param identifier
     *             {@link InstanceIdentifierContext}
     * @param uriInfo
     *             URI info
     * @return {@link WriterParameters}
     */
    @Nonnull
    public static WriterParameters parseUriParameters(@Nonnull final InstanceIdentifierContext<?> identifier,
                                                               @Nullable final UriInfo uriInfo) {
        return parseParams(identifier, uriInfo, false);
    }

    private static WriterParameters parseParams(final InstanceIdentifierContext<?> identifier, final UriInfo uriInfo,
            final boolean tagged) {
        final WriterParametersBuilder builder = new WriterParametersBuilder();
        builder.setTagged(tagged);

        if (uriInfo == null) {
            return builder.build();
        }

        // check only allowed parameters
        ParametersUtil.checkParametersTypes(
                RestconfDataServiceConstant.ReadData.READ_TYPE_TX,
                uriInfo.getQueryParameters().keySet(),
                RestconfDataServiceConstant.ReadData.CONTENT,
                RestconfDataServiceConstant.ReadData.DEPTH,
                RestconfDataServiceConstant.ReadData.FIELDS, RestconfDataServiceConstant.ReadData.WITH_DEFAULTS);

        // read parameters from URI or set default values
        final List<String> content = uriInfo.getQueryParameters().getOrDefault(
                RestconfDataServiceConstant.ReadData.CONTENT,
                Collections.singletonList(RestconfDataServiceConstant.ReadData.ALL));
        final List<String> depth = uriInfo.getQueryParameters().getOrDefault(
                RestconfDataServiceConstant.ReadData.DEPTH,
                Collections.singletonList(RestconfDataServiceConstant.ReadData.UNBOUNDED));
        // fields
        final List<String> fields = uriInfo.getQueryParameters().getOrDefault(
                RestconfDataServiceConstant.ReadData.FIELDS,
                Collections.emptyList());

        // parameter can be in URI at most once
        ParametersUtil.checkParameterCount(content, RestconfDataServiceConstant.ReadData.CONTENT);
        ParametersUtil.checkParameterCount(depth, RestconfDataServiceConstant.ReadData.DEPTH);
        ParametersUtil.checkParameterCount(fields, RestconfDataServiceConstant.ReadData.FIELDS);

        // check and set content
        final String contentValue = content.get(0);
        if (!contentValue.equals(RestconfDataServiceConstant.ReadData.ALL)) {
            if (!contentValue.equals(RestconfDataServiceConstant.ReadData.CONFIG)
                    && !contentValue.equals(RestconfDataServiceConstant.ReadData.NONCONFIG)) {
                throw new RestconfDocumentedException(
                        new RestconfError(RestconfError.ErrorType.PROTOCOL, RestconfError.ErrorTag.INVALID_VALUE,
                                "Invalid content parameter: " + contentValue, null,
                                "The content parameter value must be either config, nonconfig or all (default)"));
            }
        }

        builder.setContent(content.get(0));

        // check and set depth
        if (!depth.get(0).equals(RestconfDataServiceConstant.ReadData.UNBOUNDED)) {
            final Integer value = Ints.tryParse(depth.get(0));

            if ((value == null)
                    || (!((value >= RestconfDataServiceConstant.ReadData.MIN_DEPTH)
                        && (value <= RestconfDataServiceConstant.ReadData.MAX_DEPTH)))) {
                throw new RestconfDocumentedException(
                        new RestconfError(RestconfError.ErrorType.PROTOCOL, RestconfError.ErrorTag.INVALID_VALUE,
                                "Invalid depth parameter: " + depth, null,
                                "The depth parameter must be an integer between 1 and 65535 or \"unbounded\""));
            } else {
                builder.setDepth(value);
            }
        }

        // check and set fields
        if (!fields.isEmpty()) {
            builder.setFields(ParserFieldsParameter.parseFieldsParameter(identifier, fields.get(0)));
        }

        return builder.build();
    }

    /**
     * Read specific type of data from data store via transaction.
     *
     * @param valueOfContent
     *             type of data to read (config, state, all)
     * @param transactionNode
     *             {@link TransactionVarsWrapper} - wrapper for variables
     * @return {@link NormalizedNode}
     */
    @Nullable
    public static NormalizedNode<?, ?> readData(@Nonnull final String valueOfContent,
            @Nonnull final TransactionVarsWrapper transactionNode) {
        return readData(valueOfContent, transactionNode, null);
    }

    /**
     * Read specific type of data from data store via transaction.
     *
     * @param valueOfContent
     *             type of data to read (config, state, all)
     * @param transactionNode
     *             {@link TransactionVarsWrapper} - wrapper for variables
     * @param withDefa
     *             vaule of with-defaults parameter
     * @return {@link NormalizedNode}
     */
    @Nullable
    public static NormalizedNode<?, ?> readData(@Nonnull final String valueOfContent,
            @Nonnull final TransactionVarsWrapper transactionNode, final String withDefa) {
        switch (valueOfContent) {
            case RestconfDataServiceConstant.ReadData.CONFIG:
                transactionNode.setLogicalDatastoreType(LogicalDatastoreType.CONFIGURATION);
                if (withDefa == null) {
                    return readDataViaTransaction(transactionNode);
                } else {
                    return prepareDataByParamWithDef(readDataViaTransaction(transactionNode),
                            transactionNode.getInstanceIdentifier().getInstanceIdentifier(), withDefa);
                }
            case RestconfDataServiceConstant.ReadData.NONCONFIG:
                transactionNode.setLogicalDatastoreType(LogicalDatastoreType.OPERATIONAL);
                return readDataViaTransaction(transactionNode);

            case RestconfDataServiceConstant.ReadData.ALL:
                return readAllData(transactionNode, withDefa);

            default:
                throw new RestconfDocumentedException(
                        new RestconfError(RestconfError.ErrorType.PROTOCOL, RestconfError.ErrorTag.INVALID_VALUE,
                                "Invalid content parameter: " + valueOfContent, null,
                                "The content parameter value must be either config, nonconfig or all (default)"));
        }
    }

    /**
     * Read specific type of data from data store via transaction and if identifier read data from
     * streams then put streams from actual schema context to datastore.
     *
     * @param identifier
     *             identifier of data to read
     * @param content
     *             type of data to read (config, state, all)
     * @param transactionNode
     *             {@link TransactionVarsWrapper} - wrapper for variables
     * @param withDefa
     *             vaule of with-defaults parameter
     * @param schemaContextRef
     *             schema context
     * @param uriInfo
     *             uri info
     * @return {@link NormalizedNode}
     */
    public static NormalizedNode<?, ?> readData(final String identifier, final String content,
                                                final TransactionVarsWrapper transactionNode, final String withDefa,
                                                final SchemaContextRef schemaContextRef, final UriInfo uriInfo) {
        if (identifier.contains(STREAMS_PATH) && !identifier.contains(STREAM_PATH_PART)) {
            final DOMDataReadWriteTransaction wTx = transactionNode.getTransactionChain().newReadWriteTransaction();
            final SchemaContext schemaContext = schemaContextRef.get();
            final boolean exist = SubscribeToStreamUtil.checkExist(schemaContext, wTx);

            for (final NotificationDefinition notificationDefinition : schemaContextRef.get().getNotifications()) {
                final List<NotificationListenerAdapter> notifiStreamXML =
                        CreateStreamUtil.createYangNotifiStream(notificationDefinition, schemaContextRef,
                                NotificationOutputType.XML.getName());
                final List<NotificationListenerAdapter> notifiStreamJSON =
                        CreateStreamUtil.createYangNotifiStream(notificationDefinition, schemaContextRef,
                                NotificationOutputType.JSON.getName());
                notifiStreamJSON.addAll(notifiStreamXML);

                for (final NotificationListenerAdapter listener : notifiStreamJSON) {
                    final URI uri = SubscribeToStreamUtil.prepareUriByStreamName(uriInfo, listener.getStreamName());
                    final NormalizedNode mapToStreams =
                            RestconfMappingNodeUtil.mapYangNotificationStreamByIetfRestconfMonitoring(
                                    listener.getSchemaPath().getLastComponent(), schemaContext.getNotifications(),
                                    null, listener.getOutputType(), uri,
                                    SubscribeToStreamUtil.getMonitoringModule(schemaContext), exist);
                    SubscribeToStreamUtil.writeDataToDS(schemaContext,
                            listener.getSchemaPath().getLastComponent().getLocalName(), wTx, exist,
                            mapToStreams);
                }
            }
            SubscribeToStreamUtil.submitData(wTx);
        }
        return readData(content, transactionNode, withDefa);
    }

    private static NormalizedNode<?, ?> prepareDataByParamWithDef(final NormalizedNode<?, ?> result,
            final YangInstanceIdentifier path, final String withDefa) {
        boolean trim;
        switch (withDefa) {
            case "trim":
                trim = true;
                break;
            case "explicit":
                trim = false;
                break;
            default:
                throw new RestconfDocumentedException("");
        }

        final SchemaContext ctx = ControllerContext.getInstance().getGlobalSchema();
        final DataSchemaContextTree baseSchemaCtxTree = DataSchemaContextTree.from(ctx);
        final DataSchemaNode baseSchemaNode = baseSchemaCtxTree.getChild(path).getDataSchemaNode();
        if (result instanceof ContainerNode) {
            final DataContainerNodeAttrBuilder<NodeIdentifier, ContainerNode> builder =
                    Builders.containerBuilder((ContainerSchemaNode) baseSchemaNode);
            buildCont(builder, (ContainerNode) result, baseSchemaCtxTree, path, trim);
            return builder.build();
        } else {
            final DataContainerNodeAttrBuilder<NodeIdentifierWithPredicates, MapEntryNode> builder =
                    Builders.mapEntryBuilder((ListSchemaNode) baseSchemaNode);
            buildMapEntryBuilder(builder, (MapEntryNode) result, baseSchemaCtxTree, path, trim,
                    ((ListSchemaNode) baseSchemaNode).getKeyDefinition());
            return builder.build();
        }
    }

    private static void buildMapEntryBuilder(
            final DataContainerNodeAttrBuilder<NodeIdentifierWithPredicates, MapEntryNode> builder,
            final MapEntryNode result, final DataSchemaContextTree baseSchemaCtxTree,
            final YangInstanceIdentifier actualPath, final boolean trim, final List<QName> keys) {
        for (final DataContainerChild<? extends PathArgument, ?> child : result.getValue()) {
            final YangInstanceIdentifier path = actualPath.node(child.getIdentifier());
            final DataSchemaNode childSchema = baseSchemaCtxTree.getChild(path).getDataSchemaNode();
            if (child instanceof ContainerNode) {
                final DataContainerNodeAttrBuilder<NodeIdentifier, ContainerNode> childBuilder =
                        Builders.containerBuilder((ContainerSchemaNode) childSchema);
                buildCont(childBuilder, (ContainerNode) child, baseSchemaCtxTree, path, trim);
                builder.withChild(childBuilder.build());
            } else if (child instanceof MapNode) {
                final CollectionNodeBuilder<MapEntryNode, MapNode> childBuilder =
                        Builders.mapBuilder((ListSchemaNode) childSchema);
                buildList(childBuilder, (MapNode) child, baseSchemaCtxTree, path, trim,
                        ((ListSchemaNode) childSchema).getKeyDefinition());
                builder.withChild(childBuilder.build());
            } else if (child instanceof LeafNode) {
                final String defaultVal = ((LeafSchemaNode) childSchema).getDefault();
                final String nodeVal = ((LeafNode<String>) child).getValue();
                final NormalizedNodeAttrBuilder<NodeIdentifier, Object, LeafNode<Object>> leafBuilder =
                        Builders.leafBuilder((LeafSchemaNode) childSchema);
                if (keys.contains(child.getNodeType())) {
                    leafBuilder.withValue(((LeafNode) child).getValue());
                    builder.withChild(leafBuilder.build());
                } else {
                    if (trim) {
                        if ((defaultVal == null) || !defaultVal.equals(nodeVal)) {
                            leafBuilder.withValue(((LeafNode) child).getValue());
                            builder.withChild(leafBuilder.build());
                        }
                    } else {
                        if ((defaultVal != null) && defaultVal.equals(nodeVal)) {
                            leafBuilder.withValue(((LeafNode) child).getValue());
                            builder.withChild(leafBuilder.build());
                        }
                    }
                }
            }
        }
    }

    private static void buildList(final CollectionNodeBuilder<MapEntryNode, MapNode> builder, final MapNode result,
            final DataSchemaContextTree baseSchemaCtxTree, final YangInstanceIdentifier path, final boolean trim,
            final List<QName> keys) {
        for (final MapEntryNode mapEntryNode : result.getValue()) {
            final YangInstanceIdentifier actualNode = path.node(mapEntryNode.getIdentifier());
            final DataSchemaNode childSchema = baseSchemaCtxTree.getChild(actualNode).getDataSchemaNode();
            final DataContainerNodeAttrBuilder<NodeIdentifierWithPredicates, MapEntryNode> mapEntryBuilder =
                    Builders.mapEntryBuilder((ListSchemaNode) childSchema);
            buildMapEntryBuilder(mapEntryBuilder, mapEntryNode, baseSchemaCtxTree, actualNode, trim, keys);
            builder.withChild(mapEntryBuilder.build());
        }
    }

    private static void buildCont(final DataContainerNodeAttrBuilder<NodeIdentifier, ContainerNode> builder,
            final ContainerNode result, final DataSchemaContextTree baseSchemaCtxTree,
            final YangInstanceIdentifier actualPath, final boolean trim) {
        for (final DataContainerChild<? extends PathArgument, ?> child : result.getValue()) {
            final YangInstanceIdentifier path = actualPath.node(child.getIdentifier());
            final DataSchemaNode childSchema = baseSchemaCtxTree.getChild(path).getDataSchemaNode();
            if (child instanceof ContainerNode) {
                final DataContainerNodeAttrBuilder<NodeIdentifier, ContainerNode> builderChild =
                        Builders.containerBuilder((ContainerSchemaNode) childSchema);
                buildCont(builderChild, result, baseSchemaCtxTree, actualPath, trim);
                builder.withChild(builderChild.build());
            } else if (child instanceof MapNode) {
                final CollectionNodeBuilder<MapEntryNode, MapNode> childBuilder =
                        Builders.mapBuilder((ListSchemaNode) childSchema);
                buildList(childBuilder, (MapNode) child, baseSchemaCtxTree, path, trim,
                        ((ListSchemaNode) childSchema).getKeyDefinition());
                builder.withChild(childBuilder.build());
            } else if (child instanceof LeafNode) {
                final String defaultVal = ((LeafSchemaNode) childSchema).getDefault();
                final String nodeVal = ((LeafNode<String>) child).getValue();
                final NormalizedNodeAttrBuilder<NodeIdentifier, Object, LeafNode<Object>> leafBuilder =
                        Builders.leafBuilder((LeafSchemaNode) childSchema);
                if (trim) {
                    if ((defaultVal == null) || !defaultVal.equals(nodeVal)) {
                        leafBuilder.withValue(((LeafNode) child).getValue());
                        builder.withChild(leafBuilder.build());
                    }
                } else {
                    if ((defaultVal != null) && defaultVal.equals(nodeVal)) {
                        leafBuilder.withValue(((LeafNode) child).getValue());
                        builder.withChild(leafBuilder.build());
                    }
                }
            }
        }
    }

    /**
     * If is set specific {@link LogicalDatastoreType} in
     * {@link TransactionVarsWrapper}, then read this type of data from DS. If
     * don't, we have to read all data from DS (state + config)
     *
     * @param transactionNode
     *             {@link TransactionVarsWrapper} - wrapper for variables
     * @return {@link NormalizedNode}
     */
    @Nullable
    private static NormalizedNode<?, ?> readDataViaTransaction(
            @Nonnull final TransactionVarsWrapper transactionNode) {
        final NormalizedNodeFactory dataFactory = new NormalizedNodeFactory();
        try (DOMDataReadOnlyTransaction tx = transactionNode.getTransactionChain().newReadOnlyTransaction()) {
            final CheckedFuture<Optional<NormalizedNode<?, ?>>, ReadFailedException> listenableFuture = tx.read(
                transactionNode.getLogicalDatastoreType(),
                transactionNode.getInstanceIdentifier().getInstanceIdentifier());
            FutureCallbackTx.addCallback(listenableFuture, RestconfDataServiceConstant.ReadData.READ_TYPE_TX,
                dataFactory);
        }
        return dataFactory.build();
    }

    /**
     * Read config and state data, then map them.
     *
     * @param transactionNode
     *             {@link TransactionVarsWrapper} - wrapper for variables
     * @param withDefa with-defaults parameter
     * @return {@link NormalizedNode}
     */
    @Nullable
    private static NormalizedNode<?, ?> readAllData(@Nonnull final TransactionVarsWrapper transactionNode,
            final String withDefa) {
        // PREPARE STATE DATA NODE
        transactionNode.setLogicalDatastoreType(LogicalDatastoreType.OPERATIONAL);
        final NormalizedNode<?, ?> stateDataNode = readDataViaTransaction(transactionNode);

        // PREPARE CONFIG DATA NODE
        transactionNode.setLogicalDatastoreType(LogicalDatastoreType.CONFIGURATION);
        final NormalizedNode<?, ?> configDataNode;
        if (withDefa == null) {
            configDataNode = readDataViaTransaction(transactionNode);
        } else {
            configDataNode = prepareDataByParamWithDef(readDataViaTransaction(transactionNode),
                    transactionNode.getInstanceIdentifier().getInstanceIdentifier(), withDefa);
        }

        // if no data exists
        if ((stateDataNode == null) && (configDataNode == null)) {
            return null;
        }

        // return config data
        if (stateDataNode == null) {
            return configDataNode;
        }

        // return state data
        if (configDataNode == null) {
            return stateDataNode;
        }

        // merge data from config and state
        return mapNode(stateDataNode, configDataNode);
    }

    /**
     * Map data by type of read node.
     *
     * @param stateDataNode
     *             data node of state data
     * @param configDataNode
     *             data node of config data
     * @return {@link NormalizedNode}
     */
    @Nonnull
    private static NormalizedNode<?, ?> mapNode(@Nonnull final NormalizedNode<?, ?> stateDataNode,
                                                         @Nonnull final NormalizedNode<?, ?> configDataNode) {
        validPossibilityOfMergeNodes(stateDataNode, configDataNode);
        if (configDataNode instanceof RpcDefinition) {
            return prepareRpcData(configDataNode, stateDataNode);
        } else {
            return prepareData(configDataNode, stateDataNode);
        }
    }

    /**
     * Valid of can be data merged together.
     *
     * @param stateDataNode
     *             data node of state data
     * @param configDataNode
     *             data node of config data
     */
    private static void validPossibilityOfMergeNodes(@Nonnull final NormalizedNode<?, ?> stateDataNode,
                                                     @Nonnull final NormalizedNode<?, ?> configDataNode) {
        final QNameModule moduleOfStateData = stateDataNode.getIdentifier().getNodeType().getModule();
        final QNameModule moduleOfConfigData = configDataNode.getIdentifier().getNodeType().getModule();
        if (moduleOfStateData != moduleOfConfigData) {
            throw new RestconfDocumentedException("It is not possible to merge ");
        }
    }

    /**
     * Prepare and map data for rpc.
     *
     * @param configDataNode
     *             data node of config data
     * @param stateDataNode
     *             data node of state data
     * @return {@link NormalizedNode}
     */
    @Nonnull
    private static NormalizedNode<?, ?> prepareRpcData(@Nonnull final NormalizedNode<?, ?> configDataNode,
                                                                @Nonnull final NormalizedNode<?, ?> stateDataNode) {
        final DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> mapEntryBuilder = ImmutableNodes
                .mapEntryBuilder();
        mapEntryBuilder.withNodeIdentifier((NodeIdentifierWithPredicates) configDataNode.getIdentifier());

        // MAP CONFIG DATA
        mapRpcDataNode(configDataNode, mapEntryBuilder);
        // MAP STATE DATA
        mapRpcDataNode(stateDataNode, mapEntryBuilder);

        return ImmutableNodes.mapNodeBuilder(configDataNode.getNodeType()).addChild(mapEntryBuilder.build()).build();
    }

    /**
     * Map node to map entry builder.
     *
     * @param dataNode
     *             data node
     * @param mapEntryBuilder
     *             builder for mapping data
     */
    private static void mapRpcDataNode(@Nonnull final NormalizedNode<?, ?> dataNode,
                                       @Nonnull final DataContainerNodeBuilder<
                                               NodeIdentifierWithPredicates, MapEntryNode> mapEntryBuilder) {
        ((ContainerNode) dataNode).getValue().forEach(mapEntryBuilder::addChild);
    }

    /**
     * Prepare and map all data from DS.
     *
     * @param configDataNode
     *             data node of config data
     * @param stateDataNode
     *             data node of state data
     * @return {@link NormalizedNode}
     */
    @Nonnull
    private static NormalizedNode<?, ?> prepareData(@Nonnull final NormalizedNode<?, ?> configDataNode,
                                                             @Nonnull final NormalizedNode<?, ?> stateDataNode) {
        if (configDataNode instanceof MapNode) {
            final CollectionNodeBuilder<MapEntryNode, MapNode> builder = ImmutableNodes
                    .mapNodeBuilder().withNodeIdentifier(((MapNode) configDataNode).getIdentifier());

            mapValueToBuilder(
                    ((MapNode) configDataNode).getValue(), ((MapNode) stateDataNode).getValue(), builder);

            return builder.build();
        } else if (configDataNode instanceof MapEntryNode) {
            final DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> builder = ImmutableNodes
                    .mapEntryBuilder().withNodeIdentifier(((MapEntryNode) configDataNode).getIdentifier());

            mapValueToBuilder(
                    ((MapEntryNode) configDataNode).getValue(), ((MapEntryNode) stateDataNode).getValue(), builder);

            return builder.build();
        } else if (configDataNode instanceof ContainerNode) {
            final DataContainerNodeAttrBuilder<NodeIdentifier, ContainerNode> builder = Builders
                    .containerBuilder().withNodeIdentifier(((ContainerNode) configDataNode).getIdentifier());

            mapValueToBuilder(
                    ((ContainerNode) configDataNode).getValue(), ((ContainerNode) stateDataNode).getValue(), builder);

            return builder.build();
        } else if (configDataNode instanceof AugmentationNode) {
            final DataContainerNodeBuilder<AugmentationIdentifier, AugmentationNode> builder = Builders
                    .augmentationBuilder().withNodeIdentifier(((AugmentationNode) configDataNode).getIdentifier());

            mapValueToBuilder(((AugmentationNode) configDataNode).getValue(),
                    ((AugmentationNode) stateDataNode).getValue(), builder);

            return builder.build();
        } else if (configDataNode instanceof ChoiceNode) {
            final DataContainerNodeBuilder<NodeIdentifier, ChoiceNode> builder = Builders
                    .choiceBuilder().withNodeIdentifier(((ChoiceNode) configDataNode).getIdentifier());

            mapValueToBuilder(
                    ((ChoiceNode) configDataNode).getValue(), ((ChoiceNode) stateDataNode).getValue(), builder);

            return builder.build();
        } else if (configDataNode instanceof LeafNode) {
            return ImmutableNodes.leafNode(configDataNode.getNodeType(), configDataNode.getValue());
        } else {
            throw new RestconfDocumentedException("Bad type of node.");
        }
    }

    /**
     * Map value from container node to builder.
     *
     * @param configData
     *             collection of config data nodes
     * @param stateData
     *             collection of state data nodes
     * @param builder
     *             builder
     */
    private static <T extends NormalizedNode<? extends PathArgument, ?>> void mapValueToBuilder(
            @Nonnull final Collection<T> configData,
            @Nonnull final Collection<T> stateData,
            @Nonnull final NormalizedNodeContainerBuilder<?, PathArgument, T, ?> builder) {
        final Map<PathArgument, T> configMap = configData.stream().collect(
                Collectors.toMap(NormalizedNode::getIdentifier, Function.identity()));
        final Map<PathArgument, T> stateMap = stateData.stream().collect(
                Collectors.toMap(NormalizedNode::getIdentifier, Function.identity()));

        // merge config and state data of children with different identifiers
        mapDataToBuilder(configMap, stateMap, builder);

        // merge config and state data of children with the same identifiers
        mergeDataToBuilder(configMap, stateMap, builder);
    }

    /**
     * Map data with different identifiers to builder. Data with different identifiers can be just added
     * as childs to parent node.
     *
     * @param configMap
     *             map of config data nodes
     * @param stateMap
     *             map of state data nodes
     * @param builder
     *           - builder
     */
    private static <T extends NormalizedNode<? extends PathArgument, ?>> void mapDataToBuilder(
            @Nonnull final Map<PathArgument, T> configMap,
            @Nonnull final Map<PathArgument, T> stateMap,
            @Nonnull final NormalizedNodeContainerBuilder<?, PathArgument, T, ?> builder) {
        configMap.entrySet().stream().filter(x -> !stateMap.containsKey(x.getKey())).forEach(
            y -> builder.addChild(y.getValue()));
        stateMap.entrySet().stream().filter(x -> !configMap.containsKey(x.getKey())).forEach(
            y -> builder.addChild(y.getValue()));
    }

    /**
     * Map data with the same identifiers to builder. Data with the same identifiers cannot be just added but we need to
     * go one level down with {@code prepareData} method.
     *
     * @param configMap
     *             immutable config data
     * @param stateMap
     *             immutable state data
     * @param builder
     *           - builder
     */
    @SuppressWarnings("unchecked")
    private static <T extends NormalizedNode<? extends PathArgument, ?>> void mergeDataToBuilder(
            @Nonnull final Map<PathArgument, T> configMap,
            @Nonnull final Map<PathArgument, T> stateMap,
            @Nonnull final NormalizedNodeContainerBuilder<?, PathArgument, T, ?> builder) {
        // it is enough to process only config data because operational contains the same data
        configMap.entrySet().stream().filter(x -> stateMap.containsKey(x.getKey())).forEach(
            y -> builder.addChild((T) prepareData(y.getValue(), stateMap.get(y.getKey()))));
    }
}
