/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.utils;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.ContentParam;
import org.opendaylight.restconf.nb.rfc8040.WithDefaultsParam;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.RestconfStrategy;
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
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.SystemLeafSetNode;
import org.opendaylight.yangtools.yang.data.api.schema.SystemMapNode;
import org.opendaylight.yangtools.yang.data.api.schema.UnkeyedListEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.UnkeyedListNode;
import org.opendaylight.yangtools.yang.data.api.schema.UserLeafSetNode;
import org.opendaylight.yangtools.yang.data.api.schema.UserMapNode;
import org.opendaylight.yangtools.yang.data.api.schema.builder.CollectionNodeBuilder;
import org.opendaylight.yangtools.yang.data.api.schema.builder.DataContainerNodeBuilder;
import org.opendaylight.yangtools.yang.data.api.schema.builder.ListNodeBuilder;
import org.opendaylight.yangtools.yang.data.api.schema.builder.NormalizedNodeContainerBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.impl.ImmutableContainerNodeBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.impl.ImmutableLeafNodeBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.impl.ImmutableMapEntryNodeBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.impl.ImmutableMapNodeBuilder;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;

/**
 * Util class for read data from data store via transaction.
 * <ul>
 * <li>config
 * <li>state
 * <li>all (config + state)
 * </ul>
 */
public final class ReadDataTransactionUtil {
    private ReadDataTransactionUtil() {
        // Hidden on purpose
    }

    /**
     * Read specific type of data from data store via transaction. Close {@link DOMTransactionChain} if any
     * inside of object {@link RestconfStrategy} provided as a parameter.
     *
     * @param content        type of data to read (config, state, all)
     * @param path           the path to read
     * @param strategy       {@link RestconfStrategy} - object that perform the actual DS operations
     * @param withDefa       value of with-defaults parameter
     * @return {@link NormalizedNode}
     */
    public static @Nullable NormalizedNode readData(final @NonNull ContentParam content,
                                                    final @NonNull YangInstanceIdentifier path,
                                                    final @NonNull RestconfStrategy strategy,
                                                    final WithDefaultsParam withDefa) {
        return switch (content) {
            case ALL -> readAllData(strategy, path, withDefa);
            case CONFIG -> {
                final var read = readDataViaTransaction(strategy, LogicalDatastoreType.CONFIGURATION, path);
                yield withDefa == null ? read : prepareDataByParamWithDef(read, withDefa);
            }
            case NONCONFIG -> readDataViaTransaction(strategy, LogicalDatastoreType.OPERATIONAL, path);
        };
    }

    /**
     * Read specific type of data from data store via transaction with specified subtrees that should only be read.
     * Close {@link DOMTransactionChain} inside of object {@link RestconfStrategy} provided as a parameter.
     *
     * @param content        type of data to read (config, state, all)
     * @param path           the parent path to read
     * @param strategy       {@link RestconfStrategy} - object that perform the actual DS operations
     * @param withDefa       value of with-defaults parameter
     * @param fields         paths to selected subtrees which should be read, relative to to the parent path
     * @return {@link NormalizedNode}
     */
    public static @Nullable NormalizedNode readData(final @NonNull ContentParam content,
            final @NonNull YangInstanceIdentifier path, final @NonNull RestconfStrategy strategy,
            final @Nullable WithDefaultsParam withDefa, final @NonNull List<YangInstanceIdentifier> fields) {
        return switch (content) {
            case ALL -> readAllData(strategy, path, withDefa, fields);
            case CONFIG -> {
                final var read = readDataViaTransaction(strategy, LogicalDatastoreType.CONFIGURATION, path, fields);
                yield withDefa == null ? read : prepareDataByParamWithDef(read, withDefa);
            }
            case NONCONFIG -> readDataViaTransaction(strategy, LogicalDatastoreType.OPERATIONAL, path, fields);
        };
    }

    private static NormalizedNode prepareDataByParamWithDef(final NormalizedNode result,
            final WithDefaultsParam withDefa) {
        final boolean trim = switch (withDefa) {
            case TRIM -> true;
            case EXPLICIT -> false;
            case REPORT_ALL, REPORT_ALL_TAGGED -> throw new RestconfDocumentedException(
                    "Unsupported with-defaults value " + withDefa.paramValue());
        };
        final var qName = result.getIdentifier().getNodeType();
        final var identifier = NodeIdentifier.create(qName);
        if (result instanceof ContainerNode) {
            final var builder = ImmutableContainerNodeBuilder.create()
                    .withNodeIdentifier(identifier)
                    .withValue(((ContainerNode) result).body());
            buildCont(builder, (ContainerNode) result, trim);
            return builder.build();
        } else {
            final var builder = ImmutableMapEntryNodeBuilder.create()
                    .withNodeIdentifier(NodeIdentifierWithPredicates.of(identifier.getNodeType()))
                    .withValue((Collection<DataContainerChild>) result.body());
            buildMapEntryBuilder(builder, (MapEntryNode) result, trim);
            return builder.build();
        }
    }

