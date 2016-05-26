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
import org.opendaylight.restconf.restful.transaction.TransactionNode;
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

public class ReadDataTransactionUtil {

    private final static Logger LOG = LoggerFactory.getLogger(ReadDataTransactionUtil.class);
    private static final String CONFIG = "config";
    private static final String NONCONFIG = "nonconfig";
    private static final String ALL = "all";

    public static NormalizedNode<?, ?> readData(final String valueOfContent, final TransactionNode transactionNode) {
        if (valueOfContent != null) {
            switch (valueOfContent) {
                case CONFIG:
                    transactionNode.setLogicalDatastoreType(LogicalDatastoreType.CONFIGURATION);
                    return readData(transactionNode);
                case NONCONFIG:
                    transactionNode.setLogicalDatastoreType(LogicalDatastoreType.OPERATIONAL);
                    return readData(transactionNode);
                case ALL:
                    return readData(transactionNode);
                default:
                    throw new RestconfDocumentedException("Bad querry parameter for content.", ErrorType.APPLICATION,
                            ErrorTag.INVALID_VALUE);
            }
        } else {
            return readData(transactionNode);
        }
    }

    private static NormalizedNode<?, ?> readData(final TransactionNode transactionNode) {
        if (transactionNode.getMountPoint() == null) {
            return readDataViaTransaction(transactionNode.getDomTransactionChain().newReadOnlyTransaction(),
                    transactionNode);
        } else {
            return readDataOfMountPointViaTransaction(transactionNode);
        }
    }

    private static NormalizedNode<?, ?> readDataViaTransaction(final DOMDataReadTransaction readTransaction,
            final TransactionNode transactionNode) {
        if (transactionNode.getLogicalDatastoreType() != null) {
            final CheckedFuture<Optional<NormalizedNode<?, ?>>, ReadFailedException> listenableFuture = readTransaction
                    .read(transactionNode.getLogicalDatastoreType(),
                            transactionNode.getInstanceIdentifier().getInstanceIdentifier());
            return getNodeFromFuture(listenableFuture, transactionNode);
        } else {
            return readAllData(transactionNode);
        }
    }

    private static NormalizedNode<?, ?> readDataOfMountPointViaTransaction(final TransactionNode transactionNode) {
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

    private static NormalizedNode<?, ?> readAllData(final TransactionNode transactionNode) {
        // PREPARE STATE DATA NODE
        transactionNode.setLogicalDatastoreType(LogicalDatastoreType.OPERATIONAL);
        final NormalizedNode<?, ?> stateDataNode = readData(transactionNode);

        // PREPARE CONFIG DATA NODE
        transactionNode.setLogicalDatastoreType(LogicalDatastoreType.CONFIGURATION);
        final NormalizedNode<?, ?> configDataNode = readData(transactionNode);

        return mapNode(stateDataNode, configDataNode, transactionNode);
    }

    private static NormalizedNode<?, ?> mapNode(final NormalizedNode<?, ?> stateDataNode,
            final NormalizedNode<?, ?> configDataNode,
            final TransactionNode transactionNode) {
        validPossibilityOfMergeNodes(stateDataNode, configDataNode);
        if (configDataNode instanceof RpcDefinition) {
            return prepareRpcData(configDataNode, stateDataNode);
        } else {
            return prepareData(configDataNode, stateDataNode);
        }
    }

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

    private static void mapRpcDataNode(final NormalizedNode<?, ?> dataNode,
            final DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> mapEntryBuilder) {
        for (final DataContainerChild<? extends PathArgument, ?> child : ((ContainerNode) dataNode).getValue()) {
            mapEntryBuilder.addChild(child);
        }
    }

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

    private static <T extends DataContainerNode<? extends PathArgument>> void addChildToMap(final Class<T> type,
            final DataContainerChild<? extends PathArgument, ?> child,
            final DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> mapEntryBuilder) {
        final T node = (T) child;
        for (final DataContainerChild<? extends PathArgument, ?> childNode : node.getValue()) {
            mapEntryBuilder.addChild(childNode);
        }
    }

    private static void validPossibilityOfMergeNodes(final NormalizedNode<?, ?> stateDataNode,
            final NormalizedNode<?, ?> configDataNode) {
        final QNameModule moduleOfStateData = stateDataNode.getIdentifier().getNodeType().getModule();
        final QNameModule moduleOfConfigData = configDataNode.getIdentifier().getNodeType().getModule();
        if (moduleOfStateData != moduleOfConfigData) {
            throw new RestconfDocumentedException("It is not possible to mergre ");
        }
    }

    private static NormalizedNode<?, ?> getNodeFromFuture(
            final CheckedFuture<Optional<NormalizedNode<?, ?>>, ReadFailedException> listenableFuture,
            final TransactionNode transactionNode) {
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
