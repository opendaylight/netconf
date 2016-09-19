/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.restful.utils;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nonnull;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorTag;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorType;
import org.opendaylight.restconf.restful.transaction.TransactionVarsWrapper;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.AugmentationIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.AugmentationNode;
import org.opendaylight.yangtools.yang.data.api.schema.ChoiceNode;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerNode;
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

    private static Class<? extends DataContainerNode> type;
    private static DataContainerChild<? extends PathArgument, ?> child;
    private static DataContainerNodeBuilder<? extends PathArgument, ? extends DataContainerNode<?>> mapBuilder;

    private ReadDataTransactionUtil() {
        throw new UnsupportedOperationException("Util class.");
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
    public static NormalizedNode<?, ?> readData(final String valueOfContent, final TransactionVarsWrapper transactionNode) {
        final NormalizedNode<?, ?> data;
        if (valueOfContent != null) {
            switch (valueOfContent) {
                case RestconfDataServiceConstant.ReadData.CONFIG:
                    transactionNode.setLogicalDatastoreType(LogicalDatastoreType.CONFIGURATION);
                    data = readDataViaTransaction(transactionNode);
                    break;
                case RestconfDataServiceConstant.ReadData.NONCONFIG:
                    transactionNode.setLogicalDatastoreType(LogicalDatastoreType.OPERATIONAL);
                    data = readDataViaTransaction(transactionNode);
                    break;
                case RestconfDataServiceConstant.ReadData.ALL:
                    data = readAllData(transactionNode);
                    break;
                default:
                    throw new RestconfDocumentedException("Bad query parameter for content.", ErrorType.APPLICATION,
                            ErrorTag.INVALID_VALUE);
            }
        } else {
            data = readAllData(transactionNode);
        }

        return data;
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
    private static NormalizedNode<?, ?> readDataViaTransaction(final TransactionVarsWrapper transactionNode) {
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
    private static NormalizedNode<?, ?> readAllData(final TransactionVarsWrapper transactionNode) {
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
    private static NormalizedNode<?, ?> mapNode(final NormalizedNode<?, ?> stateDataNode,
            final NormalizedNode<?, ?> configDataNode) {
        validPossibilityOfMergeNodes(stateDataNode, configDataNode);
        if (configDataNode instanceof RpcDefinition) {
            return prepareRpcData(configDataNode, stateDataNode);
        } else {
            return prepareData(configDataNode, stateDataNode);
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
    private static NormalizedNode<?, ?> prepareRpcData(final NormalizedNode<?, ?> configDataNode,
            final NormalizedNode<?, ?> stateDataNode) {
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
    private static void mapRpcDataNode(final NormalizedNode<?, ?> dataNode,
            final DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> mapEntryBuilder) {
        for (final DataContainerChild<? extends PathArgument, ?> child : ((ContainerNode) dataNode).getValue()) {
            mapEntryBuilder.addChild(child);
        }
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
    private static NormalizedNode<?, ?> prepareData(final NormalizedNode<?, ?> configDataNode,
                                                    final NormalizedNode<?, ?> stateDataNode) {
        if (configDataNode instanceof MapNode) { // part for lists mapping
            final CollectionNodeBuilder<MapEntryNode, MapNode> mapNodeBuilder = ImmutableNodes
                    .mapNodeBuilder().withNodeIdentifier(((MapNode) configDataNode).getIdentifier());

            final Collection<? extends NormalizedNode<?, ?>> configData = ((MapNode) configDataNode).getValue();
            final Collection<? extends NormalizedNode<?, ?>> stateData = ((MapNode) stateDataNode).getValue();

            final Map<PathArgument, NormalizedNode<?, ?>> configMap = createMap(configData);
            final Map<PathArgument, NormalizedNode<?, ?>> stateMap = createMap(stateData);


            // MERGE CONFIG AND STATE DATA
            mapDataToBuilder(configMap, stateMap, mapNodeBuilder);
            mergeDataToBuilder(configMap, stateMap, mapNodeBuilder);

            return mapNodeBuilder.build();
        } else if (configDataNode instanceof MapEntryNode) { // part for list entries
            final DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> mapEntryBuilder = ImmutableNodes
                    .mapEntryBuilder().withNodeIdentifier(((MapEntryNode) configDataNode).getIdentifier());

            final Collection<? extends NormalizedNode<?, ?>> configData = ((MapEntryNode) configDataNode).getValue();
            final Collection<? extends NormalizedNode<?, ?>> stateData = ((MapEntryNode) stateDataNode).getValue();

            final Map<PathArgument, NormalizedNode<?, ?>> configMap = createMap(configData);
            final Map<PathArgument, NormalizedNode<?, ?>> stateMap = createMap(stateData);


            // MERGE CONFIG AND STATE DATA
            mapDataToBuilder(configMap, stateMap, mapEntryBuilder);
            mergeDataToBuilder(configMap, stateMap, mapEntryBuilder);

            return mapEntryBuilder.build();
        } else if (configDataNode instanceof ContainerNode) { // part for containers mapping
            final DataContainerNodeAttrBuilder<NodeIdentifier, ContainerNode> containerBuilder = Builders
                    .containerBuilder().withNodeIdentifier(((ContainerNode) configDataNode).getIdentifier());

            final Collection<? extends NormalizedNode<?, ?>> configData = ((ContainerNode) configDataNode).getValue();
            final Collection<? extends NormalizedNode<?, ?>> stateData = ((ContainerNode) stateDataNode).getValue();

            final Map<PathArgument, NormalizedNode<?, ?>> configMap = createMap(configData);
            final Map<PathArgument, NormalizedNode<?, ?>> stateMap = createMap(stateData);


            // MERGE CONFIG AND STATE DATA
            mapDataToBuilder(configMap, stateMap, containerBuilder);
            mergeDataToBuilder(configMap, stateMap,containerBuilder);

            return containerBuilder.build();
        } else if (configDataNode instanceof AugmentationNode) {
            final DataContainerNodeBuilder<AugmentationIdentifier, AugmentationNode> augmentationBuilder = Builders
                    .augmentationBuilder().withNodeIdentifier(((AugmentationNode) configDataNode).getIdentifier());

            final Collection<? extends NormalizedNode<?, ?>> configData = ((AugmentationNode) configDataNode).getValue();
            final Collection<? extends NormalizedNode<?, ?>> stateData = ((AugmentationNode) stateDataNode).getValue();

            final Map<PathArgument, NormalizedNode<?, ?>> configMap = createMap(configData);
            final Map<PathArgument, NormalizedNode<?, ?>> stateMap = createMap(stateData);


            // MERGE CONFIG AND STATE DATA
            mapDataToBuilder(configMap, stateMap, augmentationBuilder);
            mergeDataToBuilder(configMap, stateMap, augmentationBuilder);

            return augmentationBuilder.build();
        } else if (configDataNode instanceof ChoiceNode) {
            final DataContainerNodeBuilder<NodeIdentifier, ChoiceNode> choiceBuilder = Builders
                    .choiceBuilder().withNodeIdentifier(((ChoiceNode) configDataNode).getIdentifier());

            final Collection<? extends NormalizedNode<?, ?>> configData = ((ChoiceNode) configDataNode).getValue();
            final Collection<? extends NormalizedNode<?, ?>> stateData = ((ChoiceNode) stateDataNode).getValue();

            final Map<PathArgument, NormalizedNode<?, ?>> configMap = createMap(configData);
            final Map<PathArgument, NormalizedNode<?, ?>> stateMap = createMap(stateData);


            // MERGE CONFIG AND STATE DATA
            mapDataToBuilder(configMap, stateMap, choiceBuilder);
            mergeDataToBuilder(configMap, stateMap, choiceBuilder);

            return choiceBuilder.build();
        } else if (configDataNode instanceof LeafNode) {
            return ImmutableNodes.leafNode(configDataNode.getNodeType(), configDataNode.getValue());
        } else {
            throw new RestconfDocumentedException("Bad type of node.");
        }
    }

    private static Map<PathArgument, NormalizedNode<?, ?>> createMap(Collection<? extends NormalizedNode<?, ?>> data) {
        final Map<PathArgument, NormalizedNode<?, ?>> map = new HashMap<>();
        data.forEach(x -> map.put(x.getIdentifier(), x));
        return map;
    }

    private static void mergeDataToBuilder(final Map<PathArgument, NormalizedNode<?, ?>> configMap,
                                           final Map<PathArgument, NormalizedNode<?, ?>> stateMap,
                                           final NormalizedNodeContainerBuilder builder) {
        configMap.entrySet().stream().filter(x -> stateMap.containsKey(x.getKey())).forEach(
                y -> builder.addChild(prepareData(y.getValue(), stateMap.get(y.getKey()))));
        stateMap.entrySet().stream().filter(x -> configMap.containsKey(x.getKey())).forEach(
                y -> builder.addChild(prepareData(y.getValue(), configMap.get(y.getKey()))));
    }

    /**
     * Map data to builder
     *
     *  @param configMap
     *            - immutable config data
     *  @param stateMap
     *            - immutable state data
     * @param builder
     *           - builder
     */
    private static void mapDataToBuilder(final Map<PathArgument, NormalizedNode<?, ?>> configMap,
                                         final Map<PathArgument, NormalizedNode<?, ?>> stateMap,
                                         final NormalizedNodeContainerBuilder builder) {
        configMap.entrySet().stream().filter(x -> !stateMap.containsKey(x.getKey())).forEach(
                y -> builder.addChild(y.getValue()));
        stateMap.entrySet().stream().filter(x -> !configMap.containsKey(x.getKey())).forEach(
                y -> builder.addChild(y.getValue()));
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
}