    private static void buildMapEntryBuilder(
            final DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> builder,
            final MapEntryNode result, final boolean trim) {
        for (final DataContainerChild child : result.body()) {
            final var qName = child.getIdentifier().getNodeType();
            final var identifier = NodeIdentifier.create(qName);
            if (child instanceof ContainerNode) {
                final var childBuilder = ImmutableContainerNodeBuilder.create()
                        .withNodeIdentifier(identifier);
                buildCont(childBuilder, (ContainerNode) child, trim);
                builder.withChild(childBuilder.build());
            } else if (child instanceof MapNode) {
                final var childBuilder = ImmutableMapNodeBuilder.create()
                        .withNodeIdentifier(identifier);
                buildList(childBuilder, (MapNode) child, trim);
                builder.withChild(childBuilder.build());
            } else if (child instanceof LeafNode) {
                final var childBuilder = ImmutableLeafNodeBuilder.create()
                        .withNodeIdentifier(identifier)
                        .withValue(child.body());
                builder.withChild(childBuilder.build());
            }
        }
    }

    private static void buildList(final CollectionNodeBuilder<MapEntryNode, SystemMapNode> builder,
            final MapNode result, final boolean trim) {
        for (final MapEntryNode mapEntryNode : result.body()) {
            final var qName = mapEntryNode.getIdentifier().getNodeType();
            final var identifier = NodeIdentifierWithPredicates.of(qName);
            final var mapEntryBuilder = ImmutableMapEntryNodeBuilder.create()
                    .withNodeIdentifier(identifier)
                    .withValue(mapEntryNode.body());
            buildMapEntryBuilder(mapEntryBuilder, mapEntryNode, trim);
            builder.withChild(mapEntryBuilder.build());
        }
    }

    private static void buildCont(final DataContainerNodeBuilder<NodeIdentifier, ContainerNode> builder,
            final ContainerNode result, final boolean trim) {
        for (final DataContainerChild child : result.body()) {
            final var qName = child.getIdentifier().getNodeType();
            final var identifier = NodeIdentifier.create(qName);
            if (child instanceof ContainerNode) {
                final var builderChild = ImmutableContainerNodeBuilder.create()
                        .withNodeIdentifier(identifier);
                buildCont(builderChild, (ContainerNode) child, trim);
                builder.withChild(builderChild.build());
            } else if (child instanceof MapNode) {
                final var childBuilder = ImmutableMapNodeBuilder.create()
                        .withNodeIdentifier(identifier);
                buildList(childBuilder, (MapNode) child, trim);
                builder.withChild(childBuilder.build());
            } else if (child instanceof LeafNode) {
                final var childBuilder = ImmutableLeafNodeBuilder.create()
                        .withNodeIdentifier(identifier)
                        .withValue(child.body());
                builder.withChild(childBuilder.build());
            }
        }
    }

    /**
     * If is set specific {@link LogicalDatastoreType} in {@link RestconfStrategy}, then read this type of data from DS.
     * If don't, we have to read all data from DS (state + config)
     *
     * @param strategy              {@link RestconfStrategy} - object that perform the actual DS operations
     * @param closeTransactionChain If is set to true, after transaction it will close transactionChain
     *                              in {@link RestconfStrategy} if any
     * @return {@link NormalizedNode}
     */
    static @Nullable NormalizedNode readDataViaTransaction(final @NonNull RestconfStrategy strategy,
            final LogicalDatastoreType store, final YangInstanceIdentifier path) {
        return extractReadData(strategy, path, strategy.read(store, path));
    }

    /**
     * Read specific type of data {@link LogicalDatastoreType} via transaction in {@link RestconfStrategy} with
     * specified subtrees that should only be read.
     *
     * @param strategy              {@link RestconfStrategy} - object that perform the actual DS operations
     * @param store                 datastore type
     * @param path                  parent path to selected fields
     * @param closeTransactionChain if it is set to {@code true}, after transaction it will close transactionChain
     *                              in {@link RestconfStrategy} if any
     * @param fields                paths to selected subtrees which should be read, relative to to the parent path
     * @return {@link NormalizedNode}
     */
    private static @Nullable NormalizedNode readDataViaTransaction(final @NonNull RestconfStrategy strategy,
            final @NonNull LogicalDatastoreType store, final @NonNull YangInstanceIdentifier path,
            final @NonNull List<YangInstanceIdentifier> fields) {
        return extractReadData(strategy, path, strategy.read(store, path, fields));
    }

