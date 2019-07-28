/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.utils.mapping;

import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.Rfc8040.IetfYangLibrary;
import org.opendaylight.restconf.nb.rfc8040.Rfc8040.MonitoringModule;
import org.opendaylight.restconf.nb.rfc8040.Rfc8040.MonitoringModule.QueryParams;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.ParserIdentifier;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.SchemaPathCodec;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev160621.module.list.Module.ConformanceType;
import org.opendaylight.yang.gen.v1.urn.sal.restconf.event.subscription.rev140708.NotificationOutputTypeGrouping.NotificationOutputType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.OrderedMapNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.CollectionNodeBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.DataContainerNodeBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.ListNodeBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.impl.ImmutableContainerNodeBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.impl.ImmutableLeafNodeBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.impl.ImmutableMapEntryNodeBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.impl.ImmutableMapNodeBuilder;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Deviation;
import org.opendaylight.yangtools.yang.model.api.FeatureDefinition;
import org.opendaylight.yangtools.yang.model.api.GroupingDefinition;
import org.opendaylight.yangtools.yang.model.api.LeafListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.NotificationDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;

/**
 * Util class for mapping nodes.
 *
 */
public final class RestconfMappingNodeUtil {

    /**
     * Access information bounded to one created stream - stream location on the server and output format of data.
     */
    public static final class StreamAccessMonitoringData {
        private final URI streamLocation;
        private final NotificationOutputType outputType;

        /**
         * Creation of the stream access information.
         *
         * @param streamLocation Stream location in form of URI path.
         * @param outputType     Output format of data (XML or JSON).
         */
        public StreamAccessMonitoringData(final URI streamLocation, final NotificationOutputType outputType) {
            this.streamLocation = streamLocation;
            this.outputType = outputType;
        }
    }

    private RestconfMappingNodeUtil() {
        throw new UnsupportedOperationException("Util class");
    }

    /**
     * Map data from modules to {@link NormalizedNode}.
     *
     * @param modules
     *             modules for mapping
     * @param ietfYangLibraryModule
     *             ietf-yang-library module
     * @param context
     *             schema context
     * @param moduleSetId
     *             module-set-id of actual set
     * @return mapped data as {@link NormalizedNode}
     */
    public static NormalizedNode<NodeIdentifier, Collection<DataContainerChild<? extends PathArgument, ?>>>
            mapModulesByIetfYangLibraryYang(final Set<Module> modules, final Module ietfYangLibraryModule,
                    final SchemaContext context, final String moduleSetId) {
        final DataSchemaNode modulesStateSch =
                ietfYangLibraryModule.getDataChildByName(IetfYangLibrary.MODUELS_STATE_CONT_QNAME);
        final DataContainerNodeBuilder<NodeIdentifier, ContainerNode> modulesStateBuilder =
                Builders.containerBuilder((ContainerSchemaNode) modulesStateSch);

        final DataSchemaNode moduleSetIdSch =
                ((ContainerSchemaNode) modulesStateSch).getDataChildByName(IetfYangLibrary.MODULE_SET_ID_LEAF_QNAME);
        modulesStateBuilder
                .withChild(Builders.leafBuilder((LeafSchemaNode) moduleSetIdSch).withValue(moduleSetId).build());

        final DataSchemaNode moduleSch = findNodeInGroupings(IetfYangLibrary.MODULE_QNAME_LIST, ietfYangLibraryModule);
        final CollectionNodeBuilder<MapEntryNode, OrderedMapNode> mapBuilder =
                Builders.orderedMapBuilder((ListSchemaNode) moduleSch);
        for (final Module module : context.getModules()) {
            fillMapByModules(mapBuilder, moduleSch, false, module, ietfYangLibraryModule, context);
        }
        return modulesStateBuilder.withChild(mapBuilder.build()).build();
    }

