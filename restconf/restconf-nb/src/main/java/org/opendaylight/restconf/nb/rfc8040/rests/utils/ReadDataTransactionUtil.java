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
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.restconf.api.query.ContentParam;
import org.opendaylight.restconf.api.query.WithDefaultsParam;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.RestconfStrategy;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.ChoiceNode;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetEntryNode;
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
import org.opendaylight.yangtools.yang.data.api.schema.builder.NormalizedNodeContainerBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContext;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextTree;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
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
 */
public final class ReadDataTransactionUtil {
    private static final Logger LOG = LoggerFactory.getLogger(ReadDataTransactionUtil.class);

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
     * @param ctx            schema context
     * @return {@link NormalizedNode}
     */
    public static @Nullable NormalizedNode readData(final @NonNull ContentParam content,
                                                    final @NonNull YangInstanceIdentifier path,
                                                    final @NonNull RestconfStrategy strategy,
                                                    final WithDefaultsParam withDefa,
                                                    final EffectiveModelContext ctx) {
        return switch (content) {
            case ALL -> readAllData(strategy, path, withDefa, ctx);
            case CONFIG -> {
                final var read = readDataViaTransaction(strategy, LogicalDatastoreType.CONFIGURATION, path);
                yield withDefa == null ? read : prepareDataByParamWithDef(read, path, withDefa, ctx);
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
     * @param ctx            schema context
     * @param fields         paths to selected subtrees which should be read, relative to to the parent path
     * @return {@link NormalizedNode}
     */
    public static @Nullable NormalizedNode readData(final @NonNull ContentParam content,
            final @NonNull YangInstanceIdentifier path, final @NonNull RestconfStrategy strategy,
            final @Nullable WithDefaultsParam withDefa, @NonNull final EffectiveModelContext ctx,
            final @NonNull List<YangInstanceIdentifier> fields) {
        return switch (content) {
            case ALL -> readAllData(strategy, path, withDefa, ctx, fields);
            case CONFIG -> {
                final var read = readDataViaTransaction(strategy, LogicalDatastoreType.CONFIGURATION, path, fields);
                yield withDefa == null ? read : prepareDataByParamWithDef(read, path, withDefa, ctx);
            }
            case NONCONFIG -> readDataViaTransaction(strategy, LogicalDatastoreType.OPERATIONAL, path, fields);
        };
    }

    private static NormalizedNode prepareDataByParamWithDef(final NormalizedNode readData,
            final YangInstanceIdentifier path, final WithDefaultsParam withDefa, final EffectiveModelContext ctx) {
        final boolean trim = switch (withDefa) {
            case TRIM -> true;
            case EXPLICIT -> false;
            case REPORT_ALL, REPORT_ALL_TAGGED -> throw new RestconfDocumentedException(
                "Unsupported with-defaults value " + withDefa.paramValue());
        };

        final var ctxNode = DataSchemaContextTree.from(ctx).findChild(path).orElseThrow();
        if (readData instanceof ContainerNode container) {
            final var builder = Builders.containerBuilder().withNodeIdentifier(container.name());
            buildCont(builder, container.body(), ctxNode, trim);
            return builder.build();
        } else if (readData instanceof MapEntryNode mapEntry) {
            if (!(ctxNode.dataSchemaNode() instanceof ListSchemaNode listSchema)) {
                throw new IllegalStateException("Input " + mapEntry + " does not match " + ctxNode);
            }

            final var builder = Builders.mapEntryBuilder().withNodeIdentifier(mapEntry.name());
            buildMapEntryBuilder(builder, mapEntry.body(), ctxNode, trim, listSchema.getKeyDefinition());
            return builder.build();
        } else {
            throw new IllegalStateException("Unhandled data contract " + readData.contract());
        }
    }

    private static void buildMapEntryBuilder(
            final DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> builder,
            final Collection<@NonNull DataContainerChild> children, final DataSchemaContext ctxNode,
            final boolean trim, final List<QName> keys) {
        for (var child : children) {
            final var childCtx = getChildContext(ctxNode, child);

            if (child instanceof ContainerNode container) {
                appendContainer(builder, container, childCtx, trim);
            } else if (child instanceof MapNode map) {
                appendMap(builder, map, childCtx, trim);
            } else if (child instanceof LeafNode<?> leaf) {
                appendLeaf(builder, leaf, childCtx, trim, keys);
            } else {
                // FIXME: we should never hit this, throw an ISE if this ever happens
                LOG.debug("Ignoring unhandled child contract {}", child.contract());
            }
        }
    }

    private static void appendContainer(final DataContainerNodeBuilder<?, ?> builder, final ContainerNode container,
            final DataSchemaContext ctxNode, final boolean trim) {
        final var childBuilder = Builders.containerBuilder().withNodeIdentifier(container.name());
        buildCont(childBuilder, container.body(), ctxNode, trim);
        builder.withChild(childBuilder.build());
    }

    private static void appendLeaf(final DataContainerNodeBuilder<?, ?> builder, final LeafNode<?> leaf,
            final DataSchemaContext ctxNode, final boolean trim, final List<QName> keys) {
        if (!(ctxNode.dataSchemaNode() instanceof LeafSchemaNode leafSchema)) {
            throw new IllegalStateException("Input " + leaf + " does not match " + ctxNode);
        }

        // FIXME: Document now this works with the likes of YangInstanceIdentifier. I bet it does not.
        final var defaultVal = leafSchema.getType().getDefaultValue().orElse(null);

        // This is a combined check for when we need to emit the leaf.
        if (
            // We always have to emit key leaf values
            keys.contains(leafSchema.getQName())
            // trim == WithDefaultsParam.TRIM and the source is assumed to store explicit values:
            //
            //            When data is retrieved with a <with-defaults> parameter equal to
            //            'trim', data nodes MUST NOT be reported if they contain the schema
            //            default value.  Non-configuration data nodes containing the schema
            //            default value MUST NOT be reported.
            //
            || trim && (defaultVal == null || !defaultVal.equals(leaf.body()))
            // !trim == WithDefaultsParam.EXPLICIT and the source is assume to store explicit values... but I fail to
            // grasp what we are doing here... emit only if it matches default ???!!!
            // FIXME: The WithDefaultsParam.EXPLICIT says:
            //
            //            Data nodes set to the YANG default by the client are reported.
            //
            //        and RFC8040 (https://www.rfc-editor.org/rfc/rfc8040#page-60) says:
            //
            //            If the "with-defaults" parameter is set to "explicit", then the
            //            server MUST adhere to the default-reporting behavior defined in
            //            Section 3.3 of [RFC6243].
            //
            //        and then RFC6243 (https://www.rfc-editor.org/rfc/rfc6243#section-3.3) says:
            //
            //            When data is retrieved with a <with-defaults> parameter equal to
            //            'explicit', a data node that was set by a client to its schema
            //            default value MUST be reported.  A conceptual data node that would be
            //            set by the server to the schema default value MUST NOT be reported.
            //            Non-configuration data nodes containing the schema default value MUST
            //            be reported.
            //
            // (rovarga): The source reports explicitly-defined leaves and does *not* create defaults by itself.
            //            This seems to disregard the 'trim = true' case semantics (see above).
            //            Combining the above, though, these checks are missing the 'non-config' check, which would
            //            distinguish, but barring that this check is superfluous and results in the wrong semantics.
            //            Without that input, this really should be  covered by the previous case.
                || !trim && defaultVal != null && defaultVal.equals(leaf.body())) {
            builder.withChild(leaf);
        }
    }

    private static void appendMap(final DataContainerNodeBuilder<?, ?> builder, final MapNode map,
            final DataSchemaContext childCtx, final boolean trim) {
        if (!(childCtx.dataSchemaNode() instanceof ListSchemaNode listSchema)) {
            throw new IllegalStateException("Input " + map + " does not match " + childCtx);
        }

        final var childBuilder = switch (map.ordering()) {
            case SYSTEM -> Builders.mapBuilder();
            case USER -> Builders.orderedMapBuilder();
        };
        buildList(childBuilder.withNodeIdentifier(map.name()), map.body(), childCtx, trim,
            listSchema.getKeyDefinition());
        builder.withChild(childBuilder.build());
    }

    private static void buildList(final CollectionNodeBuilder<MapEntryNode, ? extends MapNode> builder,
            final Collection<@NonNull MapEntryNode> entries, final DataSchemaContext ctxNode, final boolean trim,
            final List<@NonNull QName> keys) {
        for (var entry : entries) {
            final var childCtx = getChildContext(ctxNode, entry);
            final var mapEntryBuilder = Builders.mapEntryBuilder().withNodeIdentifier(entry.name());
            buildMapEntryBuilder(mapEntryBuilder, entry.body(), childCtx, trim, keys);
            builder.withChild(mapEntryBuilder.build());
        }
    }

    private static void buildCont(final DataContainerNodeBuilder<NodeIdentifier, ContainerNode> builder,
            final Collection<DataContainerChild> children, final DataSchemaContext ctxNode, final boolean trim) {
        for (var child : children) {
            final var childCtx = getChildContext(ctxNode, child);
            if (child instanceof ContainerNode container) {
                appendContainer(builder, container, childCtx, trim);
            } else if (child instanceof MapNode map) {
                appendMap(builder, map, childCtx, trim);
            } else if (child instanceof LeafNode<?> leaf) {
                appendLeaf(builder, leaf, childCtx, trim, List.of());
            }
        }
    }

    private static @NonNull DataSchemaContext getChildContext(final DataSchemaContext ctxNode,
            final NormalizedNode child) {
        final var childId = child.name();
        final var childCtx = ctxNode instanceof DataSchemaContext.Composite composite ? composite.childByArg(childId)
            : null;
        if (childCtx == null) {
            throw new NoSuchElementException("Cannot resolve child " + childId + " in " + ctxNode);
        }
        return childCtx;
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
        return extractReadData(path, strategy.read(store, path));
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
        return extractReadData(path, strategy.read(store, path, fields));
    }

    private static @Nullable NormalizedNode extractReadData(final YangInstanceIdentifier path,
            final ListenableFuture<Optional<NormalizedNode>> dataFuture) {
        return TransactionUtil.syncAccess(dataFuture, path).orElse(null);
    }

    /**
     * Read config and state data, then map them. Close {@link DOMTransactionChain} inside of object
     * {@link RestconfStrategy} provided as a parameter if any.
     *
     * @param strategy {@link RestconfStrategy} - object that perform the actual DS operations
     * @param withDefa with-defaults parameter
     * @param ctx      schema context
     * @return {@link NormalizedNode}
     */
    private static @Nullable NormalizedNode readAllData(final @NonNull RestconfStrategy strategy,
            final YangInstanceIdentifier path, final WithDefaultsParam withDefa, final EffectiveModelContext ctx) {
        // PREPARE STATE DATA NODE
        final var stateDataNode = readDataViaTransaction(strategy, LogicalDatastoreType.OPERATIONAL, path);
        // PREPARE CONFIG DATA NODE
        final var configDataNode = readDataViaTransaction(strategy, LogicalDatastoreType.CONFIGURATION, path);

        return mergeConfigAndSTateDataIfNeeded(stateDataNode,
            withDefa == null ? configDataNode : prepareDataByParamWithDef(configDataNode, path, withDefa, ctx));
    }

    /**
     * Read config and state data with selected subtrees that should only be read, then map them.
     * Close {@link DOMTransactionChain} inside of object {@link RestconfStrategy} provided as a parameter.
     *
     * @param strategy {@link RestconfStrategy} - object that perform the actual DS operations
     * @param path     parent path to selected fields
     * @param withDefa with-defaults parameter
     * @param ctx      schema context
     * @param fields   paths to selected subtrees which should be read, relative to to the parent path
     * @return {@link NormalizedNode}
     */
    private static @Nullable NormalizedNode readAllData(final @NonNull RestconfStrategy strategy,
            final @NonNull YangInstanceIdentifier path, final @Nullable WithDefaultsParam withDefa,
            final @NonNull EffectiveModelContext ctx, final @NonNull List<YangInstanceIdentifier> fields) {
        // PREPARE STATE DATA NODE
        final var stateDataNode = readDataViaTransaction(strategy, LogicalDatastoreType.OPERATIONAL, path, fields);
        // PREPARE CONFIG DATA NODE
        final var configDataNode = readDataViaTransaction(strategy, LogicalDatastoreType.CONFIGURATION, path, fields);

        return mergeConfigAndSTateDataIfNeeded(stateDataNode,
            withDefa == null ? configDataNode : prepareDataByParamWithDef(configDataNode, path, withDefa, ctx));
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
        // FIXME: this check is bogus, as it confuses yang.data.api (NormalizedNode) with yang.model.api (RpcDefinition)
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
        final QNameModule moduleOfStateData = stateDataNode.name().getNodeType().getModule();
        final QNameModule moduleOfConfigData = configDataNode.name().getNodeType().getModule();
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
        final var mapEntryBuilder = Builders.mapEntryBuilder()
            .withNodeIdentifier((NodeIdentifierWithPredicates) configDataNode.name());

        // MAP CONFIG DATA
        mapRpcDataNode(configDataNode, mapEntryBuilder);
        // MAP STATE DATA
        mapRpcDataNode(stateDataNode, mapEntryBuilder);

        return Builders.mapBuilder()
            .withNodeIdentifier(NodeIdentifier.create(configDataNode.name().getNodeType()))
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
        if (configDataNode instanceof UserMapNode configMap) {
            final var builder = Builders.orderedMapBuilder().withNodeIdentifier(configMap.name());
            mapValueToBuilder(configMap.body(), ((UserMapNode) stateDataNode).body(), builder);
            return builder.build();
        } else if (configDataNode instanceof SystemMapNode configMap) {
            final var builder = Builders.mapBuilder().withNodeIdentifier(configMap.name());
            mapValueToBuilder(configMap.body(), ((SystemMapNode) stateDataNode).body(), builder);
            return builder.build();
        } else if (configDataNode instanceof MapEntryNode configEntry) {
            final var builder = Builders.mapEntryBuilder().withNodeIdentifier(configEntry.name());
            mapValueToBuilder(configEntry.body(), ((MapEntryNode) stateDataNode).body(), builder);
            return builder.build();
        } else if (configDataNode instanceof ContainerNode configContaienr) {
            final var builder = Builders.containerBuilder().withNodeIdentifier(configContaienr.name());
            mapValueToBuilder(configContaienr.body(), ((ContainerNode) stateDataNode).body(), builder);
            return builder.build();
        } else if (configDataNode instanceof ChoiceNode configChoice) {
            final var builder = Builders.choiceBuilder().withNodeIdentifier(configChoice.name());
            mapValueToBuilder(configChoice.body(), ((ChoiceNode) stateDataNode).body(), builder);
            return builder.build();
        } else if (configDataNode instanceof LeafNode configLeaf) {
            // config trumps oper
            return configLeaf;
        } else if (configDataNode instanceof UserLeafSetNode) {
            final var configLeafSet = (UserLeafSetNode<Object>) configDataNode;
            final var builder = Builders.<Object>orderedLeafSetBuilder().withNodeIdentifier(configLeafSet.name());
            mapValueToBuilder(configLeafSet.body(), ((UserLeafSetNode<Object>) stateDataNode).body(), builder);
            return builder.build();
        } else if (configDataNode instanceof SystemLeafSetNode) {
            final var configLeafSet = (SystemLeafSetNode<Object>) configDataNode;
            final var builder = Builders.<Object>leafSetBuilder().withNodeIdentifier(configLeafSet.name());
            mapValueToBuilder(configLeafSet.body(), ((SystemLeafSetNode<Object>) stateDataNode).body(), builder);
            return builder.build();
        } else if (configDataNode instanceof LeafSetEntryNode<?> configEntry) {
            // config trumps oper
            return configEntry;
        } else if (configDataNode instanceof UnkeyedListNode configList) {
            final var builder = Builders.unkeyedListBuilder().withNodeIdentifier(configList.name());
            mapValueToBuilder(configList.body(), ((UnkeyedListNode) stateDataNode).body(), builder);
            return builder.build();
        } else if (configDataNode instanceof UnkeyedListEntryNode configEntry) {
            final var builder = Builders.unkeyedListEntryBuilder().withNodeIdentifier(configEntry.name());
            mapValueToBuilder(configEntry.body(), ((UnkeyedListEntryNode) stateDataNode).body(), builder);
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
        final var configMap = configData.stream().collect(Collectors.toMap(NormalizedNode::name, Function.identity()));
        final var stateMap = stateData.stream().collect(Collectors.toMap(NormalizedNode::name, Function.identity()));

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
