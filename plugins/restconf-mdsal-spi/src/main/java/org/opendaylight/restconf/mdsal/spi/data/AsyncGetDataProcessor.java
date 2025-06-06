/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.mdsal.spi.data;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Function;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.databind.DatabindPath.Data;
import org.opendaylight.netconf.databind.RequestException;
import org.opendaylight.restconf.api.query.ContentParam;
import org.opendaylight.restconf.api.query.WithDefaultsParam;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.netconf.with.defaults.rev110601.WithDefaultsMode;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
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
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContext;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Asynchronously applies filtering based on {@code with-defaults} parameter in the Future Get data.
 * <a href="https://datatracker.ietf.org/doc/html/rfc6243#page-22">Protocol Operation Examples</a>
 */
public final class AsyncGetDataProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(AsyncGetDataProcessor.class);

    private final Data path;
    private final WithDefaultsParam defaultsMode;

    public AsyncGetDataProcessor(final @NonNull Data path, final @Nullable WithDefaultsParam defaultsMode) {
        this.path = requireNonNull(path);
        this.defaultsMode = defaultsMode;
    }

    /**
     * Applies filtering based on the {@code defaultsMode} attribute to the provided
     * {@link ContentParam#CONFIG} GET request future.
     *
     * @param config CONFIG GET request future.
     * @return ListenableFuture with applied filtering, if possible.
     */
    public ListenableFuture<Optional<NormalizedNode>> config(final ListenableFuture<Optional<NormalizedNode>> config) {
        return Futures.whenAllComplete(config).call(() -> {
            final var configDataNode = Futures.getDone(config).orElse(null);
            if (defaultsMode == null) {
                return Optional.ofNullable(configDataNode);
            }
            return Optional.of(prepareDataByParamWithDef(configDataNode, path, defaultsMode.mode()));
        }, MoreExecutors.directExecutor());
    }

    /**
     * Applies filtering based on the {@code defaultsMode} attribute to the provided
     * {@link ContentParam#NONCONFIG} GET request future.
     *
     * @param nonConfig NON-CONFIG GET request future.
     * @return ListenableFuture with applied filtering, if possible.
     */
    public ListenableFuture<Optional<NormalizedNode>> nonConfig(
            final ListenableFuture<Optional<NormalizedNode>> nonConfig) {
        // FIXME:   When data is retrieved with a <with-defaults> parameter equal to
        //          'trim', data nodes MUST NOT be reported if they contain the schema
        //          default value.  Non-configuration data nodes containing the schema
        //          default value MUST NOT be reported.
        //          https://datatracker.ietf.org/doc/html/rfc6243#section-3.2
        return nonConfig;
    }

    /**
     * Applies filtering based on the {@code defaultsMode} attribute to the provided
     * {@link ContentParam#CONFIG} GET request future and {@link ContentParam#NONCONFIG} GET request future and
     * subsequently merges their data into a single result.
     *
     * @param config CONFIG GET request future.
     * @param nonConfig NON-CONFIG GET request future.
     * @return ListenableFuture with the merged CONFIG and NONCONFIG data, with filtering applied, if possible.
     */
    public ListenableFuture<Optional<NormalizedNode>> all(final ListenableFuture<Optional<NormalizedNode>> config,
            final ListenableFuture<Optional<NormalizedNode>> nonConfig) {
        return Futures.whenAllComplete(config, nonConfig).call(() -> {
            final var configDataNode = Futures.getDone(config).orElse(null);
            final var stateDataNode = Futures.getDone(nonConfig).orElse(null);
            if (defaultsMode == null) {
                return Optional.ofNullable(mergeConfigAndSTateDataIfNeeded(stateDataNode, configDataNode));
            }
            final var configParsedDataNode = prepareDataByParamWithDef(configDataNode, path, defaultsMode.mode());
            return Optional.of(mergeConfigAndSTateDataIfNeeded(stateDataNode, configParsedDataNode));
        }, MoreExecutors.directExecutor());
    }

    private static NormalizedNode prepareDataByParamWithDef(final NormalizedNode readData, final Data path,
            final WithDefaultsMode defaultsMode) throws RequestException {
        final boolean trim = switch (defaultsMode) {
            case Trim -> true;
            case Explicit -> false;
            case ReportAll, ReportAllTagged ->
                throw new RequestException("Unsupported with-defaults value %s", defaultsMode.getName());
        };

        final var ctxNode = path.schema();
        return switch (readData) {
            case ContainerNode container -> {
                final var builder = ImmutableNodes.newContainerBuilder().withNodeIdentifier(container.name());
                buildCont(builder, container.body(), ctxNode, trim);
                yield builder.build();
            }
            case MapEntryNode mapEntry -> {
                if (!(ctxNode.dataSchemaNode() instanceof ListSchemaNode listSchema)) {
                    throw new IllegalStateException("Input " + mapEntry + " does not match " + ctxNode);
                }

                final var builder = ImmutableNodes.newMapEntryBuilder().withNodeIdentifier(mapEntry.name());
                buildMapEntryBuilder(builder, mapEntry.body(), ctxNode, trim, listSchema.getKeyDefinition());
                yield builder.build();
            }
            default -> throw new IllegalStateException("Unhandled data contract " + readData.contract());
        };
    }

    private static NormalizedNode mergeConfigAndSTateDataIfNeeded(final NormalizedNode stateDataNode,
            final NormalizedNode configDataNode) throws RequestException {
        if (stateDataNode == null) {
            // No state, return config
            return configDataNode;
        }
        if (configDataNode == null) {
            // No config, return state
            return stateDataNode;
        }
        // merge config and state
        return mergeStateAndConfigData(stateDataNode, configDataNode);
    }

    private static void buildMapEntryBuilder(
            final DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> builder,
            final Collection<@NonNull DataContainerChild> children, final DataSchemaContext ctxNode,
            final boolean trim, final List<QName> keys) {
        for (var child : children) {
            final var childCtx = getChildContext(ctxNode, child);

            switch (child) {
                case ContainerNode container -> appendContainer(builder, container, childCtx, trim);
                case MapNode map -> appendMap(builder, map, childCtx, trim);
                case LeafNode<?> leaf -> appendLeaf(builder, leaf, childCtx, trim, keys);
                default ->
                    // FIXME: we should never hit this, throw an ISE if this ever happens
                    LOG.debug("Ignoring unhandled child contract {}", child.contract());
            }
        }
    }

    private static void appendContainer(final DataContainerNodeBuilder<?, ?> builder, final ContainerNode container,
            final DataSchemaContext ctxNode, final boolean trim) {
        final var childBuilder = ImmutableNodes.newContainerBuilder().withNodeIdentifier(container.name());
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
            case SYSTEM -> ImmutableNodes.newSystemMapBuilder();
            case USER -> ImmutableNodes.newUserMapBuilder();
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
            final var mapEntryBuilder = ImmutableNodes.newMapEntryBuilder().withNodeIdentifier(entry.name());
            buildMapEntryBuilder(mapEntryBuilder, entry.body(), childCtx, trim, keys);
            builder.withChild(mapEntryBuilder.build());
        }
    }

    private static void buildCont(final DataContainerNodeBuilder<NodeIdentifier, ContainerNode> builder,
            final Collection<DataContainerChild> children, final DataSchemaContext ctxNode, final boolean trim) {
        for (var child : children) {
            final var childCtx = getChildContext(ctxNode, child);
            switch (child) {
                case ContainerNode container -> appendContainer(builder, container, childCtx, trim);
                case MapNode map -> appendMap(builder, map, childCtx, trim);
                case LeafNode<?> leaf -> appendLeaf(builder, leaf, childCtx, trim, List.of());
                default -> LOG.debug("Child contract {} is not supported while building container, ignoring.",
                    child.contract());
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
     * Merge state and config data into a single NormalizedNode.
     *
     * @param stateDataNode  data node of state data
     * @param configDataNode data node of config data
     * @return {@link NormalizedNode}
     */
    private static @NonNull NormalizedNode mergeStateAndConfigData(final @NonNull NormalizedNode stateDataNode,
            final @NonNull NormalizedNode configDataNode) throws RequestException {
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
            final @NonNull NormalizedNode configDataNode) throws RequestException {
        final QNameModule moduleOfStateData = stateDataNode.name().getNodeType().getModule();
        final QNameModule moduleOfConfigData = configDataNode.name().getNodeType().getModule();
        if (!moduleOfStateData.equals(moduleOfConfigData)) {
            throw new RequestException("Unable to merge data from different modules.");
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
        final var mapEntryBuilder = ImmutableNodes.newMapEntryBuilder()
            .withNodeIdentifier((NodeIdentifierWithPredicates) configDataNode.name());

        // MAP CONFIG DATA
        mapRpcDataNode(configDataNode, mapEntryBuilder);
        // MAP STATE DATA
        mapRpcDataNode(stateDataNode, mapEntryBuilder);

        return ImmutableNodes.newSystemMapBuilder()
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
        return switch (configDataNode) {
            case UserMapNode configMap -> {
                final var builder = ImmutableNodes.newUserMapBuilder().withNodeIdentifier(configMap.name());
                mapValueToBuilder(configMap.asMap(), ((UserMapNode) stateDataNode).asMap(), builder);
                yield builder.build();
            }
            case SystemMapNode configMap -> {
                final var builder = ImmutableNodes.newSystemMapBuilder().withNodeIdentifier(configMap.name());
                mapValueToBuilder(configMap.asMap(), ((SystemMapNode) stateDataNode).asMap(), builder);
                yield builder.build();
            }
            case MapEntryNode configEntry -> {
                final var builder = ImmutableNodes.newMapEntryBuilder().withNodeIdentifier(configEntry.name());
                mapValueToBuilder(configEntry.body(), ((MapEntryNode) stateDataNode).body(), builder);
                yield builder.build();
            }
            case ContainerNode configContaienr -> {
                final var builder = ImmutableNodes.newContainerBuilder().withNodeIdentifier(configContaienr.name());
                mapValueToBuilder(configContaienr.body(), ((ContainerNode) stateDataNode).body(), builder);
                yield builder.build();
            }
            case ChoiceNode configChoice -> {
                final var builder = ImmutableNodes.newChoiceBuilder().withNodeIdentifier(configChoice.name());
                mapValueToBuilder(configChoice.body(), ((ChoiceNode) stateDataNode).body(), builder);
                yield builder.build();
            }
            case UserLeafSetNode<?> userLeafSet -> {
                final var configLeafSet = (UserLeafSetNode<Object>) userLeafSet;
                final var builder = ImmutableNodes.newUserLeafSetBuilder().withNodeIdentifier(configLeafSet.name());
                mapValueToBuilder(configLeafSet.body(), ((UserLeafSetNode<Object>) stateDataNode).body(),
                    builder);
                yield builder.build();
            }
            case SystemLeafSetNode<?> systemLeafSet -> {
                final var configLeafSet = (SystemLeafSetNode<Object>) systemLeafSet;
                final var builder = ImmutableNodes.newSystemLeafSetBuilder().withNodeIdentifier(configLeafSet.name());
                mapValueToBuilder(configLeafSet.body(), ((SystemLeafSetNode<Object>) stateDataNode).body(), builder);
                yield builder.build();
            }
            case UnkeyedListNode configList -> {
                final var builder = ImmutableNodes.newUnkeyedListBuilder().withNodeIdentifier(configList.name());
                mapValueToBuilder(configList.body(), ((UnkeyedListNode) stateDataNode).body(), builder);
                yield builder.build();
            }
            case UnkeyedListEntryNode configEntry -> {
                final var builder = ImmutableNodes.newUnkeyedListEntryBuilder().withNodeIdentifier(configEntry.name());
                mapValueToBuilder(configEntry.body(), ((UnkeyedListEntryNode) stateDataNode).body(), builder);
                yield builder.build();
            }
            // config trumps oper
            case LeafNode<?> configLeaf -> configLeaf;
            case LeafSetEntryNode<?> configEntry -> configEntry;
            default -> throw new IllegalStateException("Unexpected node type: " + configDataNode.getClass().getName());
        };
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
        mapValueToBuilder(
            configData.stream().collect(ImmutableMap.toImmutableMap(NormalizedNode::name, Function.identity())),
            stateData.stream().collect(ImmutableMap.toImmutableMap(NormalizedNode::name, Function.identity())),
            builder);
    }

    private static <T extends NormalizedNode> void mapValueToBuilder(
            final @NonNull Map<? extends PathArgument, T> configMap,
            final @NonNull Map<? extends PathArgument, T> stateMap,
            final @NonNull NormalizedNodeContainerBuilder<?, PathArgument, T, ?> builder) {
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
            final @NonNull Map<? extends PathArgument, T> configMap,
            final @NonNull Map<? extends PathArgument, T> stateMap,
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
            final @NonNull Map<? extends PathArgument, T> configMap,
            final @NonNull Map<? extends PathArgument, T> stateMap,
            final @NonNull NormalizedNodeContainerBuilder<?, PathArgument, T, ?> builder) {
        // it is enough to process only config data because operational contains the same data
        configMap.entrySet().stream().filter(x -> stateMap.containsKey(x.getKey())).forEach(
            y -> builder.addChild((T) prepareData(y.getValue(), stateMap.get(y.getKey()))));
    }
}