    /**
     * Map data by the specific module or submodule.
     *
     * @param mapBuilder
     *             ordered list builder for children
     * @param moduleSch
     *             schema of list for entryMapBuilder
     * @param isSubmodule
     *             true if module is specified as submodule, false otherwise
     * @param module
     *             specific module or submodule
     * @param ietfYangLibraryModule
     *             ietf-yang-library module
     * @param context
     *             schema context
     */
    private static void fillMapByModules(final CollectionNodeBuilder<MapEntryNode, OrderedMapNode> mapBuilder,
            final DataSchemaNode moduleSch, final boolean isSubmodule, final Module module,
            final Module ietfYangLibraryModule, final SchemaContext context) {
        final DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> mapEntryBuilder =
                Builders.mapEntryBuilder((ListSchemaNode) moduleSch);
        addCommonLeafs(module, mapEntryBuilder, ietfYangLibraryModule);
        addChildOfModuleBySpecificModuleInternal(
                IetfYangLibrary.SPECIFIC_MODULE_SCHEMA_LEAF_QNAME, mapEntryBuilder, IetfYangLibrary.BASE_URI_OF_SCHEMA
                        + module.getName() + "/"
                        + module.getQNameModule().getRevision().map(Revision::toString).orElse(null),
                ietfYangLibraryModule);
        if (!isSubmodule) {
            addChildOfModuleBySpecificModuleOfListChild(IetfYangLibrary.SPECIFIC_MODULE_NAMESPACE_LEAF_QNAME,
                    mapEntryBuilder, module.getNamespace().toString(), ietfYangLibraryModule);

            // features - not mandatory
            if (module.getFeatures() != null && !module.getFeatures().isEmpty()) {
                addFeatureLeafList(IetfYangLibrary.SPECIFIC_MODULE_FEATURE_LEAF_LIST_QNAME, mapEntryBuilder,
                        module.getFeatures(), ietfYangLibraryModule);
            }
            // deviations - not mandatory
            if (module.getDeviations() != null && !module.getDeviations().isEmpty()) {
                addDeviationList(module, mapEntryBuilder, ietfYangLibraryModule, context);
                addChildOfModuleBySpecificModuleOfListChild(IetfYangLibrary.SPECIFIC_MODULE_CONFORMANCE_LEAF_QNAME,
                        mapEntryBuilder, ConformanceType.Implement.getName(), ietfYangLibraryModule);
            } else {
                addChildOfModuleBySpecificModuleOfListChild(IetfYangLibrary.SPECIFIC_MODULE_CONFORMANCE_LEAF_QNAME,
                        mapEntryBuilder, ConformanceType.Import.getName(), ietfYangLibraryModule);
            }
            // submodules - not mandatory
            if (module.getSubmodules() != null && !module.getSubmodules().isEmpty()) {
                addSubmodules(module, mapEntryBuilder, ietfYangLibraryModule, context);
            }
        }
        mapBuilder.withChild(mapEntryBuilder.build());
    }

    /**
     * Mapping submodules of specific module.
     *
     * @param module
     *             module with submodules
     * @param mapEntryBuilder
     *             mapEntryBuilder of parent for mapping children
     * @param ietfYangLibraryModule
     *             ietf-yang-library module
     * @param context
     *             schema context
     */
    private static void addSubmodules(final Module module,
            final DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> mapEntryBuilder,
            final Module ietfYangLibraryModule, final SchemaContext context) {
        final DataSchemaNode listSubm = findSchemaInListOfModulesSchema(
                IetfYangLibrary.SPECIFIC_MODULE_SUBMODULE_LIST_QNAME, ietfYangLibraryModule);
        final CollectionNodeBuilder<MapEntryNode, OrderedMapNode> mapBuilder =
                Builders.orderedMapBuilder((ListSchemaNode) listSubm);
        for (final Module submodule : module.getSubmodules()) {
            fillMapByModules(mapBuilder, listSubm, true, submodule, ietfYangLibraryModule, context);
        }
        mapEntryBuilder.withChild(mapBuilder.build());
    }