    private static NormalizedNode extractReadData(final RestconfStrategy strategy,
            final YangInstanceIdentifier path, final ListenableFuture<Optional<NormalizedNode>> dataFuture) {
        final NormalizedNodeFactory dataFactory = new NormalizedNodeFactory();
        FutureCallbackTx.addCallback(dataFuture, "READ", dataFactory, path);
        return dataFactory.build();
    }

    /**
     * Read config and state data, then map them. Close {@link DOMTransactionChain} inside of object
     * {@link RestconfStrategy} provided as a parameter if any.
     *
     * @param strategy {@link RestconfStrategy} - object that perform the actual DS operations
     * @param withDefa with-defaults parameter
     * @return {@link NormalizedNode}
     */
    private static @Nullable NormalizedNode readAllData(final @NonNull RestconfStrategy strategy,
            final YangInstanceIdentifier path, final WithDefaultsParam withDefa) {
        // PREPARE STATE DATA NODE
        final NormalizedNode stateDataNode = readDataViaTransaction(strategy, LogicalDatastoreType.OPERATIONAL, path);
        // PREPARE CONFIG DATA NODE
        final NormalizedNode configDataNode = readDataViaTransaction(strategy, LogicalDatastoreType.CONFIGURATION,
            path);

        return mergeConfigAndSTateDataIfNeeded(stateDataNode,
            withDefa == null ? configDataNode : prepareDataByParamWithDef(configDataNode, withDefa));
    }

    /**
     * Read config and state data with selected subtrees that should only be read, then map them.
     * Close {@link DOMTransactionChain} inside of object {@link RestconfStrategy} provided as a parameter.
     *
     * @param strategy {@link RestconfStrategy} - object that perform the actual DS operations
     * @param path     parent path to selected fields
     * @param withDefa with-defaults parameter
     * @param fields   paths to selected subtrees which should be read, relative to to the parent path
     * @return {@link NormalizedNode}
     */
    private static @Nullable NormalizedNode readAllData(final @NonNull RestconfStrategy strategy,
            final @NonNull YangInstanceIdentifier path, final @Nullable WithDefaultsParam withDefa,
            final @NonNull List<YangInstanceIdentifier> fields) {
        // PREPARE STATE DATA NODE
        final NormalizedNode stateDataNode = readDataViaTransaction(strategy, LogicalDatastoreType.OPERATIONAL, path,
            fields);

        // PREPARE CONFIG DATA NODE
        final NormalizedNode configDataNode = readDataViaTransaction(strategy, LogicalDatastoreType.CONFIGURATION, path,
            fields);
        return mergeConfigAndSTateDataIfNeeded(stateDataNode,
            withDefa == null ? configDataNode : prepareDataByParamWithDef(configDataNode, withDefa));
    }

