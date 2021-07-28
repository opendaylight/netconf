/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.rests.utils;

import static org.opendaylight.restconf.nb.rfc8040.utils.parser.ParserFieldsParameter.parseFieldsParameter;
import static org.opendaylight.restconf.nb.rfc8040.utils.parser.ParserFieldsParameter.parseFieldsPaths;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.primitives.Ints;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.ws.rs.core.MultivaluedMap;
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
import org.opendaylight.restconf.nb.rfc8040.rests.transactions.RestconfStrategy;
import org.opendaylight.restconf.nb.rfc8040.rests.utils.RestconfDataServiceConstant.ReadData.WithDefaults;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
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
import org.opendaylight.yangtools.yang.data.api.schema.SystemLeafSetNode;
import org.opendaylight.yangtools.yang.data.api.schema.SystemMapNode;
import org.opendaylight.yangtools.yang.data.api.schema.UnkeyedListEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.UnkeyedListNode;
import org.opendaylight.yangtools.yang.data.api.schema.UserLeafSetNode;
import org.opendaylight.yangtools.yang.data.api.schema.UserMapNode;
import org.opendaylight.yangtools.yang.data.api.schema.builder.CollectionNodeBuilder;
import org.opendaylight.yangtools.yang.data.api.schema.builder.DataContainerNodeBuilder;
import org.opendaylight.yangtools.yang.data.api.schema.builder.ListNodeBuilder;
import org.opendaylight.yangtools.yang.data.api.schema.builder.NormalizedNodeBuilder;
import org.opendaylight.yangtools.yang.data.api.schema.builder.NormalizedNodeContainerBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.data.impl.schema.SchemaAwareBuilders;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextTree;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
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
    private static final Set<String> ALLOWED_PARAMETERS = Set.of(
        RestconfDataServiceConstant.ReadData.CONTENT,
        RestconfDataServiceConstant.ReadData.DEPTH,
        RestconfDataServiceConstant.ReadData.FIELDS,
        RestconfDataServiceConstant.ReadData.WITH_DEFAULTS);
    private static final List<String> DEFAULT_CONTENT = List.of(RestconfDataServiceConstant.ReadData.ALL);
    private static final List<String> DEFAULT_DEPTH = List.of(RestconfDataServiceConstant.ReadData.UNBOUNDED);
    private static final String READ_TYPE_TX = "READ";

    private ReadDataTransactionUtil() {
        // Hidden on purpose
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
        final MultivaluedMap<String, String> queryParams = uriInfo.getQueryParameters();
        checkParametersTypes(queryParams.keySet(), ALLOWED_PARAMETERS);

        // read parameters from URI or set default values
        final List<String> content = queryParams.getOrDefault(
                RestconfDataServiceConstant.ReadData.CONTENT, DEFAULT_CONTENT);
        final List<String> depth = queryParams.getOrDefault(
                RestconfDataServiceConstant.ReadData.DEPTH, DEFAULT_DEPTH);
        final List<String> withDefaults = queryParams.getOrDefault(
                RestconfDataServiceConstant.ReadData.WITH_DEFAULTS, List.of());
        // fields
        final List<String> fields = queryParams.getOrDefault(RestconfDataServiceConstant.ReadData.FIELDS, List.of());

        // parameter can be in URI at most once
        checkParameterCount(content, RestconfDataServiceConstant.ReadData.CONTENT);
        checkParameterCount(depth, RestconfDataServiceConstant.ReadData.DEPTH);
        checkParameterCount(fields, RestconfDataServiceConstant.ReadData.FIELDS);
        checkParameterCount(withDefaults, RestconfDataServiceConstant.ReadData.WITH_DEFAULTS);

        // check and set content
        final String contentValue = content.get(0);
        switch (contentValue) {
            case RestconfDataServiceConstant.ReadData.ALL:
            case RestconfDataServiceConstant.ReadData.CONFIG:
            case RestconfDataServiceConstant.ReadData.NONCONFIG:
                // FIXME: we really want to have a proper enumeration for this field
                builder.setContent(contentValue);
                break;
            default:
                throw new RestconfDocumentedException(
                    new RestconfError(ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE,
                        "Invalid content parameter: " + contentValue, null,
                        "The content parameter value must be either config, nonconfig or all (default)"));
        }

        // check and set depth
        if (!depth.get(0).equals(RestconfDataServiceConstant.ReadData.UNBOUNDED)) {
            final Integer value = Ints.tryParse(depth.get(0));

            if (value == null || value < RestconfDataServiceConstant.ReadData.MIN_DEPTH
                    || value > RestconfDataServiceConstant.ReadData.MAX_DEPTH) {
                throw new RestconfDocumentedException(
                        new RestconfError(ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE,
                                "Invalid depth parameter: " + depth, null,
                                "The depth parameter must be an integer between 1 and 65535 or \"unbounded\""));
            } else {
                builder.setDepth(value);
            }
        }

        // check and set fields
        if (!fields.isEmpty()) {
            if (identifier.getMountPoint() != null) {
                builder.setFieldPaths(parseFieldsPaths(identifier, fields.get(0)));
            } else {
                builder.setFields(parseFieldsParameter(identifier, fields.get(0)));
            }
        }

        // check and set withDefaults parameter
        if (!withDefaults.isEmpty()) {
            final String str = withDefaults.get(0);
            final WithDefaults val = WithDefaults.forValue(str);
            if (val == null) {
                throw new RestconfDocumentedException(new RestconfError(ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE,
                    "Invalid with-defaults parameter: " + str, null,
                    "The with-defaults parameter must be a string in " + WithDefaults.possibleValues()));
            }

            switch (val) {
                case REPORT_ALL:
                    break;
                case REPORT_ALL_TAGGED:
                    builder.setTagged(true);
                    break;
                default:
                    builder.setWithDefault(val.value());
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
    public static @Nullable NormalizedNode readData(final @NonNull String valueOfContent,
                                                    final @NonNull YangInstanceIdentifier path,
                                                    final @NonNull RestconfStrategy strategy,
                                                    final String withDefa, final EffectiveModelContext ctx) {
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
                throw new RestconfDocumentedException(
                        new RestconfError(ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE,
                                "Invalid content parameter: " + valueOfContent, null,
                                "The content parameter value must be either config, nonconfig or all (default)"));
        }
    }

    /**
     * Read specific type of data from data store via transaction with specified subtrees that should only be read.
     * Close {@link DOMTransactionChain} inside of object {@link RestconfStrategy} provided as a parameter.
     *
     * @param valueOfContent type of data to read (config, state, all)
     * @param path           the parent path to read
     * @param strategy       {@link RestconfStrategy} - object that perform the actual DS operations
     * @param withDefa       value of with-defaults parameter
     * @param ctx            schema context
     * @param fields         paths to selected subtrees which should be read, relative to to the parent path
     * @return {@link NormalizedNode}
     */
    public static @Nullable NormalizedNode readData(final @NonNull String valueOfContent,
            final @NonNull YangInstanceIdentifier path, final @NonNull RestconfStrategy strategy,
            final @Nullable String withDefa, @NonNull final EffectiveModelContext ctx,
            final @NonNull List<YangInstanceIdentifier> fields) {
        switch (valueOfContent) {
            case RestconfDataServiceConstant.ReadData.CONFIG:
                if (withDefa == null) {
                    return readDataViaTransaction(strategy, LogicalDatastoreType.CONFIGURATION, path, true, fields);
                } else {
                    return prepareDataByParamWithDef(
                            readDataViaTransaction(strategy, LogicalDatastoreType.CONFIGURATION, path, true, fields),
                            path, withDefa, ctx);
                }
            case RestconfDataServiceConstant.ReadData.NONCONFIG:
                return readDataViaTransaction(strategy, LogicalDatastoreType.OPERATIONAL, path, true, fields);
            case RestconfDataServiceConstant.ReadData.ALL:
                return readAllData(strategy, path, withDefa, ctx, fields);
            default:
                throw new RestconfDocumentedException(new RestconfError(ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE,
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
     * @param usedParameters parameters used in URI request
     * @param allowedParameters allowed parameters for operation
     */
    @VisibleForTesting
    static void checkParametersTypes(final @NonNull Set<String> usedParameters,
                                     final @NonNull Set<String> allowedParameters) {
        if (!allowedParameters.containsAll(usedParameters)) {
            final Set<String> notAllowedParameters = usedParameters.stream()
                .filter(param -> !allowedParameters.contains(param))
                .collect(Collectors.toSet());
            throw new RestconfDocumentedException(
                "Not allowed parameters for " + READ_TYPE_TX + " operation: " + notAllowedParameters,
                ErrorType.PROTOCOL, ErrorTag.INVALID_VALUE);
        }
    }

    private static NormalizedNode prepareDataByParamWithDef(final NormalizedNode result,
            final YangInstanceIdentifier path, final String withDefa, final EffectiveModelContext ctx) {
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
        final DataSchemaNode baseSchemaNode = baseSchemaCtxTree.findChild(path).orElseThrow().getDataSchemaNode();
        if (result instanceof ContainerNode) {
            final DataContainerNodeBuilder<NodeIdentifier, ContainerNode> builder =
                SchemaAwareBuilders.containerBuilder((ContainerSchemaNode) baseSchemaNode);
            buildCont(builder, (ContainerNode) result, baseSchemaCtxTree, path, trim);
            return builder.build();
        } else {
            final DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> builder =
                SchemaAwareBuilders.mapEntryBuilder((ListSchemaNode) baseSchemaNode);
            buildMapEntryBuilder(builder, (MapEntryNode) result, baseSchemaCtxTree, path, trim,
                    ((ListSchemaNode) baseSchemaNode).getKeyDefinition());
            return builder.build();
        }
    }

    private static void buildMapEntryBuilder(
            final DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> builder,
            final MapEntryNode result, final DataSchemaContextTree baseSchemaCtxTree,
            final YangInstanceIdentifier actualPath, final boolean trim, final List<QName> keys) {
        for (final DataContainerChild child : result.body()) {
            final YangInstanceIdentifier path = actualPath.node(child.getIdentifier());
            final DataSchemaNode childSchema = baseSchemaCtxTree.findChild(path).orElseThrow().getDataSchemaNode();
            if (child instanceof ContainerNode) {
                final DataContainerNodeBuilder<NodeIdentifier, ContainerNode> childBuilder =
                    SchemaAwareBuilders.containerBuilder((ContainerSchemaNode) childSchema);
                buildCont(childBuilder, (ContainerNode) child, baseSchemaCtxTree, path, trim);
                builder.withChild(childBuilder.build());
            } else if (child instanceof MapNode) {
                final CollectionNodeBuilder<MapEntryNode, SystemMapNode> childBuilder =
                    SchemaAwareBuilders.mapBuilder((ListSchemaNode) childSchema);
                buildList(childBuilder, (MapNode) child, baseSchemaCtxTree, path, trim,
                        ((ListSchemaNode) childSchema).getKeyDefinition());
                builder.withChild(childBuilder.build());
            } else if (child instanceof LeafNode) {
                final Object defaultVal = ((LeafSchemaNode) childSchema).getType().getDefaultValue().orElse(null);
                final Object nodeVal = child.body();
                final NormalizedNodeBuilder<NodeIdentifier, Object, LeafNode<Object>> leafBuilder =
                    SchemaAwareBuilders.leafBuilder((LeafSchemaNode) childSchema);
                if (keys.contains(child.getIdentifier().getNodeType())) {
                    leafBuilder.withValue(((LeafNode<?>) child).body());
                    builder.withChild(leafBuilder.build());
                } else {
                    if (trim) {
                        if (defaultVal == null || !defaultVal.equals(nodeVal)) {
                            leafBuilder.withValue(((LeafNode<?>) child).body());
                            builder.withChild(leafBuilder.build());
                        }
                    } else {
                        if (defaultVal != null && defaultVal.equals(nodeVal)) {
                            leafBuilder.withValue(((LeafNode<?>) child).body());
                            builder.withChild(leafBuilder.build());
                        }
                    }
                }
            }
        }
    }

    private static void buildList(final CollectionNodeBuilder<MapEntryNode, SystemMapNode> builder,
            final MapNode result, final DataSchemaContextTree baseSchemaCtxTree, final YangInstanceIdentifier path,
            final boolean trim, final List<QName> keys) {
        for (final MapEntryNode mapEntryNode : result.body()) {
            final YangInstanceIdentifier actualNode = path.node(mapEntryNode.getIdentifier());
            final DataSchemaNode childSchema = baseSchemaCtxTree.findChild(actualNode).orElseThrow()
                    .getDataSchemaNode();
            final DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> mapEntryBuilder =
                SchemaAwareBuilders.mapEntryBuilder((ListSchemaNode) childSchema);
            buildMapEntryBuilder(mapEntryBuilder, mapEntryNode, baseSchemaCtxTree, actualNode, trim, keys);
            builder.withChild(mapEntryBuilder.build());
        }
    }

    private static void buildCont(final DataContainerNodeBuilder<NodeIdentifier, ContainerNode> builder,
            final ContainerNode result, final DataSchemaContextTree baseSchemaCtxTree,
            final YangInstanceIdentifier actualPath, final boolean trim) {
        for (final DataContainerChild child : result.body()) {
            final YangInstanceIdentifier path = actualPath.node(child.getIdentifier());
            final DataSchemaNode childSchema = baseSchemaCtxTree.findChild(path).orElseThrow().getDataSchemaNode();
            if (child instanceof ContainerNode) {
                final DataContainerNodeBuilder<NodeIdentifier, ContainerNode> builderChild =
                    SchemaAwareBuilders.containerBuilder((ContainerSchemaNode) childSchema);
                buildCont(builderChild, result, baseSchemaCtxTree, actualPath, trim);
                builder.withChild(builderChild.build());
            } else if (child instanceof MapNode) {
                final CollectionNodeBuilder<MapEntryNode, SystemMapNode> childBuilder =
                    SchemaAwareBuilders.mapBuilder((ListSchemaNode) childSchema);
                buildList(childBuilder, (MapNode) child, baseSchemaCtxTree, path, trim,
                        ((ListSchemaNode) childSchema).getKeyDefinition());
                builder.withChild(childBuilder.build());
            } else if (child instanceof LeafNode) {
                final Object defaultVal = ((LeafSchemaNode) childSchema).getType().getDefaultValue().orElse(null);
                final Object nodeVal = child.body();
                final NormalizedNodeBuilder<NodeIdentifier, Object, LeafNode<Object>> leafBuilder =
                    SchemaAwareBuilders.leafBuilder((LeafSchemaNode) childSchema);
                if (trim) {
                    if (defaultVal == null || !defaultVal.equals(nodeVal)) {
                        leafBuilder.withValue(((LeafNode<?>) child).body());
                        builder.withChild(leafBuilder.build());
                    }
                } else {
                    if (defaultVal != null && defaultVal.equals(nodeVal)) {
                        leafBuilder.withValue(((LeafNode<?>) child).body());
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
    static @Nullable NormalizedNode readDataViaTransaction(final @NonNull RestconfStrategy strategy,
            final LogicalDatastoreType store, final YangInstanceIdentifier path,
            final boolean closeTransactionChain) {
        final ListenableFuture<Optional<NormalizedNode>> listenableFuture = strategy.read(store, path);
        return extractReadData(strategy, path, closeTransactionChain, listenableFuture);
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
            final boolean closeTransactionChain, final @NonNull List<YangInstanceIdentifier> fields) {
        final ListenableFuture<Optional<NormalizedNode>> listenableFuture = strategy.read(store, path, fields);
        return extractReadData(strategy, path, closeTransactionChain, listenableFuture);
    }

    private static NormalizedNode extractReadData(final RestconfStrategy strategy,
            final YangInstanceIdentifier path, final boolean closeTransactionChain,
            final ListenableFuture<Optional<NormalizedNode>> dataFuture) {
        final NormalizedNodeFactory dataFactory = new NormalizedNodeFactory();
        if (closeTransactionChain) {
            //Method close transactionChain if any
            FutureCallbackTx.addCallback(dataFuture, READ_TYPE_TX, dataFactory, strategy, path);
        } else {
            FutureCallbackTx.addCallback(dataFuture, READ_TYPE_TX, dataFactory);
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
    private static @Nullable NormalizedNode readAllData(final @NonNull RestconfStrategy strategy,
            final YangInstanceIdentifier path, final String withDefa, final EffectiveModelContext ctx) {
        // PREPARE STATE DATA NODE
        final NormalizedNode stateDataNode = readDataViaTransaction(strategy, LogicalDatastoreType.OPERATIONAL, path,
            false);

        // PREPARE CONFIG DATA NODE
        final NormalizedNode configDataNode;
        //Here will be closed transactionChain if any
        if (withDefa == null) {
            configDataNode = readDataViaTransaction(
                    strategy, LogicalDatastoreType.CONFIGURATION, path, true);
        } else {
            configDataNode = prepareDataByParamWithDef(
                    readDataViaTransaction(strategy, LogicalDatastoreType.CONFIGURATION, path, true),
                    path, withDefa, ctx);
        }

        return mergeConfigAndSTateDataIfNeeded(stateDataNode, configDataNode);
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
            final @NonNull YangInstanceIdentifier path, final @Nullable String withDefa,
            final @NonNull EffectiveModelContext ctx, final @NonNull List<YangInstanceIdentifier> fields) {
        // PREPARE STATE DATA NODE
        final NormalizedNode stateDataNode = readDataViaTransaction(strategy, LogicalDatastoreType.OPERATIONAL, path,
            false, fields);

        // PREPARE CONFIG DATA NODE
        final NormalizedNode configDataNode;
        //Here will be closed transactionChain if any
        if (withDefa == null) {
            configDataNode = readDataViaTransaction(strategy, LogicalDatastoreType.CONFIGURATION, path, true, fields);
        } else {
            configDataNode = prepareDataByParamWithDef(
                    readDataViaTransaction(strategy, LogicalDatastoreType.CONFIGURATION, path, true, fields),
                    path, withDefa, ctx);
        }

        return mergeConfigAndSTateDataIfNeeded(stateDataNode, configDataNode);
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