    /**
     * Mapping deviations of specific module.
     *
     * @param module
     *             module with deviations
     * @param mapEntryBuilder
     *             mapEntryBuilder of parent for mapping children
     * @param ietfYangLibraryModule
     *             ietf-yang-library module
     * @param context
     *             schema context
     */
    private static void addDeviationList(final Module module,
            final DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> mapEntryBuilder,
            final Module ietfYangLibraryModule, final SchemaContext context) {
        final DataSchemaNode deviationsSchema = findSchemaInListOfModulesSchema(
                IetfYangLibrary.SPECIFIC_MODULE_DEVIATION_LIST_QNAME, ietfYangLibraryModule);
        final CollectionNodeBuilder<MapEntryNode, MapNode> deviations =
                Builders.mapBuilder((ListSchemaNode) deviationsSchema);
        for (final Deviation deviation : module.getDeviations()) {
            final DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> deviationEntryNode =
                    Builders.mapEntryBuilder((ListSchemaNode) deviationsSchema);
            final QName lastComponent = deviation.getTargetPath().getLastComponent();
            addChildOfModuleBySpecificModuleInternal(IetfYangLibrary.SPECIFIC_MODULE_NAME_LEAF_QNAME,
                    deviationEntryNode, context.findModule(lastComponent.getModule()).get().getName(),
                    ietfYangLibraryModule);
            if (lastComponent.getRevision().isPresent()) {
                addChildOfModuleBySpecificModuleInternal(IetfYangLibrary.SPECIFIC_MODULE_REVISION_LEAF_QNAME,
                        deviationEntryNode, lastComponent.getRevision(),
                        ietfYangLibraryModule);
            }
            deviations.withChild(deviationEntryNode.build());
        }
        mapEntryBuilder.withChild(deviations.build());
    }

    /**
     * Mapping features of specific module.
     *
     * @param qnameOfFeaturesLeafList
     *             qname of feature leaf-list in ietf-yang-library module
     * @param mapEntryBuilder
     *             mapEntryBuilder of parent for mapping children
     * @param features
     *             features of specific module
     * @param ietfYangLibraryModule
     *             ieat-yang-library module
     */
    private static void addFeatureLeafList(final QName qnameOfFeaturesLeafList,
            final DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> mapEntryBuilder,
            final Set<FeatureDefinition> features, final Module ietfYangLibraryModule) {
        final DataSchemaNode schemaNode =
                findSchemaInListOfModulesSchema(qnameOfFeaturesLeafList, ietfYangLibraryModule);
        final ListNodeBuilder<Object, LeafSetEntryNode<Object>> leafSetBuilder =
                Builders.leafSetBuilder((LeafListSchemaNode) schemaNode);
        for (final FeatureDefinition feature : features) {
            leafSetBuilder.withChild(Builders.leafSetEntryBuilder((LeafListSchemaNode) schemaNode)
                    .withValue(feature.getQName().getLocalName()).build());
        }
        mapEntryBuilder.withChild(leafSetBuilder.build());
    }

    /**
     * Mapping common leafs (grouping common-leafs in ietf-yang-library) of
     * specific module.
     *
     * @param module
     *             specific module for getting name and revision
     * @param mapEntryBuilder
     *             mapEntryBuilder of parent for mapping children
     * @param ietfYangLibraryModule
     *             ietf-yang-library module
     */
    private static void addCommonLeafs(final Module module,
            final DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> mapEntryBuilder,
            final Module ietfYangLibraryModule) {
        addChildOfModuleBySpecificModuleInternal(IetfYangLibrary.SPECIFIC_MODULE_NAME_LEAF_QNAME, mapEntryBuilder,
                module.getName(), ietfYangLibraryModule);
        addChildOfModuleBySpecificModuleInternal(IetfYangLibrary.SPECIFIC_MODULE_REVISION_LEAF_QNAME, mapEntryBuilder,
                module.getQNameModule().getRevision().map(Revision::toString).orElse(""), ietfYangLibraryModule);
    }

