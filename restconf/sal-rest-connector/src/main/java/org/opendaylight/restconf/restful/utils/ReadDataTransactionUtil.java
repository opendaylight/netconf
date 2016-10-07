/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.restful.utils;

import com.google.common.base.Optional;
import com.google.common.primitives.Ints;
import com.google.common.util.concurrent.CheckedFuture;
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
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError;
import org.opendaylight.netconf.sal.restconf.impl.WriterParameters;
import org.opendaylight.netconf.sal.restconf.impl.WriterParameters.WriterParametersBuilder;
import org.opendaylight.restconf.restful.transaction.TransactionVarsWrapper;
import org.opendaylight.restconf.utils.parser.ParserFieldsParameter;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.AugmentationIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.AugmentationNode;
import org.opendaylight.yangtools.yang.data.api.schema.ChoiceNode;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.CollectionNodeBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.DataContainerNodeAttrBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.DataContainerNodeBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.NormalizedNodeContainerBuilder;
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
     * @param uriInfo
     *            - URI info
     * @return {@link WriterParameters}
     */
    @Nonnull public static WriterParameters parseUriParameters(@Nullable final UriInfo uriInfo,
                                                               @Nonnull SchemaContext context) {
        final WriterParametersBuilder builder = new WriterParametersBuilder();

        if (uriInfo == null) {
            return builder.build();
        }

        // check only allowed parameters
        ParametersUtil.checkParametersTypes(
                RestconfDataServiceConstant.ReadData.READ_TYPE_TX,
                uriInfo.getQueryParameters().keySet(),
                RestconfDataServiceConstant.ReadData.CONTENT,
                RestconfDataServiceConstant.ReadData.DEPTH);

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

            if (value == null
                    || (!(value >= RestconfDataServiceConstant.ReadData.MIN_DEPTH
                        && value <= RestconfDataServiceConstant.ReadData.MAX_DEPTH))) {
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
            builder.setFields(ParserFieldsParameter.parseFieldsParameter(fields.get(0), "toaster", context));
        }

        return builder.build();
    }

    /**
     * Read specific type of data from data store via transaction.
     *
     * @param valueOfContent
     *            - type of data to read (config, state, all)
     * @param transactionNode
     *            - {@link TransactionVarsWrapper} - wrapper for variables
     * @return {@link NormalizedNode}
     */
    public static @Nullable NormalizedNode<?, ?> readData(@Nonnull final String valueOfContent,
                                                          @Nonnull final TransactionVarsWrapper transactionNode) {
        switch (valueOfContent) {
            case RestconfDataServiceConstant.ReadData.CONFIG:
                transactionNode.setLogicalDatastoreType(LogicalDatastoreType.CONFIGURATION);
                return readDataViaTransaction(transactionNode);

            case RestconfDataServiceConstant.ReadData.NONCONFIG:
                transactionNode.setLogicalDatastoreType(LogicalDatastoreType.OPERATIONAL);
                return readDataViaTransaction(transactionNode);

            case RestconfDataServiceConstant.ReadData.ALL:
                return readAllData(transactionNode);

            default:
                throw new RestconfDocumentedException(
                        new RestconfError(RestconfError.ErrorType.PROTOCOL, RestconfError.ErrorTag.INVALID_VALUE,
                                "Invalid content parameter: " + valueOfContent, null,
                                "The content parameter value must be either config, nonconfig or all (default)"));
        }
    }

    /**
     * If is set specific {@link LogicalDatastoreType} in
     * {@link TransactionVarsWrapper}, then read this type of data from DS. If
     * don't, we have to read all data from DS (state + config)
     *
     * @param transactionNode
     *            - {@link TransactionVarsWrapper} - wrapper for variables
     * @return {@link NormalizedNode}
     */
    private static @Nullable NormalizedNode<?, ?> readDataViaTransaction(
            @Nonnull final TransactionVarsWrapper transactionNode) {
        final CheckedFuture<Optional<NormalizedNode<?, ?>>, ReadFailedException> listenableFuture = transactionNode
                .getTransactionChain().newReadOnlyTransaction().read(transactionNode.getLogicalDatastoreType(),
                        transactionNode.getInstanceIdentifier().getInstanceIdentifier());
        final NormalizedNodeFactory dataFactory = new NormalizedNodeFactory();
        FutureCallbackTx.addCallback(listenableFuture, RestconfDataServiceConstant.ReadData.READ_TYPE_TX,
                dataFactory);
        return dataFactory.build();
    }

    /**
     * Read config and state data, then map them.
     *
     * @param transactionNode
     *            - {@link TransactionVarsWrapper} - wrapper for variables
     * @return {@link NormalizedNode}
     */
    private static @Nullable NormalizedNode<?, ?> readAllData(@Nonnull final TransactionVarsWrapper transactionNode) {
        // PREPARE STATE DATA NODE
        transactionNode.setLogicalDatastoreType(LogicalDatastoreType.OPERATIONAL);
        final NormalizedNode<?, ?> stateDataNode = readDataViaTransaction(transactionNode);

        // PREPARE CONFIG DATA NODE
        transactionNode.setLogicalDatastoreType(LogicalDatastoreType.CONFIGURATION);
        final NormalizedNode<?, ?> configDataNode = readDataViaTransaction(transactionNode);

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
     *            - data node of state data
     * @param configDataNode
     *            - data node of config data
     * @return {@link NormalizedNode}
     */
    private static @Nonnull NormalizedNode<?, ?> mapNode(@Nonnull final NormalizedNode<?, ?> stateDataNode,
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
     *            - data node of state data
     * @param configDataNode
     *            - data node of config data
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
     * Prepare and map data for rpc
     *
     * @param configDataNode
     *            - data node of config data
     * @param stateDataNode
     *            - data node of state data
     * @return {@link NormalizedNode}
     */
    private static @Nonnull NormalizedNode<?, ?> prepareRpcData(@Nonnull final NormalizedNode<?, ?> configDataNode,
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
     *            - data node
     * @param mapEntryBuilder
     *            - builder for mapping data
     */
    private static void mapRpcDataNode(@Nonnull final NormalizedNode<?, ?> dataNode,
                                       @Nonnull final DataContainerNodeBuilder<
                                               NodeIdentifierWithPredicates, MapEntryNode> mapEntryBuilder) {
        ((ContainerNode) dataNode).getValue().forEach(mapEntryBuilder::addChild);
    }

    /**
     * Prepare and map all data from DS
     *
     * @param configDataNode
     *            - data node of config data
     * @param stateDataNode
     *            - data node of state data
     * @return {@link NormalizedNode}
     */
    private static @Nonnull NormalizedNode<?, ?> prepareData(@Nonnull final NormalizedNode<?, ?> configDataNode,
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

            mapValueToBuilder(
                    ((AugmentationNode) configDataNode).getValue(), ((AugmentationNode) stateDataNode).getValue(), builder);

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
     *            - collection of config data nodes
     * @param stateData
     *            - collection of state data nodes
     * @param builder
     *            - builder
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
     *            - map of config data nodes
     * @param stateMap
     *            - map of state data nodes
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
     *            - immutable config data
     * @param stateMap
     *            - immutable state data
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