    private static NormalizedNode mergeConfigAndSTateDataIfNeeded(final NormalizedNode stateDataNode,
                                                                  final NormalizedNode configDataNode) {
        // if no data exists
        if (stateDataNode == null && configDataNode == null) {
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
        return mergeStateAndConfigData(stateDataNode, configDataNode);
    }

    /**
     * Merge state and config data into a single NormalizedNode.
     *
     * @param stateDataNode  data node of state data
     * @param configDataNode data node of config data
     * @return {@link NormalizedNode}
     */
    private static @NonNull NormalizedNode mergeStateAndConfigData(
            final @NonNull NormalizedNode stateDataNode, final @NonNull NormalizedNode configDataNode) {
        validateNodeMerge(stateDataNode, configDataNode);
        if (configDataNode instanceof RpcDefinition) {
            return prepareRpcData(configDataNode, stateDataNode);
        } else {
            return prepareData(configDataNode, stateDataNode);
        }
    }

    /**
     * Validates whether the two NormalizedNodes can be merged.
     *
     * @param stateDataNode  data node of state data
     * @param configDataNode data node of config data
     */
    private static void validateNodeMerge(final @NonNull NormalizedNode stateDataNode,
                                          final @NonNull NormalizedNode configDataNode) {
        final QNameModule moduleOfStateData = stateDataNode.getIdentifier().getNodeType().getModule();
        final QNameModule moduleOfConfigData = configDataNode.getIdentifier().getNodeType().getModule();
        if (!moduleOfStateData.equals(moduleOfConfigData)) {
            throw new RestconfDocumentedException("Unable to merge data from different modules.");
        }
    }

    /**
     * Prepare and map data for rpc.
     *
     * @param configDataNode data node of config data
     * @param stateDataNode  data node of state data
     * @return {@link NormalizedNode}
     */
    private static @NonNull NormalizedNode prepareRpcData(final @NonNull NormalizedNode configDataNode,
                                                          final @NonNull NormalizedNode stateDataNode) {
        final DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> mapEntryBuilder = ImmutableNodes
                .mapEntryBuilder();
        mapEntryBuilder.withNodeIdentifier((NodeIdentifierWithPredicates) configDataNode.getIdentifier());

        // MAP CONFIG DATA
        mapRpcDataNode(configDataNode, mapEntryBuilder);
        // MAP STATE DATA
        mapRpcDataNode(stateDataNode, mapEntryBuilder);

        return ImmutableNodes.mapNodeBuilder(configDataNode.getIdentifier().getNodeType())
            .addChild(mapEntryBuilder.build())
            .build();
    }

    /**
     * Map node to map entry builder.
     *
     * @param dataNode        data node
     * @param mapEntryBuilder builder for mapping data
     */
    private static void mapRpcDataNode(final @NonNull NormalizedNode dataNode,
            final @NonNull DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> mapEntryBuilder) {
        ((ContainerNode) dataNode).body().forEach(mapEntryBuilder::addChild);
    }

    /**
     * Prepare and map all data from DS.
     *
     * @param configDataNode data node of config data
     * @param stateDataNode  data node of state data
     * @return {@link NormalizedNode}
     */
    @SuppressWarnings("unchecked")
    private static @NonNull NormalizedNode prepareData(final @NonNull NormalizedNode configDataNode,
                                                       final @NonNull NormalizedNode stateDataNode) {
        if (configDataNode instanceof UserMapNode) {
            final CollectionNodeBuilder<MapEntryNode, UserMapNode> builder = Builders
                    .orderedMapBuilder().withNodeIdentifier(((MapNode) configDataNode).getIdentifier());

            mapValueToBuilder(
                    ((UserMapNode) configDataNode).body(), ((UserMapNode) stateDataNode).body(), builder);

            return builder.build();
        } else if (configDataNode instanceof MapNode) {
            final CollectionNodeBuilder<MapEntryNode, SystemMapNode> builder = ImmutableNodes
                    .mapNodeBuilder().withNodeIdentifier(((MapNode) configDataNode).getIdentifier());

            mapValueToBuilder(
                    ((MapNode) configDataNode).body(), ((MapNode) stateDataNode).body(), builder);

            return builder.build();
        } else if (configDataNode instanceof MapEntryNode) {
            final DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> builder = ImmutableNodes
                    .mapEntryBuilder().withNodeIdentifier(((MapEntryNode) configDataNode).getIdentifier());

            mapValueToBuilder(
                    ((MapEntryNode) configDataNode).body(), ((MapEntryNode) stateDataNode).body(), builder);

            return builder.build();
        } else if (configDataNode instanceof ContainerNode) {
            final DataContainerNodeBuilder<NodeIdentifier, ContainerNode> builder = Builders
                    .containerBuilder().withNodeIdentifier(((ContainerNode) configDataNode).getIdentifier());

            mapValueToBuilder(
                    ((ContainerNode) configDataNode).body(), ((ContainerNode) stateDataNode).body(), builder);

            return builder.build();
        } else if (configDataNode instanceof AugmentationNode) {
            final DataContainerNodeBuilder<AugmentationIdentifier, AugmentationNode> builder = Builders
                    .augmentationBuilder().withNodeIdentifier(((AugmentationNode) configDataNode).getIdentifier());

            mapValueToBuilder(((AugmentationNode) configDataNode).body(),
                    ((AugmentationNode) stateDataNode).body(), builder);

            return builder.build();
        } else if (configDataNode instanceof ChoiceNode) {
            final DataContainerNodeBuilder<NodeIdentifier, ChoiceNode> builder = Builders
                    .choiceBuilder().withNodeIdentifier(((ChoiceNode) configDataNode).getIdentifier());

            mapValueToBuilder(
                    ((ChoiceNode) configDataNode).body(), ((ChoiceNode) stateDataNode).body(), builder);

            return builder.build();
        } else if (configDataNode instanceof LeafNode) {
            return ImmutableNodes.leafNode(configDataNode.getIdentifier().getNodeType(), configDataNode.body());
        } else if (configDataNode instanceof UserLeafSetNode) {
            final ListNodeBuilder<Object, UserLeafSetNode<Object>> builder = Builders
                .orderedLeafSetBuilder().withNodeIdentifier(((UserLeafSetNode<?>) configDataNode).getIdentifier());

            mapValueToBuilder(((UserLeafSetNode<Object>) configDataNode).body(),
                    ((UserLeafSetNode<Object>) stateDataNode).body(), builder);
            return builder.build();
        } else if (configDataNode instanceof LeafSetNode) {
            final ListNodeBuilder<Object, SystemLeafSetNode<Object>> builder = Builders
                    .leafSetBuilder().withNodeIdentifier(((LeafSetNode<?>) configDataNode).getIdentifier());

            mapValueToBuilder(((LeafSetNode<Object>) configDataNode).body(),
                    ((LeafSetNode<Object>) stateDataNode).body(), builder);
            return builder.build();
        } else if (configDataNode instanceof LeafSetEntryNode) {
            return Builders.leafSetEntryBuilder()
                    .withNodeIdentifier(((LeafSetEntryNode<?>) configDataNode).getIdentifier())
                    .withValue(configDataNode.body())
                    .build();
        } else if (configDataNode instanceof UnkeyedListNode) {
            final CollectionNodeBuilder<UnkeyedListEntryNode, UnkeyedListNode> builder = Builders
                    .unkeyedListBuilder().withNodeIdentifier(((UnkeyedListNode) configDataNode).getIdentifier());

            mapValueToBuilder(((UnkeyedListNode) configDataNode).body(),
                    ((UnkeyedListNode) stateDataNode).body(), builder);
            return builder.build();
        } else if (configDataNode instanceof UnkeyedListEntryNode) {
            final DataContainerNodeBuilder<NodeIdentifier, UnkeyedListEntryNode> builder = Builders
                .unkeyedListEntryBuilder().withNodeIdentifier(((UnkeyedListEntryNode) configDataNode).getIdentifier());

            mapValueToBuilder(((UnkeyedListEntryNode) configDataNode).body(),
                    ((UnkeyedListEntryNode) stateDataNode).body(), builder);
            return builder.build();
        } else {
            throw new RestconfDocumentedException("Unexpected node type: " + configDataNode.getClass().getName());
        }
    }

    /**
     * Map value from container node to builder.
     *
     * @param configData collection of config data nodes
     * @param stateData  collection of state data nodes
     * @param builder    builder
     */
    private static <T extends NormalizedNode> void mapValueToBuilder(
            final @NonNull Collection<T> configData, final @NonNull Collection<T> stateData,
            final @NonNull NormalizedNodeContainerBuilder<?, PathArgument, T, ?> builder) {
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
     * @param configMap map of config data nodes
     * @param stateMap  map of state data nodes
     * @param builder   - builder
     */
    private static <T extends NormalizedNode> void mapDataToBuilder(
            final @NonNull Map<PathArgument, T> configMap, final @NonNull Map<PathArgument, T> stateMap,
            final @NonNull NormalizedNodeContainerBuilder<?, PathArgument, T, ?> builder) {
        configMap.entrySet().stream().filter(x -> !stateMap.containsKey(x.getKey())).forEach(
            y -> builder.addChild(y.getValue()));
        stateMap.entrySet().stream().filter(x -> !configMap.containsKey(x.getKey())).forEach(
            y -> builder.addChild(y.getValue()));
    }

    /**
     * Map data with the same identifiers to builder. Data with the same identifiers cannot be just added but we need to
     * go one level down with {@code prepareData} method.
     *
     * @param configMap immutable config data
     * @param stateMap  immutable state data
     * @param builder   - builder
     */
    @SuppressWarnings("unchecked")
    private static <T extends NormalizedNode> void mergeDataToBuilder(
            final @NonNull Map<PathArgument, T> configMap, final @NonNull Map<PathArgument, T> stateMap,
            final @NonNull NormalizedNodeContainerBuilder<?, PathArgument, T, ?> builder) {
        // it is enough to process only config data because operational contains the same data
        configMap.entrySet().stream().filter(x -> stateMap.containsKey(x.getKey())).forEach(
            y -> builder.addChild((T) prepareData(y.getValue(), stateMap.get(y.getKey()))));
    }
}