    /**
     * Mapping data child of grouping module-list by ietf-yang-library.
     *
     * @param specificQName
     *             qname of leaf in module-list grouping
     * @param mapEntryBuilder
     *             mapEntryBuilder of parent for mapping children
     * @param value
     *             value of leaf
     * @param ietfYangLibraryModule
     *             ietf-yang-library module
     */
    private static void addChildOfModuleBySpecificModuleOfListChild(final QName specificQName,
            final DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> mapEntryBuilder,
            final Object value, final Module ietfYangLibraryModule) {
        final DataSchemaNode leafSch = findSchemaInListOfModulesSchema(specificQName, ietfYangLibraryModule);
        mapEntryBuilder.withChild(Builders.leafBuilder((LeafSchemaNode) leafSch).withValue(value).build());
    }

    /**
     * Find specific schema in gourping module-lsit.
     *
     * @param specificQName
     *             qname of schema
     * @param ietfYangLibraryModule
     *             ietf-yang-library module
     * @return schemaNode of specific child
     */
    private static DataSchemaNode findSchemaInListOfModulesSchema(final QName specificQName,
            final Module ietfYangLibraryModule) {
        for (final GroupingDefinition groupingDefinition : ietfYangLibraryModule.getGroupings()) {
            if (groupingDefinition.getQName().equals(IetfYangLibrary.GROUPING_MODULE_LIST_QNAME)) {
                final DataSchemaNode dataChildByName =
                        groupingDefinition.getDataChildByName(IetfYangLibrary.MODULE_QNAME_LIST);
                return ((ListSchemaNode) dataChildByName).getDataChildByName(specificQName);
            }
        }
        throw new RestconfDocumentedException(specificQName.getLocalName() + " doesn't exist.");
    }

    /**
     * Mapping data child of internal groupings in module-list grouping.
     *
     * @param specifiLeafQName
     *             qnmae of leaf for mapping
     * @param mapEntryBuilder
     *             mapEntryBuilder of parent for mapping children
     * @param value
     *             value of leaf
     * @param ietfYangLibraryModule
     *             ietf-yang-library module
     */
    private static void addChildOfModuleBySpecificModuleInternal(final QName specifiLeafQName,
            final DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> mapEntryBuilder,
            final Object value, final Module ietfYangLibraryModule) {
        final DataSchemaNode nameLeaf = findNodeInInternGroupings(specifiLeafQName, ietfYangLibraryModule);
        mapEntryBuilder.withChild(Builders.leafBuilder((LeafSchemaNode) nameLeaf).withValue(value).build());
    }

    /**
     * Find schema node of leaf by qname in internal groupings of module-list.
     * grouping
     *
     * @param qnameOfSchema
     *             qname of leaf
     * @param ietfYangLibraryModule
     *             ietf-yang-library module
     * @return schema node of specific leaf
     */
    private static DataSchemaNode findNodeInInternGroupings(final QName qnameOfSchema,
            final Module ietfYangLibraryModule) {
        for (final GroupingDefinition groupingDefinition : ietfYangLibraryModule.getGroupings()) {
            if (groupingDefinition.getQName().equals(IetfYangLibrary.GROUPING_MODULE_LIST_QNAME)) {
                for (final GroupingDefinition internalGrouping : groupingDefinition.getGroupings()) {
                    if (internalGrouping.getDataChildByName(qnameOfSchema) != null) {
                        return internalGrouping.getDataChildByName(qnameOfSchema);
                    }
                }
            }
        }
        throw new RestconfDocumentedException(qnameOfSchema.getLocalName() + " doesn't exist.");
    }

    /**
     * Find schema of specific leaf in list-module grouping.
     *
     * @param qnameOfSchema
     *             qname of leaf
     * @param ietfYangLibraryModule
     *             ietf-yang-library module
     * @return schemaNode of specific leaf
     */
    private static DataSchemaNode findNodeInGroupings(final QName qnameOfSchema, final Module ietfYangLibraryModule) {
        for (final GroupingDefinition groupingDefinition : ietfYangLibraryModule.getGroupings()) {
            if (groupingDefinition.getDataChildByName(qnameOfSchema) != null) {
                return groupingDefinition.getDataChildByName(qnameOfSchema);
            }
        }
        throw new RestconfDocumentedException(qnameOfSchema.getLocalName() + " doesn't exist.");
    }

