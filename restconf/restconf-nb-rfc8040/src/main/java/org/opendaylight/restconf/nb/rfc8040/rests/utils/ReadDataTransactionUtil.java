/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.utils;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.ws.rs.core.UriInfo;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMTransactionChain;
import org.opendaylight.restconf.common.context.InstanceIdentifierContext;
import org.opendaylight.restconf.common.context.WriterParameters;
import org.opendaylight.restconf.common.context.WriterParameters.WriterParametersBuilder;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfError;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorTag;
import org.opendaylight.restconf.common.errors.RestconfError.ErrorType;
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.RestconfStrategy;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.ParserFieldsParameter;
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
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.OrderedLeafSetNode;
import org.opendaylight.yangtools.yang.data.api.schema.OrderedMapNode;
import org.opendaylight.yangtools.yang.data.api.schema.UnkeyedListEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.UnkeyedListNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.CollectionNodeBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.DataContainerNodeBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.ListNodeBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.NormalizedNodeBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.NormalizedNodeContainerBuilder;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextTree;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
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
     * @param identifier {@link InstanceIdentifierContext}
     * @param uriInfo    URI info
     * @return {@link WriterParameters}
     */
    public static WriterParameters parseUriParameters(final InstanceIdentifierContext<?> identifier,
                                                      final UriInfo uriInfo) {
        final WriterParametersBuilder builder = new WriterParametersBuilder();

        if (uriInfo == null) {
            return builder.build();
        }

        // check only allowed parameters
        checkParametersTypes(RestconfDataServiceConstant.ReadData.READ_TYPE_TX,
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
        final List<String> withDefaults = uriInfo.getQueryParameters().getOrDefault(
                RestconfDataServiceConstant.ReadData.WITH_DEFAULTS,
                Collections.emptyList());
        // fields
        final List<String> fields = uriInfo.getQueryParameters().getOrDefault(
                RestconfDataServiceConstant.ReadData.FIELDS,
                Collections.emptyList());

        // parameter can be in URI at most once
        checkParameterCount(content, RestconfDataServiceConstant.ReadData.CONTENT);
        checkParameterCount(depth, RestconfDataServiceConstant.ReadData.DEPTH);
        checkParameterCount(fields, RestconfDataServiceConstant.ReadData.FIELDS);
        checkParameterCount(fields, RestconfDataServiceConstant.ReadData.WITH_DEFAULTS);

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

            if (value == null || value < RestconfDataServiceConstant.ReadData.MIN_DEPTH
                    || value > RestconfDataServiceConstant.ReadData.MAX_DEPTH) {
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

        // check and set withDefaults parameter
        if (!withDefaults.isEmpty()) {
            switch (withDefaults.get(0)) {
                case RestconfDataServiceConstant.ReadData.REPORT_ALL_TAGGED_DEFAULT_VALUE:
                    builder.setTagged(true);
                    break;
                case RestconfDataServiceConstant.ReadData.REPORT_ALL_DEFAULT_VALUE:
                    break;
                default:
                    builder.setWithDefault(withDefaults.get(0));
            }
        }
        return builder.build();
    }

    /**
     * Read specific type of data from data store via transaction. Close {@link DOMTransactionChain} if any
     * inside of object {@link RestconfStrategy} provided as a parameter.
     *
     * @param valueOfContent type of data to read (config, state, all)
     * @param path           the path to read
     * @param strategy       {@link RestconfStrategy} - object that perform the actual DS operations
     * @param withDefa       value of with-defaults parameter
     * @param ctx            schema context
     * @return {@link NormalizedNode}
     */
    public static @Nullable NormalizedNode<?, ?> readData(final @NonNull String valueOfContent,
                                                          final @NonNull YangInstanceIdentifier path,
                                                          final @NonNull RestconfStrategy strategy,
                                                          final String withDefa, final SchemaContext ctx) {
        switch (valueOfContent) {
            case RestconfDataServiceConstant.ReadData.CONFIG:
                if (withDefa == null) {
                    return readDataViaTransaction(strategy, LogicalDatastoreType.CONFIGURATION, path, true);
                } else {
                    return prepareDataByParamWithDef(
                            readDataViaTransaction(strategy, LogicalDatastoreType.CONFIGURATION, path, true),
                            path, withDefa, ctx);
                }
            case RestconfDataServiceConstant.ReadData.NONCONFIG:
                return readDataViaTransaction(strategy, LogicalDatastoreType.OPERATIONAL, path, true);
            case RestconfDataServiceConstant.ReadData.ALL:
                return readAllData(strategy, path, withDefa, ctx);
            default:
                strategy.cancel();
                throw new RestconfDocumentedException(
                        new RestconfError(RestconfError.ErrorType.PROTOCOL, RestconfError.ErrorTag.INVALID_VALUE,
                                "Invalid content parameter: " + valueOfContent, null,
                                "The content parameter value must be either config, nonconfig or all (default)"));
        }
    }


    /**
     * Check if URI does not contain value for the same parameter more than once.
     *
     * @param parameterValues URI parameter values
     * @param parameterName URI parameter name
     */
    @VisibleForTesting
    static void checkParameterCount(final @NonNull List<String> parameterValues, final @NonNull String parameterName) {
        if (parameterValues.size() > 1) {
            throw new RestconfDocumentedException(
                    "Parameter " + parameterName + " can appear at most once in request URI",
                    ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
        }
    }

    /**
     * Check if URI does not contain not allowed parameters for specified operation.
     *
     * @param operationType type of operation (READ, POST, PUT, DELETE...)
     * @param usedParameters parameters used in URI request
     * @param allowedParameters allowed parameters for operation
     */
    @VisibleForTesting
    static void checkParametersTypes(final @NonNull String operationType,
                                     final @NonNull Set<String> usedParameters,
                                     final @NonNull String... allowedParameters) {
        // FIXME: there should be a speedier way to do this
        final Set<String> notAllowedParameters = Sets.newHashSet(usedParameters);
        notAllowedParameters.removeAll(Sets.newHashSet(allowedParameters));

        if (!notAllowedParameters.isEmpty()) {
            throw new RestconfDocumentedException(
                    "Not allowed parameters for " + operationType + " operation: " + notAllowedParameters,
                    RestconfError.ErrorType.PROTOCOL,
                    RestconfError.ErrorTag.INVALID_VALUE);
        }
    }

    private static NormalizedNode<?, ?> prepareDataByParamWithDef(final NormalizedNode<?, ?> result,
            final YangInstanceIdentifier path, final String withDefa, final SchemaContext ctx) {
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

        final DataSchemaContextTree baseSchemaCtxTree = DataSchemaContextTree.from(ctx);
        final DataSchemaNode baseSchemaNode = baseSchemaCtxTree.getChild(path).getDataSchemaNode();
        if (result instanceof ContainerNode) {
            final DataContainerNodeBuilder<NodeIdentifier, ContainerNode> builder =
                    Builders.containerBuilder((ContainerSchemaNode) baseSchemaNode);
            buildCont(builder, (ContainerNode) result, baseSchemaCtxTree, path, trim);
            return builder.build();
        } else {
            final DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> builder =
                    Builders.mapEntryBuilder((ListSchemaNode) baseSchemaNode);
            buildMapEntryBuilder(builder, (MapEntryNode) result, baseSchemaCtxTree, path, trim,
                    ((ListSchemaNode) baseSchemaNode).getKeyDefinition());
            return builder.build();
        }
    }

    private static void buildMapEntryBuilder(
            final DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> builder,
            final MapEntryNode result, final DataSchemaContextTree baseSchemaCtxTree,
            final YangInstanceIdentifier actualPath, final boolean trim, final List<QName> keys) {
        for (final DataContainerChild<? extends PathArgument, ?> child : result.getValue()) {
            final YangInstanceIdentifier path = actualPath.node(child.getIdentifier());
            final DataSchemaNode childSchema = baseSchemaCtxTree.getChild(path).getDataSchemaNode();
            if (child instanceof ContainerNode) {
                final DataContainerNodeBuilder<NodeIdentifier, ContainerNode> childBuilder =
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
                final Object defaultVal = ((LeafSchemaNode) childSchema).getType().getDefaultValue().orElse(null);
                final Object nodeVal = child.getValue();
                final NormalizedNodeBuilder<NodeIdentifier, Object, LeafNode<Object>> leafBuilder =
                        Builders.leafBuilder((LeafSchemaNode) childSchema);
                if (keys.contains(child.getNodeType())) {
                    leafBuilder.withValue(((LeafNode<?>) child).getValue());
                    builder.withChild(leafBuilder.build());
                } else {
                    if (trim) {
                        if (defaultVal == null || !defaultVal.equals(nodeVal)) {
                            leafBuilder.withValue(((LeafNode<?>) child).getValue());
                            builder.withChild(leafBuilder.build());
                        }
                    } else {
                        if (defaultVal != null && defaultVal.equals(nodeVal)) {
                            leafBuilder.withValue(((LeafNode<?>) child).getValue());
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
            final DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> mapEntryBuilder =
                    Builders.mapEntryBuilder((ListSchemaNode) childSchema);
            buildMapEntryBuilder(mapEntryBuilder, mapEntryNode, baseSchemaCtxTree, actualNode, trim, keys);
            builder.withChild(mapEntryBuilder.build());
        }
    }

    private static void buildCont(final DataContainerNodeBuilder<NodeIdentifier, ContainerNode> builder,
            final ContainerNode result, final DataSchemaContextTree baseSchemaCtxTree,
            final YangInstanceIdentifier actualPath, final boolean trim) {
        for (final DataContainerChild<? extends PathArgument, ?> child : result.getValue()) {
            final YangInstanceIdentifier path = actualPath.node(child.getIdentifier());
            final DataSchemaNode childSchema = baseSchemaCtxTree.getChild(path).getDataSchemaNode();
            if (child instanceof ContainerNode) {
                final DataContainerNodeBuilder<NodeIdentifier, ContainerNode> builderChild =
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
                final Object defaultVal = ((LeafSchemaNode) childSchema).getType().getDefaultValue().orElse(null);
                final Object nodeVal = child.getValue();
                final NormalizedNodeBuilder<NodeIdentifier, Object, LeafNode<Object>> leafBuilder =
                        Builders.leafBuilder((LeafSchemaNode) childSchema);
                if (trim) {
                    if (defaultVal == null || !defaultVal.equals(nodeVal)) {
                        leafBuilder.withValue(((LeafNode<?>) child).getValue());
                        builder.withChild(leafBuilder.build());
                    }
                } else {
                    if (defaultVal != null && defaultVal.equals(nodeVal)) {
                        leafBuilder.withValue(((LeafNode<?>) child).getValue());
                        builder.withChild(leafBuilder.build());
                    }
                }
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
    private static @Nullable NormalizedNode<?, ?> readDataViaTransaction(
            final @NonNull RestconfStrategy strategy,
            final LogicalDatastoreType store, final YangInstanceIdentifier path,
            final boolean closeTransactionChain) {
        final NormalizedNodeFactory dataFactory = new NormalizedNodeFactory();
        final ListenableFuture<Optional<NormalizedNode<?, ?>>> listenableFuture = strategy.read(store, path);
        if (closeTransactionChain) {
            //Method close transactionChain if any
            FutureCallbackTx.addCallback(listenableFuture, RestconfDataServiceConstant.ReadData.READ_TYPE_TX,
                    dataFactory, strategy.getTransactionChain());
        } else {
            FutureCallbackTx.addCallback(listenableFuture, RestconfDataServiceConstant.ReadData.READ_TYPE_TX,
                    dataFactory);
        }
        return dataFactory.build();
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
    private static @Nullable NormalizedNode<?, ?> readAllData(final @NonNull RestconfStrategy strategy,
            final YangInstanceIdentifier path, final String withDefa, final SchemaContext ctx) {
        // PREPARE STATE DATA NODE
        final NormalizedNode<?, ?> stateDataNode = readDataViaTransaction(
                strategy, LogicalDatastoreType.OPERATIONAL, path, false);

        // PREPARE CONFIG DATA NODE
        final NormalizedNode<?, ?> configDataNode;
        //Here will be closed transactionChain if any
        if (withDefa == null) {
            configDataNode = readDataViaTransaction(
                    strategy, LogicalDatastoreType.CONFIGURATION, path, true);
        } else {
            configDataNode = prepareDataByParamWithDef(
                    readDataViaTransaction(strategy, LogicalDatastoreType.CONFIGURATION, path, true),
                    path, withDefa, ctx);
        }

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
    private static @NonNull NormalizedNode<?, ?> mergeStateAndConfigData(
            final @NonNull NormalizedNode<?, ?> stateDataNode, final @NonNull NormalizedNode<?, ?> configDataNode) {
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
    private static void validateNodeMerge(final @NonNull NormalizedNode<?, ?> stateDataNode,
                                          final @NonNull NormalizedNode<?, ?> configDataNode) {
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
    private static @NonNull NormalizedNode<?, ?> prepareRpcData(final @NonNull NormalizedNode<?, ?> configDataNode,
                                                                final @NonNull NormalizedNode<?, ?> stateDataNode) {
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
     * @param dataNode        data node
     * @param mapEntryBuilder builder for mapping data
     */
    private static void mapRpcDataNode(final @NonNull NormalizedNode<?, ?> dataNode,
            final @NonNull DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> mapEntryBuilder) {
        ((ContainerNode) dataNode).getValue().forEach(mapEntryBuilder::addChild);
    }

    /**
     * Prepare and map all data from DS.
     *
     * @param configDataNode data node of config data
     * @param stateDataNode  data node of state data
     * @return {@link NormalizedNode}
     */
    @SuppressWarnings("unchecked")
    private static @NonNull NormalizedNode<?, ?> prepareData(final @NonNull NormalizedNode<?, ?> configDataNode,
                                                             final @NonNull NormalizedNode<?, ?> stateDataNode) {
        if (configDataNode instanceof OrderedMapNode) {
            final CollectionNodeBuilder<MapEntryNode, OrderedMapNode> builder = Builders
                    .orderedMapBuilder().withNodeIdentifier(((MapNode) configDataNode).getIdentifier());

            mapValueToBuilder(
                    ((OrderedMapNode) configDataNode).getValue(), ((OrderedMapNode) stateDataNode).getValue(), builder);

            return builder.build();
        } else if (configDataNode instanceof MapNode) {
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
            final DataContainerNodeBuilder<NodeIdentifier, ContainerNode> builder = Builders
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
        } else if (configDataNode instanceof OrderedLeafSetNode) {
            final ListNodeBuilder<Object, LeafSetEntryNode<Object>> builder = Builders
                .orderedLeafSetBuilder().withNodeIdentifier(((OrderedLeafSetNode<?>) configDataNode).getIdentifier());

            mapValueToBuilder(((OrderedLeafSetNode<Object>) configDataNode).getValue(),
                    ((OrderedLeafSetNode<Object>) stateDataNode).getValue(), builder);
            return builder.build();
        } else if (configDataNode instanceof LeafSetNode) {
            final ListNodeBuilder<Object, LeafSetEntryNode<Object>> builder = Builders
                    .leafSetBuilder().withNodeIdentifier(((LeafSetNode<?>) configDataNode).getIdentifier());

            mapValueToBuilder(((LeafSetNode<Object>) configDataNode).getValue(),
                    ((LeafSetNode<Object>) stateDataNode).getValue(), builder);
            return builder.build();
        } else if (configDataNode instanceof LeafSetEntryNode) {
            return Builders.leafSetEntryBuilder()
                    .withNodeIdentifier(((LeafSetEntryNode<?>) configDataNode).getIdentifier())
                    .withValue(configDataNode.getValue())
                    .build();
        } else if (configDataNode instanceof UnkeyedListNode) {
            final CollectionNodeBuilder<UnkeyedListEntryNode, UnkeyedListNode> builder = Builders
                    .unkeyedListBuilder().withNodeIdentifier(((UnkeyedListNode) configDataNode).getIdentifier());

            mapValueToBuilder(((UnkeyedListNode) configDataNode).getValue(),
                    ((UnkeyedListNode) stateDataNode).getValue(), builder);
            return builder.build();
        } else if (configDataNode instanceof UnkeyedListEntryNode) {
            final DataContainerNodeBuilder<NodeIdentifier, UnkeyedListEntryNode> builder = Builders
                .unkeyedListEntryBuilder().withNodeIdentifier(((UnkeyedListEntryNode) configDataNode).getIdentifier());

            mapValueToBuilder(((UnkeyedListEntryNode) configDataNode).getValue(),
                    ((UnkeyedListEntryNode) stateDataNode).getValue(), builder);
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
    private static <T extends NormalizedNode<? extends PathArgument, ?>> void mapValueToBuilder(
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
    private static <T extends NormalizedNode<? extends PathArgument, ?>> void mapDataToBuilder(
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
    private static <T extends NormalizedNode<? extends PathArgument, ?>> void mergeDataToBuilder(
            final @NonNull Map<PathArgument, T> configMap, final @NonNull Map<PathArgument, T> stateMap,
            final @NonNull NormalizedNodeContainerBuilder<?, PathArgument, T, ?> builder) {
        // it is enough to process only config data because operational contains the same data
        configMap.entrySet().stream().filter(x -> stateMap.containsKey(x.getKey())).forEach(
            y -> builder.addChild((T) prepareData(y.getValue(), stateMap.get(y.getKey()))));
    }
}
