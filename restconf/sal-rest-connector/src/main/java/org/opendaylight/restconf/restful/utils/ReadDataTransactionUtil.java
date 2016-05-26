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
import java.util.concurrent.ExecutionException;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadTransaction;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorTag;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorType;
import org.opendaylight.restconf.restful.transaction.TransactionVarsWrapper;
import org.opendaylight.yangtools.yang.common.QNameModule;
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
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.DataContainerNodeBuilder;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private final static Logger LOG = LoggerFactory.getLogger(ReadDataTransactionUtil.class);

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
        if (valueOfContent != null) {
            switch (valueOfContent) {
                case RestconfDataServiceConstant.ReadData.CONFIG:
                    transactionNode.setLogicalDatastoreType(LogicalDatastoreType.CONFIGURATION);
                    return readData(transactionNode);
                case RestconfDataServiceConstant.ReadData.NONCONFIG:
                    transactionNode.setLogicalDatastoreType(LogicalDatastoreType.OPERATIONAL);
                    return readData(transactionNode);
                case RestconfDataServiceConstant.ReadData.ALL:
                    return readData(transactionNode);
                default:
                    throw new RestconfDocumentedException("Bad querry parameter for content.", ErrorType.APPLICATION,
                            ErrorTag.INVALID_VALUE);
            }
        } else {
            return readData(transactionNode);
        }
    }

    /**
     * Check mount point
     *
     * @param transactionNode
     *            - {@link TransactionVarsWrapper} - wrapper for variables
     * @return {@link NormalizedNode}
     */
    private static NormalizedNode<?, ?> readData(final TransactionVarsWrapper transactionNode) {
        if (transactionNode.getMountPoint() == null) {
            return readDataViaTransaction(transactionNode.getDomTransactionChain().newReadOnlyTransaction(),
                    transactionNode);
        } else {
            return readDataOfMountPointViaTransaction(transactionNode);
        }
    }

    /**
     * If is set specific {@link LogicalDatastoreType} in
     * {@link TransactionVarsWrapper}, then read this type of data from DS. If don't,
     * we have to read all data from DS (state + config)
     *
     * @param readTransaction
     *            - {@link DOMDataReadTransaction} to read data from DS
     * @param transactionNode
     *            - {@link TransactionVarsWrapper} - wrapper for variables
     * @return {@link NormalizedNode}
     */
    private static NormalizedNode<?, ?> readDataViaTransaction(final DOMDataReadTransaction readTransaction,
            final TransactionVarsWrapper transactionNode) {
        if (transactionNode.getLogicalDatastoreType() != null) {
            final CheckedFuture<Optional<NormalizedNode<?, ?>>, ReadFailedException> listenableFuture = readTransaction
                    .read(transactionNode.getLogicalDatastoreType(),
                            transactionNode.getInstanceIdentifier().getInstanceIdentifier());
            final FutureDataFactory dataFactory = new FutureDataFactory();
            FutureCallbackTx.addCallback(listenableFuture, readTransaction,
                    RestconfDataServiceConstant.ReadData.READ_TYPE_TX, dataFactory);
            return dataFactory.build();
        } else {
            return readAllData(transactionNode);
        }
    }

    /**
     * Prepare transaction to read data of mount point, if these data are
     * present.
     *
     * @param transactionNode
     *            - {@link TransactionVarsWrapper} - wrapper for variables
     * @return {@link NormalizedNode}
     */
    private static NormalizedNode<?, ?> readDataOfMountPointViaTransaction(final TransactionVarsWrapper transactionNode) {
        final Optional<DOMDataBroker> domDataBrokerService = transactionNode.getMountPoint()
                .getService(DOMDataBroker.class);
        if (domDataBrokerService.isPresent()) {
            return readDataViaTransaction(domDataBrokerService.get().newReadOnlyTransaction(), transactionNode);
        } else {
            final String errMsg = "DOM data broker service isn't available for mount point "
                    + transactionNode.getInstanceIdentifier().getInstanceIdentifier();
            LOG.warn(errMsg);
            throw new RestconfDocumentedException(errMsg);
        }
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
        final NormalizedNode<?, ?> stateDataNode = readData(transactionNode);

        // PREPARE CONFIG DATA NODE
        transactionNode.setLogicalDatastoreType(LogicalDatastoreType.CONFIGURATION);
        final NormalizedNode<?, ?> configDataNode = readData(transactionNode);

        return mapNode(stateDataNode, configDataNode, transactionNode);
    }

    /**
     * Map data by type of read node.
     *
     * @param stateDataNode
     *            - data node of state data
     * @param configDataNode
     *            - data node of config data
     * @param transactionNode
     *            - {@link TransactionVarsWrapper} - wrapper for variables
     * @return {@link NormalizedNode}
     */
    private static NormalizedNode<?, ?> mapNode(final NormalizedNode<?, ?> stateDataNode,
            final NormalizedNode<?, ?> configDataNode,
            final TransactionVarsWrapper transactionNode) {
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
        final MapNode immutableStateData = ImmutableNodes.mapNodeBuilder(stateDataNode.getNodeType())
                .addChild((MapEntryNode) stateDataNode).build();
        final MapNode immutableConfigData = ImmutableNodes.mapNodeBuilder(configDataNode.getNodeType())
                .addChild((MapEntryNode) configDataNode).build();

        final DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> mapEntryBuilder = ImmutableNodes
                .mapEntryBuilder();
        mapEntryBuilder.withNodeIdentifier((NodeIdentifierWithPredicates) configDataNode.getIdentifier());

        // MAP CONFIG DATA
        mapDataNode(immutableConfigData, mapEntryBuilder);
        // MAP STATE DATA
        mapDataNode(immutableStateData, mapEntryBuilder);

        return ImmutableNodes.mapNodeBuilder(configDataNode.getNodeType()).addChild(mapEntryBuilder.build()).build();
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
    private static void validPossibilityOfMergeNodes(final NormalizedNode<?, ?> stateDataNode,
            final NormalizedNode<?, ?> configDataNode) {
        final QNameModule moduleOfStateData = stateDataNode.getIdentifier().getNodeType().getModule();
        final QNameModule moduleOfConfigData = configDataNode.getIdentifier().getNodeType().getModule();
        if (moduleOfStateData != moduleOfConfigData) {
            throw new RestconfDocumentedException("It is not possible to merge ");
        }
    }

    /**
     * Get data from future object if these data are present.
     *
     * @param listenableFuture
     *            - future of optional {@link NormalizedNode}
     * @param transactionNode
     *            - {@link TransactionVarsWrapper} - wrapper for variables
     * @return {@link NormalizedNode}
     */
    private static NormalizedNode<?, ?> getNodeFromFuture(
            final CheckedFuture<Optional<NormalizedNode<?, ?>>, ReadFailedException> listenableFuture,
            final TransactionVarsWrapper transactionNode) {
        Optional<NormalizedNode<?, ?>> optional;
        try {
            LOG.debug("Reading result data from transaction.");
            optional = listenableFuture.get();
        } catch (InterruptedException | ExecutionException e) {
            LOG.warn("Exception by reading {} via Restconf: {}", transactionNode.getLogicalDatastoreType().name(),
                    transactionNode.getInstanceIdentifier().getInstanceIdentifier(), e);
            throw new RestconfDocumentedException("Problem to get data from transaction.", e.getCause());

        }
        if (optional != null) {
            if (optional.isPresent()) {
                return optional.get();
            }
        }
        throw new RestconfDocumentedException("Normalized node is not available : "
                + transactionNode.getInstanceIdentifier().getInstanceIdentifier());
    }
}