    /**
     * Map capabilites by ietf-restconf-monitoring.
     *
     * @param monitoringModule
     *             ietf-restconf-monitoring module
     * @return mapped capabilites
     */
    public static NormalizedNode<NodeIdentifier, Collection<DataContainerChild<? extends PathArgument, ?>>>
            mapCapabilites(final Module monitoringModule) {
        final DataSchemaNode restconfState =
                monitoringModule.getDataChildByName(MonitoringModule.CONT_RESTCONF_STATE_QNAME);
        final DataContainerNodeBuilder<NodeIdentifier, ContainerNode> restStateContBuilder =
                Builders.containerBuilder((ContainerSchemaNode) restconfState);
        final DataSchemaNode capabilitesContSchema =
                getChildOfCont((ContainerSchemaNode) restconfState, MonitoringModule.CONT_CAPABILITES_QNAME);
        final DataContainerNodeBuilder<NodeIdentifier, ContainerNode> capabilitesContBuilder =
                Builders.containerBuilder((ContainerSchemaNode) capabilitesContSchema);
        final DataSchemaNode leafListCapa = getChildOfCont((ContainerSchemaNode) capabilitesContSchema,
                MonitoringModule.LEAF_LIST_CAPABILITY_QNAME);
        final ListNodeBuilder<Object, LeafSetEntryNode<Object>> leafListCapaBuilder =
                Builders.orderedLeafSetBuilder((LeafListSchemaNode) leafListCapa);
        fillLeafListCapa(leafListCapaBuilder, (LeafListSchemaNode) leafListCapa);

        return restStateContBuilder.withChild(capabilitesContBuilder.withChild(leafListCapaBuilder.build()).build())
                .build();
    }

    /**
     * Map data to leaf-list.
     *
     * @param builder
     *             builder of parent for children
     * @param leafListSchema
     *             leaf list schema
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void fillLeafListCapa(final ListNodeBuilder builder, final LeafListSchemaNode leafListSchema) {
        builder.withChild(leafListEntryBuild(leafListSchema, QueryParams.DEPTH));
        builder.withChild(leafListEntryBuild(leafListSchema, QueryParams.FIELDS));
        builder.withChild(leafListEntryBuild(leafListSchema, QueryParams.FILTER));
        builder.withChild(leafListEntryBuild(leafListSchema, QueryParams.REPLAY));
        builder.withChild(leafListEntryBuild(leafListSchema, QueryParams.WITH_DEFAULTS));
    }

    /**
     * Map value to leaf list entry node.
     *
     * @param leafListSchema
     *             leaf list schema of leaf list entry
     * @param value
     *             value of leaf entry
     * @return entry node
     */
    @SuppressWarnings("rawtypes")
    private static LeafSetEntryNode leafListEntryBuild(final LeafListSchemaNode leafListSchema, final String value) {
        return Builders.leafSetEntryBuilder(leafListSchema).withValue(value).build();
    }

    /**
     * Find specific schema node by qname in parent {@link ContainerSchemaNode}.
     *
     * @param parent
     *             schemaNode
     * @param childQName
     *             specific qname of child
     * @return schema node of child by qname
     */
    private static DataSchemaNode getChildOfCont(final ContainerSchemaNode parent, final QName childQName) {
        for (final DataSchemaNode child : parent.getChildNodes()) {
            if (child.getQName().equals(childQName)) {
                return child;
            }
        }
        throw new RestconfDocumentedException(
                childQName.getLocalName() + " doesn't exist in container " + MonitoringModule.CONT_RESTCONF_STATE_NAME);
    }

