/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.utils.mapping;

import com.google.common.base.Optional;
import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Set;
import org.opendaylight.restconf.Rfc8040.IetfYangLibrary;
import org.opendaylight.restconf.Rfc8040.MonitoringModule;
import org.opendaylight.restconf.Rfc8040.MonitoringModule.QueryParams;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.utils.parser.ParserIdentifier;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev160621.module.list.Module.ConformanceType;
import org.opendaylight.yangtools.yang.common.QName;
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
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.DataContainerNodeAttrBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.ListNodeBuilder;
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

/**
 * Util class for mapping nodes.
 *
 */
public final class RestconfMappingNodeUtil {

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
        final DataContainerNodeAttrBuilder<NodeIdentifier, ContainerNode> modulesStateBuilder =
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
        final DataContainerNodeAttrBuilder<NodeIdentifierWithPredicates, MapEntryNode> mapEntryBuilder =
                Builders.mapEntryBuilder((ListSchemaNode) moduleSch);
        addCommonLeafs(module, mapEntryBuilder, ietfYangLibraryModule);
        addChildOfModuleBySpecificModuleInternal(
                IetfYangLibrary.SPECIFIC_MODULE_SCHEMA_LEAF_QNAME, mapEntryBuilder, IetfYangLibrary.BASE_URI_OF_SCHEMA
                        + module.getName() + "/" + module.getQNameModule().getFormattedRevision(),
                ietfYangLibraryModule);
        if (!isSubmodule) {
            addChildOfModuleBySpecificModuleOfListChild(IetfYangLibrary.SPECIFIC_MODULE_NAMESPACE_LEAF_QNAME,
                    mapEntryBuilder, module.getNamespace().toString(), ietfYangLibraryModule);

            // features - not mandatory
            if ((module.getFeatures() != null) && !module.getFeatures().isEmpty()) {
                addFeatureLeafList(IetfYangLibrary.SPECIFIC_MODULE_FEATURE_LEAF_LIST_QNAME, mapEntryBuilder,
                        module.getFeatures(), ietfYangLibraryModule);
            }
            // deviations - not mandatory
            if ((module.getDeviations() != null) && !module.getDeviations().isEmpty()) {
                addDeviationList(module, mapEntryBuilder, ietfYangLibraryModule, context);
                addChildOfModuleBySpecificModuleOfListChild(IetfYangLibrary.SPECIFIC_MODULE_CONFORMANCE_LEAF_QNAME,
                        mapEntryBuilder, ConformanceType.Implement.getName(), ietfYangLibraryModule);
            } else {
                addChildOfModuleBySpecificModuleOfListChild(IetfYangLibrary.SPECIFIC_MODULE_CONFORMANCE_LEAF_QNAME,
                        mapEntryBuilder, ConformanceType.Import.getName(), ietfYangLibraryModule);
            }
            // submodules - not mandatory
            if ((module.getSubmodules() != null) && !module.getSubmodules().isEmpty()) {
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
            final DataContainerNodeAttrBuilder<NodeIdentifierWithPredicates, MapEntryNode> mapEntryBuilder,
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
            final DataContainerNodeAttrBuilder<NodeIdentifierWithPredicates, MapEntryNode> mapEntryBuilder,
            final Module ietfYangLibraryModule, final SchemaContext context) {
        final DataSchemaNode deviationsSchema = findSchemaInListOfModulesSchema(
                IetfYangLibrary.SPECIFIC_MODULE_DEVIATION_LIST_QNAME, ietfYangLibraryModule);
        final CollectionNodeBuilder<MapEntryNode, MapNode> deviations =
                Builders.mapBuilder((ListSchemaNode) deviationsSchema);
        for (final Deviation deviation : module.getDeviations()) {
            final DataContainerNodeAttrBuilder<NodeIdentifierWithPredicates, MapEntryNode> deviationEntryNode =
                    Builders.mapEntryBuilder((ListSchemaNode) deviationsSchema);
            final QName lastComponent = deviation.getTargetPath().getLastComponent();
            addChildOfModuleBySpecificModule(IetfYangLibrary.SPECIFIC_MODULE_NAME_LEAF_QNAME, deviationEntryNode,
                    context.findModuleByNamespaceAndRevision(lastComponent.getNamespace(), lastComponent.getRevision())
                            .getName(),
                    ietfYangLibraryModule);
            addChildOfModuleBySpecificModule(IetfYangLibrary.SPECIFIC_MODULE_REVISION_LEAF_QNAME, deviationEntryNode,
                    lastComponent.getRevision(), ietfYangLibraryModule);
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
            final DataContainerNodeAttrBuilder<NodeIdentifierWithPredicates, MapEntryNode> mapEntryBuilder,
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
            final DataContainerNodeAttrBuilder<NodeIdentifierWithPredicates, MapEntryNode> mapEntryBuilder,
            final Module ietfYangLibraryModule) {
        addChildOfModuleBySpecificModuleInternal(IetfYangLibrary.SPECIFIC_MODULE_NAME_LEAF_QNAME, mapEntryBuilder,
                module.getName(), ietfYangLibraryModule);
        addChildOfModuleBySpecificModuleInternal(IetfYangLibrary.SPECIFIC_MODULE_REVISION_LEAF_QNAME, mapEntryBuilder,
                module.getQNameModule().getFormattedRevision(), ietfYangLibraryModule);
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
            final DataContainerNodeAttrBuilder<NodeIdentifierWithPredicates, MapEntryNode> mapEntryBuilder,
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
            final DataContainerNodeAttrBuilder<NodeIdentifierWithPredicates, MapEntryNode> mapEntryBuilder,
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
     * Mapping childrens of list-module.
     *
     * @param specifiLeafQName
     *             qname of leaf
     * @param mapEntryBuilder
     *             maptEntryBuilder of parent for mapping children
     * @param value
     *             valeu of leaf
     * @param ietfYangLibraryModule
     *             ietf-yang-library module
     */
    private static void addChildOfModuleBySpecificModule(final QName specifiLeafQName,
            final DataContainerNodeAttrBuilder<NodeIdentifierWithPredicates, MapEntryNode> mapEntryBuilder,
            final Object value, final Module ietfYangLibraryModule) {
        final DataSchemaNode nameLeaf = findNodeInGroupings(specifiLeafQName, ietfYangLibraryModule);
        mapEntryBuilder.withChild(Builders.leafBuilder((LeafSchemaNode) nameLeaf).withValue(value).build());
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
        final DataContainerNodeAttrBuilder<NodeIdentifier, ContainerNode> restStateContBuilder =
                Builders.containerBuilder((ContainerSchemaNode) restconfState);
        final DataSchemaNode capabilitesContSchema =
                getChildOfCont((ContainerSchemaNode) restconfState, MonitoringModule.CONT_CAPABILITES_QNAME);
        final DataContainerNodeAttrBuilder<NodeIdentifier, ContainerNode> capabilitesContBuilder =
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
    @SuppressWarnings("rawtypes")
    public static NormalizedNode mapYangNotificationStreamByIetfRestconfMonitoring(final QName notifiQName,
            final Set<NotificationDefinition> notifications, final Instant start, final String outputType,
            final URI uri, final Module monitoringModule, final boolean existParent) {
        for (final NotificationDefinition notificationDefinition : notifications) {
            if (notificationDefinition.getQName().equals(notifiQName)) {
                final DataSchemaNode streamListSchema = ((ContainerSchemaNode) ((ContainerSchemaNode) monitoringModule
                        .getDataChildByName(MonitoringModule.CONT_RESTCONF_STATE_QNAME))
                                .getDataChildByName(MonitoringModule.CONT_STREAMS_QNAME))
                                        .getDataChildByName(MonitoringModule.LIST_STREAM_QNAME);
                final DataContainerNodeAttrBuilder<NodeIdentifierWithPredicates, MapEntryNode> streamEntry =
                        Builders.mapEntryBuilder((ListSchemaNode) streamListSchema);

                final ListSchemaNode listSchema = ((ListSchemaNode) streamListSchema);
                prepareLeafAndFillEntryBuilder(streamEntry,
                        listSchema.getDataChildByName(MonitoringModule.LEAF_NAME_STREAM_QNAME),
                        notificationDefinition.getQName().getLocalName());
                if ((notificationDefinition.getDescription() != null)
                        && !notificationDefinition.getDescription().equals("")) {
                    prepareLeafAndFillEntryBuilder(streamEntry,
                            listSchema.getDataChildByName(MonitoringModule.LEAF_DESCR_STREAM_QNAME),
                            notificationDefinition.getDescription());
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
            final DataContainerNodeAttrBuilder<NodeIdentifierWithPredicates, MapEntryNode> streamEntry,
            final ListSchemaNode listSchemaNode, final String outputType, final URI uriToWebsocketServer) {
        final CollectionNodeBuilder<MapEntryNode, MapNode> accessListBuilder = Builders.mapBuilder(listSchemaNode);
        final DataContainerNodeAttrBuilder<NodeIdentifierWithPredicates, MapEntryNode> entryAccessList =
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
            final DataContainerNodeAttrBuilder<NodeIdentifierWithPredicates, MapEntryNode> streamEntry,
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
    @SuppressWarnings("rawtypes")
    public static NormalizedNode mapDataChangeNotificationStreamByIetfRestconfMonitoring(
            final YangInstanceIdentifier path, final Instant start, final String outputType, final URI uri,
            final Module monitoringModule, final boolean existParent, final SchemaContext schemaContext) {
        final SchemaNode schemaNode = ParserIdentifier
                .toInstanceIdentifier(ParserIdentifier.stringFromYangInstanceIdentifier(path, schemaContext),
                        schemaContext, Optional.absent())
                .getSchemaNode();
        final DataSchemaNode streamListSchema = ((ContainerSchemaNode) ((ContainerSchemaNode) monitoringModule
                .getDataChildByName(MonitoringModule.CONT_RESTCONF_STATE_QNAME))
                        .getDataChildByName(MonitoringModule.CONT_STREAMS_QNAME))
                                .getDataChildByName(MonitoringModule.LIST_STREAM_QNAME);
        final DataContainerNodeAttrBuilder<NodeIdentifierWithPredicates, MapEntryNode> streamEntry =
                Builders.mapEntryBuilder((ListSchemaNode) streamListSchema);

        final ListSchemaNode listSchema = ((ListSchemaNode) streamListSchema);
        prepareLeafAndFillEntryBuilder(streamEntry,
                listSchema.getDataChildByName(MonitoringModule.LEAF_NAME_STREAM_QNAME),
                schemaNode.getQName().getLocalName());
        if ((schemaNode.getDescription() != null) && !schemaNode.getDescription().equals("")) {
            prepareLeafAndFillEntryBuilder(streamEntry,
                    listSchema.getDataChildByName(MonitoringModule.LEAF_DESCR_STREAM_QNAME),
                    schemaNode.getDescription());
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
