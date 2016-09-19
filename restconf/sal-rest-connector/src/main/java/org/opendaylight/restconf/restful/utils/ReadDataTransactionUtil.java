/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.restful.utils;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.CheckedFuture;
import java.util.Collection;
import javax.annotation.Nonnull;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorTag;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorType;
import org.opendaylight.restconf.restful.transaction.TransactionVarsWrapper;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.AugmentationNode;
import org.opendaylight.yangtools.yang.data.api.schema.ChoiceNode;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.DataContainerNodeAttrBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.DataContainerNodeBuilder;
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
                    throw new RestconfDocumentedException("Bad querry parameter for content.", ErrorType.APPLICATION,
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
            final DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> mapEntryBuilder = ImmutableNodes
                    .mapEntryBuilder();
            final NodeIdentifierWithPredicates node = ((MapNode) configDataNode).getValue().iterator().next().getIdentifier();
            mapEntryBuilder.withNodeIdentifier(node);

            // MAP CONFIG DATA
            mapDataNode((MapNode) configDataNode, mapEntryBuilder);
            // MAP STATE DATA
            mapDataNode((MapNode) stateDataNode, mapEntryBuilder);
            return ImmutableNodes.mapNodeBuilder(configDataNode.getNodeType()).addChild(mapEntryBuilder.build()).build();
        } else if (configDataNode instanceof MapEntryNode) {
            // FIXME adds only operational
            return ImmutableNodes.mapNodeBuilder(configDataNode.getNodeType())
                    .addChild((MapEntryNode) configDataNode)
                    .addChild((MapEntryNode) stateDataNode)
                    .build();
        } else if (configDataNode instanceof ContainerNode) { // part for containers mapping
            final DataContainerNodeAttrBuilder<NodeIdentifier, ContainerNode> containerBuilder = Builders
                    .containerBuilder((ContainerNode) configDataNode);

            // MAP CONFIG DATA
            mapCont(containerBuilder, ((ContainerNode) configDataNode).getValue());
            // MAP STATE DATA
            mapCont(containerBuilder, ((ContainerNode) stateDataNode).getValue());
            return containerBuilder.build();
        } else {
            throw new RestconfDocumentedException("Bad type of node.");
        }
    }

    /**
     * Map data to builder
     *
     * @param containerBuilder
     *            - builder for mapping data
     * @param childs
     *            - childs of data (container)
     */
    private static void mapCont(final DataContainerNodeAttrBuilder<NodeIdentifier, ContainerNode> containerBuilder,
            final Collection<DataContainerChild<? extends PathArgument, ?>> childs) {
        for (final DataContainerChild<? extends PathArgument, ?> child : childs) {
            containerBuilder.addChild(child);
        }
    }

    /**
     * Map data to builder
     *
     * @param immutableData
     *            - immutable data - {@link MapNode}
     * @param mapEntryBuilder
     *            - builder for mapping data
     */
    private static void mapDataNode(final MapNode immutableData,
            final DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> mapEntryBuilder) {
        for (final DataContainerChild<? extends PathArgument, ?> child : immutableData.getValue().iterator()
                .next().getValue()) {
            Preconditions.checkNotNull(child);
            if (child instanceof ContainerNode) {
                addChildToMap(ContainerNode.class, child, mapEntryBuilder);
            } else if (child instanceof AugmentationNode) {
                addChildToMap(AugmentationNode.class, child, mapEntryBuilder);
            } else if(child instanceof MapNode){
                final MapNode listNode = (MapNode) child;
                for (final MapEntryNode listChild : listNode.getValue()) {
                    for (final DataContainerChild<? extends PathArgument, ?> entryChild : listChild.getValue()) {
                        addChildToMap(MapEntryNode.class, entryChild, mapEntryBuilder);
                    }
                }
            } else if (child instanceof ChoiceNode) {
                addChildToMap(ChoiceNode.class, child, mapEntryBuilder);
            } else if ((child instanceof LeafSetNode<?>) || (child instanceof LeafNode)) {
                mapEntryBuilder.addChild(child);
            }

        }
    }

    /**
     * Mapping child
     *
     * @param type
     *            - type of data
     * @param child
     *            - child to map
     * @param mapEntryBuilder
     *            - builder for mapping child
     */
    private static <T extends DataContainerNode<? extends PathArgument>> void addChildToMap(final Class<T> type,
            final DataContainerChild<? extends PathArgument, ?> child,
            final DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> mapEntryBuilder) {
        @SuppressWarnings("unchecked")
        final T node = (T) child;
        for (final DataContainerChild<? extends PathArgument, ?> childNode : node.getValue()) {
            mapEntryBuilder.addChild(childNode);
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
}