    /**
     * Mapping of the single data-change event stream to monitoring data at schema path: '/restconf-state/streams'.
     *
     * @param schemaContext Schema context used for serialization of {@link YangInstanceIdentifier} and searching for
     *                      the schema node of the input YIID.
     * @param dataNodePath  Path of the data-change events source.
     * @param accessData    Stream location and resulting stream URI.
     * @return Streams container with monitoring information bound to the input stream
     *     specified by {@link YangInstanceIdentifier}.
     */
    public static ContainerNode mapDataChangeStream(final SchemaContext schemaContext,
            final YangInstanceIdentifier dataNodePath, final StreamAccessMonitoringData accessData) {
        final String serializedPath = ParserIdentifier.stringFromYangInstanceIdentifier(dataNodePath, schemaContext);
        final SchemaNode schemaNode = ParserIdentifier.toInstanceIdentifier(serializedPath, schemaContext,
                Optional.empty()).getSchemaNode();
        if (schemaNode == null) {
            throw new IllegalArgumentException(String.format("Cannot find schema node for data specified by YIID %s.",
                    dataNodePath));
        }

        final MapEntryNode streamAccessNode = buildStreamAccessNode(accessData.outputType, accessData.streamLocation);
        final DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> streamNodeBuilder
                = createStreamMapNodeByName(serializedPath);

        schemaNode.getDescription().ifPresent(description -> addDescription(streamNodeBuilder, description));
        addAccessInformation(streamNodeBuilder, Collections.singleton(streamAccessNode));
        addReplaySupport(streamNodeBuilder);
        return createStreamsContainer(Collections.singletonList(streamNodeBuilder.build()));
    }

    /**
     * Mapping of information about YANG notification streams to monitoring data at schema-path:
     * '/restconf-state/streams'.
     *
     * @param schemaContext Schema context used for serialization of {@link SchemaPath}.
     * @param notifications Notification definitions with corresponding access information (stream location and output).
     * @return Streams container with parsed notifications (this container contains only one list with name 'stream').
     */
    public static ContainerNode mapNotificationStreams(final SchemaContext schemaContext,
            final Map<NotificationDefinition, List<StreamAccessMonitoringData>> notifications) {
        final List<MapEntryNode> streamNodes = notifications.entrySet().stream()
                .map(entry -> buildStreamNode(entry.getKey(), entry.getValue(), schemaContext))
                .collect(Collectors.toList());
        return createStreamsContainer(streamNodes);
    }

    /**
     * Mapping of the data-change event bound to YIID or notification definition to monitoring data with updated
     * replay log creation time. The output {@link MapEntryNode} is at schema-path: '/restconf-state/streams/stream'.
     *
     * @param streamName            Stream name derived from the schema-path or YIID.
     * @param replayLogCreationTime Time the replay log for this stream was created.
     * @return List node for the input stream specified by notification definition.
     */
    public static MapEntryNode mapStreamSubscription(final String streamName, final Instant replayLogCreationTime) {
        final DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> streamNodeBuilder
                = createStreamMapNodeByName(streamName);
        addReplayLogCreationTime(streamNodeBuilder, replayLogCreationTime);
        return streamNodeBuilder.build();
    }

    private static ContainerNode createStreamsContainer(final List<MapEntryNode> streamNodes) {
        return ImmutableContainerNodeBuilder.create()
                .withNodeIdentifier(NodeIdentifier.create(MonitoringModule.CONT_STREAMS_QNAME))
                .withChild(ImmutableMapNodeBuilder.create()
                        .withNodeIdentifier(NodeIdentifier.create(MonitoringModule.LIST_STREAM_QNAME))
                        .withValue(streamNodes)
                        .build())
                .build();
    }

    /**
     * Building of one list node for the input notification definition and corresponding access information. The output
     * {@link MapEntryNode} is at schema-path: '/restconf-state/streams/stream'.
     *
     * @param notification      Structure that hold information about notification.
     * @param accessInformation Stream location and resulting stream URI.
     * @param schemaContext     Schema context used for serialization of {@link SchemaPath}.
     * @return List node for the input stream.
     */
    private static MapEntryNode buildStreamNode(final NotificationDefinition notification,
            final List<StreamAccessMonitoringData> accessInformation, final SchemaContext schemaContext) {
        final String streamName = SchemaPathCodec.serialize(notification.getPath(), schemaContext);
        final DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> streamNodeBuilder
                = createStreamMapNodeByName(streamName);
        final List<MapEntryNode> accessTypes = accessInformation.stream()
                .map(outputURIEntry -> buildStreamAccessNode(outputURIEntry.outputType, outputURIEntry.streamLocation))
                .collect(Collectors.toList());

        notification.getDescription().ifPresent(description -> addDescription(streamNodeBuilder, description));
        addAccessInformation(streamNodeBuilder, accessTypes);
        addReplaySupport(streamNodeBuilder);
        return streamNodeBuilder.build();
    }

