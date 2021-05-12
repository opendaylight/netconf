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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.Rfc8040.IetfYangLibrary;
import org.opendaylight.restconf.nb.rfc8040.Rfc8040.MonitoringModule;
import org.opendaylight.restconf.nb.rfc8040.Rfc8040.MonitoringModule.QueryParams;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.ParserIdentifier;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev160621.ModulesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev160621.module.list.Module.ConformanceType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeWithValue;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.OrderedMapNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.CollectionNodeBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.DataContainerNodeBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.ListNodeBuilder;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Deviation;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.FeatureDefinition;
import org.opendaylight.yangtools.yang.model.api.LeafListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.ModuleLike;
import org.opendaylight.yangtools.yang.model.api.NotificationDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.model.api.Submodule;

/**
 * Util class for mapping nodes.
 *
 */
public final class RestconfMappingNodeUtil {
    private RestconfMappingNodeUtil() {
        // Hidden on purpose
    }

    /**
     * Map data from modules to {@link NormalizedNode}.
     *
     * @param modules modules for mapping
     * @param context schema context
     * @param moduleSetId module-set-id of actual set
     * @return mapped data as {@link NormalizedNode}
     */
    public static ContainerNode mapModulesByIetfYangLibraryYang(final Collection<? extends Module> modules,
            final SchemaContext context, final String moduleSetId) {
        final CollectionNodeBuilder<MapEntryNode, OrderedMapNode> mapBuilder = Builders.orderedMapBuilder()
                .withNodeIdentifier(new NodeIdentifier(IetfYangLibrary.MODULE_QNAME_LIST));
        for (final Module module : context.getModules()) {
            fillMapByModules(mapBuilder, IetfYangLibrary.MODULE_QNAME_LIST, false, module, context);
        }
        return Builders.containerBuilder()
            .withNodeIdentifier(new NodeIdentifier(ModulesState.QNAME))
            .withChild(ImmutableNodes.leafNode(IetfYangLibrary.MODULE_SET_ID_LEAF_QNAME, moduleSetId))
            .withChild(mapBuilder.build()).build();
    }

