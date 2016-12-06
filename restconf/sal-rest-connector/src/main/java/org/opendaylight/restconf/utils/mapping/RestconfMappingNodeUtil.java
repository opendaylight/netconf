/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.utils.mapping;

import com.google.common.base.Preconditions;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Set;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.restconf.Draft18.IetfYangLibrary;
import org.opendaylight.restconf.Draft18.MonitoringModule;
import org.opendaylight.restconf.Draft18.MonitoringModule.QueryParams;
import org.opendaylight.restconf.utils.schema.context.RestconfSchemaUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev160621.module.list.Module.ConformanceType;
import org.opendaylight.yangtools.yang.common.QName;
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
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

/**
 * Util class for mapping nodes
 *
 */
public final class RestconfMappingNodeUtil {

    private RestconfMappingNodeUtil() {
        throw new UnsupportedOperationException("Util class");
    }

    /**
     * Map data from modules to {@link NormalizedNode}
     *
     * @param modules
     *            - modules for mapping
     * @param ietfYangLibraryModule
     *            - ietf-yang-library module
     * @param context
     *            - schema context
     * @param moduleSetId
     *            - module-set-id of actual set
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
     * Map data by the specific module or submodule
     *
     * @param mapBuilder
     *            - ordered list builder for children
     * @param moduleSch
     *            - schema of list for entryMapBuilder
     * @param isSubmodule
     *            - true if module is specified as submodule, false otherwise
     * @param module
     *            - specific module or submodule
     * @param ietfYangLibraryModule
     *            - ietf-yang-library module
     * @param context
     *            - schema context
     */
    private static void fillMapByModules(final CollectionNodeBuilder<MapEntryNode, OrderedMapNode> mapBuilder,
            final DataSchemaNode moduleSch, final boolean isSubmodule, final Module module,
            final Module ietfYangLibraryModule, final SchemaContext context) {
        final DataContainerNodeAttrBuilder<NodeIdentifierWithPredicates, MapEntryNode> mapEntryBuilder =
                Builders.mapEntryBuilder((ListSchemaNode) moduleSch);
        addCommonLeafs(module, mapEntryBuilder, ietfYangLibraryModule);
        addChildOfModuleBySpecificModuleInternal(
                IetfYangLibrary.SPECIFIC_MODULE_SCHEMA_LEAF_QNAME, mapEntryBuilder, IetfYangLibrary.BASE_URI_OF_SCHEMA
                        + module.getName() + "/" + new SimpleDateFormat("yyyy-MM-dd").format(module.getRevision()),
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
     * Mapping submodules of specific module
     *
     * @param module
     *            - module with submodules
     * @param mapEntryBuilder
     *            - mapEntryBuilder of parent for mapping children
     * @param ietfYangLibraryModule
     *            - ietf-yang-library module
     * @param context
     *            - schema context
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
     * Mapping deviations of specific module
     *
     * @param module
     *            - module with deviations
     * @param mapEntryBuilder
     *            - mapEntryBuilder of parent for mapping children
     * @param ietfYangLibraryModule
     *            - ietf-yang-library module
     * @param context
     *            - schema context
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
     * Mapping features of specific module
     *
     * @param qnameOfFeaturesLeafList
     *            - qname of feature leaf-list in ietf-yang-library module
     * @param mapEntryBuilder
     *            - mapEntryBuilder of parent for mapping children
     * @param features
     *            - features of specific module
     * @param ietfYangLibraryModule
     *            - ieat-yang-library module
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
     * specific module
     *
     * @param module
     *            - specific module for getting name and revision
     * @param mapEntryBuilder
     *            - mapEntryBuilder of parent for mapping children
     * @param ietfYangLibraryModule
     *            - ietf-yang-library module
     */
    private static void addCommonLeafs(final Module module,
            final DataContainerNodeAttrBuilder<NodeIdentifierWithPredicates, MapEntryNode> mapEntryBuilder,
            final Module ietfYangLibraryModule) {
        addChildOfModuleBySpecificModuleInternal(IetfYangLibrary.SPECIFIC_MODULE_NAME_LEAF_QNAME, mapEntryBuilder,
                module.getName(), ietfYangLibraryModule);
        addChildOfModuleBySpecificModuleInternal(IetfYangLibrary.SPECIFIC_MODULE_REVISION_LEAF_QNAME, mapEntryBuilder,
                new SimpleDateFormat("yyyy-MM-dd").format(module.getRevision()), ietfYangLibraryModule);
    }

    /**
     * Mapping data child of grouping module-list by ietf-yang-library
     *
     * @param specificQName
     *            - qname of leaf in module-list grouping
     * @param mapEntryBuilder
     *            - mapEntryBuilder of parent for mapping children
     * @param value
     *            - value of leaf
     * @param ietfYangLibraryModule
     *            - ietf-yang-library module
     */
    private static void addChildOfModuleBySpecificModuleOfListChild(final QName specificQName,
            final DataContainerNodeAttrBuilder<NodeIdentifierWithPredicates, MapEntryNode> mapEntryBuilder,
            final Object value, final Module ietfYangLibraryModule) {
        final DataSchemaNode leafSch = findSchemaInListOfModulesSchema(specificQName, ietfYangLibraryModule);
        mapEntryBuilder.withChild(Builders.leafBuilder((LeafSchemaNode) leafSch).withValue(value).build());
    }

    /**
     * Find specific schema in gourping module-lsit
     *
     * @param specificQName
     *            - qname of schema
     * @param ietfYangLibraryModule
     *            - ietf-yang-library module
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
     * Mapping data child of internal groupings in module-list grouping
     *
     * @param specifiLeafQName
     *            - qnmae of leaf for mapping
     * @param mapEntryBuilder
     *            - mapEntryBuilder of parent for mapping children
     * @param value
     *            - value of leaf
     * @param ietfYangLibraryModule
     *            - ietf-yang-library module
     */
    private static void addChildOfModuleBySpecificModuleInternal(final QName specifiLeafQName,
            final DataContainerNodeAttrBuilder<NodeIdentifierWithPredicates, MapEntryNode> mapEntryBuilder,
            final Object value, final Module ietfYangLibraryModule) {
        final DataSchemaNode nameLeaf = findNodeInInternGroupings(specifiLeafQName, ietfYangLibraryModule);
        mapEntryBuilder.withChild(Builders.leafBuilder((LeafSchemaNode) nameLeaf).withValue(value).build());
    }

    /**
     * Find schema node of leaf by qname in internal groupings of module-list
     * grouping
     *
     * @param qnameOfSchema
     *            - qname of leaf
     * @param ietfYangLibraryModule
     *            - ietf-yang-library module
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
     * Mapping childrens of list-module
     *
     * @param specifiLeafQName
     *            - qname of leaf
     * @param mapEntryBuilder
     *            - maptEntryBuilder of parent for mapping children
     * @param value
     *            - valeu of leaf
     * @param ietfYangLibraryModule
     *            - ietf-yang-library module
     */
    private static void addChildOfModuleBySpecificModule(final QName specifiLeafQName,
            final DataContainerNodeAttrBuilder<NodeIdentifierWithPredicates, MapEntryNode> mapEntryBuilder,
            final Object value, final Module ietfYangLibraryModule) {
        final DataSchemaNode nameLeaf = findNodeInGroupings(specifiLeafQName, ietfYangLibraryModule);
        mapEntryBuilder.withChild(Builders.leafBuilder((LeafSchemaNode) nameLeaf).withValue(value).build());
    }

    /**
     * Find schema of specific leaf in list-module grouping
     *
     * @param qnameOfSchema
     *            - qname of leaf
     * @param ietfYangLibraryModule
     *            - ietf-yang-library module
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
     * Mapping {@link MapEntryNode} stream entries of stream to
     * {@link ListSchemaNode}
     *
     * @param streamName
     *            - stream name
     * @param streamListSchemaNode
     *            - mapped {@link DataSchemaNode}
     * @return {@link MapEntryNode}
     */
    public static MapEntryNode toStreamEntryNode(final String streamName, final DataSchemaNode streamListSchemaNode) {
        Preconditions.checkState(streamListSchemaNode instanceof ListSchemaNode);
        final ListSchemaNode listStreamSchemaNode = (ListSchemaNode) streamListSchemaNode;
        final DataContainerNodeAttrBuilder<NodeIdentifierWithPredicates, MapEntryNode> streamNodeValues = Builders
                .mapEntryBuilder(listStreamSchemaNode);

        // STREAM NAME
        fillListWithLeaf(listStreamSchemaNode, streamNodeValues, RestconfMappingNodeConstants.NAME, streamName);

        // STREAM DESCRIPTION
        fillListWithLeaf(listStreamSchemaNode, streamNodeValues, RestconfMappingNodeConstants.DESCRIPTION,
                RestconfMappingStreamConstants.DESCRIPTION);

        // STREAM REPLAY_SUPPORT
        fillListWithLeaf(listStreamSchemaNode, streamNodeValues, RestconfMappingNodeConstants.REPLAY_SUPPORT,
                RestconfMappingStreamConstants.REPLAY_SUPPORT);

        // STREAM REPLAY_LOG
        fillListWithLeaf(listStreamSchemaNode, streamNodeValues, RestconfMappingNodeConstants.REPLAY_LOG,
                RestconfMappingStreamConstants.REPLAY_LOG);

        // STREAM EVENTS
        fillListWithLeaf(listStreamSchemaNode, streamNodeValues, RestconfMappingNodeConstants.EVENTS,
                RestconfMappingStreamConstants.EVENTS);

        return streamNodeValues.build();
    }

    /**
     * Method for filling {@link ListSchemaNode} with {@link LeafSchemaNode}
     *
     * @param listStreamSchemaNode
     *            - {@link ListSchemaNode}
     * @param streamNodeValues
     *            - filled {@link DataContainerNodeAttrBuilder}
     * @param nameSchemaNode
     *            - name of mapped leaf
     * @param value
     *            - value for mapped node
     */
    private static void fillListWithLeaf(
            final ListSchemaNode listStreamSchemaNode,
            final DataContainerNodeAttrBuilder<NodeIdentifierWithPredicates, MapEntryNode> streamNodeValues,
            final String nameSchemaNode, final Object value) {
        final DataSchemaNode schemaNode = RestconfSchemaUtil
                .findSchemaNodeInCollection(listStreamSchemaNode.getChildNodes(), nameSchemaNode);
        Preconditions.checkState(schemaNode instanceof LeafSchemaNode);
        streamNodeValues.withChild(Builders.leafBuilder((LeafSchemaNode) schemaNode).withValue(value).build());
    }

    /**
     * Map capabilites by ietf-restconf-monitoring
     *
     * @param monitoringModule
     *            - ietf-restconf-monitoring module
     * @return mapped capabilites
     */
    public static NormalizedNode<NodeIdentifier, Collection<DataContainerChild<? extends PathArgument, ?>>>
            mapCapabilites(final Module monitoringModule) {
        final DataSchemaNode restconfState =
                monitoringModule.getDataChildByName(MonitoringModule.CONT_RESTCONF_STATE_QNAME);
        final DataContainerNodeAttrBuilder<NodeIdentifier, ContainerNode> restStateContBuilder =
                Builders.containerBuilder((ContainerSchemaNode) restconfState);
        final DataSchemaNode capabilitesContSchema =
                getChildOFCOnt((ContainerSchemaNode) restconfState, MonitoringModule.CONT_CAPABILITES_QNAME);
        final DataContainerNodeAttrBuilder<NodeIdentifier, ContainerNode> capabilitesContBuilder =
                Builders.containerBuilder((ContainerSchemaNode) capabilitesContSchema);
        final DataSchemaNode leafListCapa = getChildOFCOnt((ContainerSchemaNode) capabilitesContSchema,
                MonitoringModule.LEAF_LIST_CAPABILITY_QNAME);
        final ListNodeBuilder<Object, LeafSetEntryNode<Object>> leafListCapaBuilder =
                Builders.orderedLeafSetBuilder((LeafListSchemaNode) leafListCapa);
        fillLeafListCapa(leafListCapaBuilder, (LeafListSchemaNode) leafListCapa);

        return restStateContBuilder.withChild(capabilitesContBuilder.withChild(leafListCapaBuilder.build()).build())
                .build();
    }

    /**
     * Map data to leaf-list
     *
     * @param builder
     *            - builder of parent for children
     * @param leafListSchema
     */
    @SuppressWarnings("unchecked")
    private static void fillLeafListCapa(final ListNodeBuilder builder, final LeafListSchemaNode leafListSchema) {
        builder.withChild(leafListEntryBuild(leafListSchema, QueryParams.DEPTH));
        builder.withChild(leafListEntryBuild(leafListSchema, QueryParams.FIELDS));
        builder.withChild(leafListEntryBuild(leafListSchema, QueryParams.FILTER));
        builder.withChild(leafListEntryBuild(leafListSchema, QueryParams.REPLAY));
        builder.withChild(leafListEntryBuild(leafListSchema, QueryParams.WITH_DEFAULTS));
    }

    /**
     * Map value to leaf list entry node
     *
     * @param leafListSchema
     *            - leaf list schema of leaf list entry
     * @param value
     *            - value of leaf entry
     * @return entry node
     */
    @SuppressWarnings("rawtypes")
    private static LeafSetEntryNode leafListEntryBuild(final LeafListSchemaNode leafListSchema, final String value) {
        return Builders.leafSetEntryBuilder(leafListSchema).withValue(value).build();
    }

    /**
     * Find specific schema node by qname in parent {@link ContainerSchemaNode}
     *
     * @param parent
     *            - schemaNode
     * @param childQName
     *            - specific qname of child
     * @return schema node of child by qname
     */
    private static DataSchemaNode getChildOFCOnt(final ContainerSchemaNode parent, final QName childQName) {
        for (final DataSchemaNode child : parent.getChildNodes()) {
            if (child.getQName().equals(childQName)) {
                return child;
            }
        }
        throw new RestconfDocumentedException(
                childQName.getLocalName() + "doesn't exist in container " + MonitoringModule.CONT_RESTCONF_STATE_NAME);
    }
}