    /**
     * Building of one list-node for the input stream-access information. The output {@link MapEntryNode}
     * is at schema-path: '/restconf-state/streams/stream/access'.
     *
     * @param outputType     Output type of data streamed via web-socket session.
     * @param streamLocation Stream location in form of URI path.
     * @return List node for the stream access information that holds data about stream location and output type.
     */
    private static MapEntryNode buildStreamAccessNode(final NotificationOutputType outputType,
                                                      final URI streamLocation) {
        final DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> streamAccessNodeBuilder
                = ImmutableMapEntryNodeBuilder.create()
                .withNodeIdentifier(NodeIdentifierWithPredicates.of(MonitoringModule.LIST_ACCESS_STREAM_QNAME,
                        MonitoringModule.LEAF_ENCODING_ACCESS_QNAME, outputType.getName()));

        streamAccessNodeBuilder.withChild(ImmutableLeafNodeBuilder.create()
                .withNodeIdentifier(NodeIdentifier.create(MonitoringModule.LEAF_ENCODING_ACCESS_QNAME))
                .withValue(outputType.getName())
                .build());
        streamAccessNodeBuilder.withChild(ImmutableLeafNodeBuilder.create()
                .withNodeIdentifier(NodeIdentifier.create(MonitoringModule.LEAF_LOCATION_ACCESS_QNAME))
                .withValue(streamLocation)
                .build());

        return streamAccessNodeBuilder.build();
    }

    private static DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> createStreamMapNodeByName(
            final String streamName) {
        final DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> streamNodeBuilder
                = ImmutableMapEntryNodeBuilder.create().withNodeIdentifier(NodeIdentifierWithPredicates.of(
                MonitoringModule.LIST_STREAM_QNAME, MonitoringModule.LEAF_NAME_STREAM_QNAME, streamName));
        streamNodeBuilder.withChild(ImmutableLeafNodeBuilder.create()
                .withNodeIdentifier(NodeIdentifier.create(MonitoringModule.LEAF_NAME_STREAM_QNAME))
                .withValue(streamName)
                .build());
        return streamNodeBuilder;
    }

    private static void addDescription(final DataContainerNodeBuilder<?, ?> builder, final String description) {
        builder.withChild(ImmutableLeafNodeBuilder.create()
                .withNodeIdentifier(NodeIdentifier.create(MonitoringModule.LEAF_DESCR_STREAM_QNAME))
                .withValue(description)
                .build());
    }

    private static void addReplaySupport(final DataContainerNodeBuilder<?, ?> builder) {
        builder.withChild(ImmutableLeafNodeBuilder.create()
                .withNodeIdentifier(NodeIdentifier.create(MonitoringModule.LEAF_REPLAY_SUPP_STREAM_QNAME))
                .withValue(true)
                .build());
    }

    private static void addAccessInformation(final DataContainerNodeBuilder<?, ?> builder,
                                             final Collection<MapEntryNode> accessTypes) {
        builder.withChild(ImmutableMapNodeBuilder.create()
                .withNodeIdentifier(NodeIdentifier.create(MonitoringModule.LIST_ACCESS_STREAM_QNAME))
                .withValue(accessTypes)
                .build());
    }

    private static void addReplayLogCreationTime(final DataContainerNodeBuilder<?, ?> builder,
                                                 final Instant replayLogCreationTime) {
        builder.withChild(ImmutableLeafNodeBuilder.create()
                .withNodeIdentifier(NodeIdentifier.create(MonitoringModule.LEAF_START_TIME_STREAM_QNAME))
                .withValue(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
                        OffsetDateTime.ofInstant(replayLogCreationTime, ZoneId.systemDefault())))
                .build());
    }
}