    /**
     * Map data by the specific module or submodule.
     *
     * @param mapBuilder
     *             ordered list builder for children
     * @param mapQName
     *             QName corresponding to the list builder
     * @param isSubmodule
     *             true if module is specified as submodule, false otherwise
     * @param module
     *             specific module or submodule
     * @param context
     *             schema context
     */
    private static void fillMapByModules(final CollectionNodeBuilder<MapEntryNode, OrderedMapNode> mapBuilder,
            final QName mapQName, final boolean isSubmodule, final ModuleLike module, final SchemaContext context) {
        final DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> mapEntryBuilder =
            newCommonLeafsMapEntryBuilder(mapQName, module);

        mapEntryBuilder.withChild(ImmutableNodes.leafNode(IetfYangLibrary.SPECIFIC_MODULE_SCHEMA_LEAF_QNAME,
            IetfYangLibrary.BASE_URI_OF_SCHEMA + module.getName() + "/"
            // FIXME: orElse(null) does not seem appropriate here
            + module.getQNameModule().getRevision().map(Revision::toString).orElse(null)));

        if (!isSubmodule) {
            mapEntryBuilder.withChild(ImmutableNodes.leafNode(IetfYangLibrary.SPECIFIC_MODULE_NAMESPACE_LEAF_QNAME,
                module.getNamespace().toString()));

            // features - not mandatory
            if (module.getFeatures() != null && !module.getFeatures().isEmpty()) {
                addFeatureLeafList(mapEntryBuilder, module.getFeatures());
            }
            // deviations - not mandatory
            final ConformanceType conformance;
            if (module.getDeviations() != null && !module.getDeviations().isEmpty()) {
                addDeviationList(module, mapEntryBuilder, context);
                conformance = ConformanceType.Implement;
            } else {
                conformance = ConformanceType.Import;
            }
            mapEntryBuilder.withChild(
                ImmutableNodes.leafNode(IetfYangLibrary.SPECIFIC_MODULE_CONFORMANCE_LEAF_QNAME, conformance.getName()));

            // submodules - not mandatory
            if (module.getSubmodules() != null && !module.getSubmodules().isEmpty()) {
                addSubmodules(module, mapEntryBuilder, context);
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
    private static void addSubmodules(final ModuleLike module,
            final DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> mapEntryBuilder,
            final SchemaContext context) {
        final CollectionNodeBuilder<MapEntryNode, OrderedMapNode> mapBuilder = Builders.orderedMapBuilder()
            .withNodeIdentifier(new NodeIdentifier(IetfYangLibrary.SPECIFIC_MODULE_SUBMODULE_LIST_QNAME));

        for (final Submodule submodule : module.getSubmodules()) {
            fillMapByModules(mapBuilder, IetfYangLibrary.SPECIFIC_MODULE_SUBMODULE_LIST_QNAME, true, submodule,
                context);
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
     * @param context
     *             schema context
     */
    private static void addDeviationList(final ModuleLike module,
            final DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> mapEntryBuilder,
            final SchemaContext context) {
        final CollectionNodeBuilder<MapEntryNode, MapNode> deviations = Builders.mapBuilder()
            .withNodeIdentifier(new NodeIdentifier(IetfYangLibrary.SPECIFIC_MODULE_DEVIATION_LIST_QNAME));
        for (final Deviation deviation : module.getDeviations()) {
            final List<QName> ids = deviation.getTargetPath().getNodeIdentifiers();
            final QName lastComponent = ids.get(ids.size() - 1);

            deviations.withChild(newCommonLeafsMapEntryBuilder(IetfYangLibrary.SPECIFIC_MODULE_DEVIATION_LIST_QNAME,
                context.findModule(lastComponent.getModule()).get())
                .build());
        }
        mapEntryBuilder.withChild(deviations.build());
    }

    /**
     * Mapping features of specific module.
     *
     * @param mapEntryBuilder mapEntryBuilder of parent for mapping children
     * @param features features of specific module
     */
    private static void addFeatureLeafList(
            final DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> mapEntryBuilder,
            final Collection<? extends FeatureDefinition> features) {
        final ListNodeBuilder<Object, LeafSetEntryNode<Object>> leafSetBuilder = Builders.leafSetBuilder()
                .withNodeIdentifier(new NodeIdentifier(IetfYangLibrary.SPECIFIC_MODULE_FEATURE_LEAF_LIST_QNAME));
        for (final FeatureDefinition feature : features) {
            final String featureName = feature.getQName().getLocalName();
            leafSetBuilder.withChild(Builders.leafSetEntryBuilder()
                .withNodeIdentifier(
                    new NodeWithValue<>(IetfYangLibrary.SPECIFIC_MODULE_FEATURE_LEAF_LIST_QNAME, featureName))
                .withValue(featureName)
                .build());
        }
        mapEntryBuilder.withChild(leafSetBuilder.build());
    }

    private static DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> newCommonLeafsMapEntryBuilder(
            final QName qname, final ModuleLike module) {
        final var name = module.getName();
        final var revision = module.getQNameModule().getRevision().map(Revision::toString).orElse("");
        return Builders.mapEntryBuilder()
            .withNodeIdentifier(NodeIdentifierWithPredicates.of(qname, Map.of(
                IetfYangLibrary.SPECIFIC_MODULE_NAME_LEAF_QNAME, name,
                IetfYangLibrary.SPECIFIC_MODULE_REVISION_LEAF_QNAME, revision)))
            .withChild(ImmutableNodes.leafNode(IetfYangLibrary.SPECIFIC_MODULE_NAME_LEAF_QNAME, name))
            .withChild(ImmutableNodes.leafNode(IetfYangLibrary.SPECIFIC_MODULE_REVISION_LEAF_QNAME, revision));
    }

    /**
     * Map capabilites by ietf-restconf-monitoring.
     *
     * @param monitoringModule
     *             ietf-restconf-monitoring module
     * @return mapped capabilites
     */
    public static ContainerNode mapCapabilites(final Module monitoringModule) {
        final DataSchemaNode restconfState =
                monitoringModule.getDataChildByName(MonitoringModule.CONT_RESTCONF_STATE_QNAME);
        final DataSchemaNode capabilitesContSchema =
                getChildOfCont((ContainerSchemaNode) restconfState, MonitoringModule.CONT_CAPABILITES_QNAME);
        final DataContainerNodeBuilder<NodeIdentifier, ContainerNode> capabilitesContBuilder =
                Builders.containerBuilder((ContainerSchemaNode) capabilitesContSchema);
        final DataSchemaNode leafListCapa = getChildOfCont((ContainerSchemaNode) capabilitesContSchema,
                MonitoringModule.LEAF_LIST_CAPABILITY_QNAME);
        final ListNodeBuilder<Object, LeafSetEntryNode<Object>> leafListCapaBuilder =
                Builders.orderedLeafSetBuilder((LeafListSchemaNode) leafListCapa);
        fillLeafListCapa(leafListCapaBuilder, (LeafListSchemaNode) leafListCapa);

        return Builders.containerBuilder()
            .withNodeIdentifier(new NodeIdentifier(MonitoringModule.CONT_RESTCONF_STATE_QNAME))
            .withChild(capabilitesContBuilder.withChild(leafListCapaBuilder.build()).build())
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
    private static void fillLeafListCapa(final ListNodeBuilder<Object, LeafSetEntryNode<Object>> builder,
            final LeafListSchemaNode leafListSchema) {
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
    private static LeafSetEntryNode<Object> leafListEntryBuild(final LeafListSchemaNode leafListSchema,
            final String value) {
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
     * Map data of yang notification to normalized node according to
     * ietf-restconf-monitoring.
     *
     * @param notifiQName
     *             qname of notification from listener
     * @param notifications
     *             list of notifications for find schema of notification by
     *            notifiQName
     * @param start
     *             start-time query parameter of notification
     * @param outputType
     *             output type of notification
     * @param uri
     *             location of registered listener for sending data of
     *            notification
     * @param monitoringModule
     *             ietf-restconf-monitoring module
     * @param existParent
     *             true if data of parent -
     *            ietf-restconf-monitoring:restconf-state/streams - exist in DS
     * @return mapped data of notification - map entry node if parent exists,
     *         container streams with list and map entry node if not
     */
    public static NormalizedNode<?, ?> mapYangNotificationStreamByIetfRestconfMonitoring(final QName notifiQName,
            final Collection<? extends NotificationDefinition> notifications, final Instant start,
            final String outputType, final URI uri, final Module monitoringModule, final boolean existParent) {
        for (final NotificationDefinition notificationDefinition : notifications) {
            if (notificationDefinition.getQName().equals(notifiQName)) {
                final DataSchemaNode streamListSchema = ((ContainerSchemaNode) ((ContainerSchemaNode) monitoringModule
                        .getDataChildByName(MonitoringModule.CONT_RESTCONF_STATE_QNAME))
                                .getDataChildByName(MonitoringModule.CONT_STREAMS_QNAME))
                                        .getDataChildByName(MonitoringModule.LIST_STREAM_QNAME);
                final DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> streamEntry =
                        Builders.mapEntryBuilder((ListSchemaNode) streamListSchema);

                final ListSchemaNode listSchema = (ListSchemaNode) streamListSchema;
                prepareLeafAndFillEntryBuilder(streamEntry,
                        listSchema.getDataChildByName(MonitoringModule.LEAF_NAME_STREAM_QNAME),
                        notificationDefinition.getQName().getLocalName());

                final Optional<String> optDesc = notificationDefinition.getDescription();
                if (optDesc.isPresent()) {
                    prepareLeafAndFillEntryBuilder(streamEntry,
                            listSchema.getDataChildByName(MonitoringModule.LEAF_DESCR_STREAM_QNAME), optDesc.get());
                }
                prepareLeafAndFillEntryBuilder(streamEntry,
                        listSchema.getDataChildByName(MonitoringModule.LEAF_REPLAY_SUPP_STREAM_QNAME), true);
                if (start != null) {
                    prepareLeafAndFillEntryBuilder(streamEntry,
                        listSchema.getDataChildByName(MonitoringModule.LEAF_START_TIME_STREAM_QNAME),
                        DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(OffsetDateTime.ofInstant(start,
                            ZoneId.systemDefault())));
                }
                prepareListAndFillEntryBuilder(streamEntry,
                        (ListSchemaNode) listSchema.getDataChildByName(MonitoringModule.LIST_ACCESS_STREAM_QNAME),
                        outputType, uri);

                if (!existParent) {
                    final DataSchemaNode contStreamsSchema = ((ContainerSchemaNode) monitoringModule
                            .getDataChildByName(MonitoringModule.CONT_RESTCONF_STATE_QNAME))
                                    .getDataChildByName(MonitoringModule.CONT_STREAMS_QNAME);
                    return Builders.containerBuilder((ContainerSchemaNode) contStreamsSchema).withChild(Builders
                            .mapBuilder((ListSchemaNode) streamListSchema).withChild(streamEntry.build()).build())
                            .build();
                }
                return streamEntry.build();
            }
        }

        throw new RestconfDocumentedException(notifiQName + " doesn't exist in any modul");
    }

    private static void prepareListAndFillEntryBuilder(
            final DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> streamEntry,
            final ListSchemaNode listSchemaNode, final String outputType, final URI uriToWebsocketServer) {
        final CollectionNodeBuilder<MapEntryNode, MapNode> accessListBuilder = Builders.mapBuilder(listSchemaNode);
        final DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> entryAccessList =
                Builders.mapEntryBuilder(listSchemaNode);
        prepareLeafAndFillEntryBuilder(entryAccessList,
                listSchemaNode.getDataChildByName(MonitoringModule.LEAF_ENCODING_ACCESS_QNAME), outputType);
        prepareLeafAndFillEntryBuilder(entryAccessList,
                listSchemaNode.getDataChildByName(MonitoringModule.LEAF_LOCATION_ACCESS_QNAME),
                uriToWebsocketServer.toString());
        streamEntry.withChild(accessListBuilder.withChild(entryAccessList.build()).build());
    }

    /**
     * Prepare leaf and fill entry builder.
     *
     * @param streamEntry   Stream entry
     * @param leafSchema    Leaf schema
     * @param value         Value
     */
    private static void prepareLeafAndFillEntryBuilder(
            final DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> streamEntry,
            final DataSchemaNode leafSchema, final Object value) {
        streamEntry.withChild(Builders.leafBuilder((LeafSchemaNode) leafSchema).withValue(value).build());
    }

    /**
     * Map data of data change notification to normalized node according to
     * ietf-restconf-monitoring.
     *
     * @param path
     *             path of data to listen on
     * @param start
     *             start-time query parameter of notification
     * @param outputType
     *             output type of notification
     * @param uri
     *             location of registered listener for sending data of
     *            notification
     * @param monitoringModule
     *             ietf-restconf-monitoring module
     * @param existParent
     *             true if data of parent -
     *            ietf-restconf-monitoring:restconf-state/streams - exist in DS
     * @param schemaContext
     *             schemaContext for parsing instance identifier to get schema
     *            node of data
     * @return mapped data of notification - map entry node if parent exists,
     *         container streams with list and map entry node if not
     */
    public static NormalizedNode<?, ?> mapDataChangeNotificationStreamByIetfRestconfMonitoring(
            final YangInstanceIdentifier path, final Instant start, final String outputType, final URI uri,
            final Module monitoringModule, final boolean existParent, final EffectiveModelContext schemaContext) {
        final SchemaNode schemaNode = ParserIdentifier
                .toInstanceIdentifier(ParserIdentifier.stringFromYangInstanceIdentifier(path, schemaContext),
                        schemaContext, Optional.empty())
                .getSchemaNode();
        final DataSchemaNode streamListSchema = ((ContainerSchemaNode) ((ContainerSchemaNode) monitoringModule
                .getDataChildByName(MonitoringModule.CONT_RESTCONF_STATE_QNAME))
                        .getDataChildByName(MonitoringModule.CONT_STREAMS_QNAME))
                                .getDataChildByName(MonitoringModule.LIST_STREAM_QNAME);
        final DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> streamEntry =
                Builders.mapEntryBuilder((ListSchemaNode) streamListSchema);

        final ListSchemaNode listSchema = (ListSchemaNode) streamListSchema;
        prepareLeafAndFillEntryBuilder(streamEntry,
                listSchema.getDataChildByName(MonitoringModule.LEAF_NAME_STREAM_QNAME),
                schemaNode.getQName().getLocalName());

        final Optional<String> optDesc = schemaNode.getDescription();
        if (optDesc.isPresent()) {
            prepareLeafAndFillEntryBuilder(streamEntry,
                    listSchema.getDataChildByName(MonitoringModule.LEAF_DESCR_STREAM_QNAME), optDesc.get());
        }
        prepareLeafAndFillEntryBuilder(streamEntry,
                listSchema.getDataChildByName(MonitoringModule.LEAF_REPLAY_SUPP_STREAM_QNAME), true);
        prepareLeafAndFillEntryBuilder(streamEntry,
                listSchema.getDataChildByName(MonitoringModule.LEAF_START_TIME_STREAM_QNAME),
                DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(OffsetDateTime.ofInstant(start, ZoneId.systemDefault())));
        prepareListAndFillEntryBuilder(streamEntry,
                (ListSchemaNode) listSchema.getDataChildByName(MonitoringModule.LIST_ACCESS_STREAM_QNAME), outputType,
                uri);

        if (!existParent) {
            final DataSchemaNode contStreamsSchema = ((ContainerSchemaNode) monitoringModule
                    .getDataChildByName(MonitoringModule.CONT_RESTCONF_STATE_QNAME))
                            .getDataChildByName(MonitoringModule.CONT_STREAMS_QNAME);
            return Builders
                    .containerBuilder((ContainerSchemaNode) contStreamsSchema).withChild(Builders
                            .mapBuilder((ListSchemaNode) streamListSchema).withChild(streamEntry.build()).build())
                    .build();
        }
        return streamEntry.build();
    }
}
