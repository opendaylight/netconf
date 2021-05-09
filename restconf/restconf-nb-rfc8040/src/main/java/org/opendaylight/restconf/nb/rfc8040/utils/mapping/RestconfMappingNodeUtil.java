/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.utils.mapping;

import static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.monitoring.rev170126.$YangModuleInfoImpl.qnameOf;

import com.google.common.annotations.VisibleForTesting;
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
import org.opendaylight.restconf.nb.rfc8040.Rfc8040;
import org.opendaylight.restconf.nb.rfc8040.Rfc8040.IetfYangLibrary;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.ParserIdentifier;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.monitoring.rev170126.RestconfState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.monitoring.rev170126.restconf.state.Capabilities;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.monitoring.rev170126.restconf.state.streams.Stream;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.monitoring.rev170126.restconf.state.streams.stream.Access;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.ModulesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.library.rev190104.module.list.Module.ConformanceType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.UserMapNode;
import org.opendaylight.yangtools.yang.data.api.schema.builder.CollectionNodeBuilder;
import org.opendaylight.yangtools.yang.data.api.schema.builder.DataContainerNodeBuilder;
import org.opendaylight.yangtools.yang.data.api.schema.builder.ListNodeBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.Deviation;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.FeatureDefinition;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.ModuleLike;
import org.opendaylight.yangtools.yang.model.api.NotificationDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.model.api.Submodule;

/**
 * Util class for mapping nodes.
 */
public final class RestconfMappingNodeUtil {
    private static final QName CAPABILITY_QNAME = qnameOf("capability");
    @VisibleForTesting
    static final QName DESCRIPTION_QNAME = qnameOf("description");
    @VisibleForTesting
    static final QName ENCODING_QNAME = qnameOf("encoding");
    @VisibleForTesting
    static final QName LOCATION_QNAME = qnameOf("location");
    @VisibleForTesting
    static final QName NAME_QNAME = qnameOf("name");
    @VisibleForTesting
    static final QName REPLAY_SUPPORT_QNAME = qnameOf("replay-support");
    @VisibleForTesting
    static final QName REPLAY_LOG_CREATION_TIME = qnameOf("replay-log-creation-time");

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
        final CollectionNodeBuilder<MapEntryNode, UserMapNode> mapBuilder = Builders.orderedMapBuilder()
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
    private static void fillMapByModules(final CollectionNodeBuilder<MapEntryNode, UserMapNode> mapBuilder,
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
        final ListNodeBuilder<String, LeafSetEntryNode<String>> leafSetBuilder = Builders.<String>leafSetBuilder()
                .withNodeIdentifier(new NodeIdentifier(IetfYangLibrary.SPECIFIC_MODULE_FEATURE_LEAF_LIST_QNAME));
        for (final FeatureDefinition feature : features) {
            leafSetBuilder.withChildValue(feature.getQName().getLocalName());
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
     * @param monitoringModule ietf-restconf-monitoring module
     * @return mapped capabilites
     */
    public static ContainerNode mapCapabilites(final Module monitoringModule) {
        return Builders.containerBuilder()
            .withNodeIdentifier(new NodeIdentifier(RestconfState.QNAME))
            .withChild(Builders.containerBuilder()
                .withNodeIdentifier(new NodeIdentifier(Capabilities.QNAME))
                .withChild(Builders.<String>orderedLeafSetBuilder()
                    .withNodeIdentifier(new NodeIdentifier(CAPABILITY_QNAME))
                    .withChildValue(Rfc8040.Capabilities.DEPTH)
                    .withChildValue(Rfc8040.Capabilities.FIELDS)
                    .withChildValue(Rfc8040.Capabilities.FILTER)
                    .withChildValue(Rfc8040.Capabilities.REPLAY)
                    .withChildValue(Rfc8040.Capabilities.WITH_DEFAULTS)
                    .build())
                .build())
            .build();
    }

    /**
     * Map data of yang notification to normalized node according to ietf-restconf-monitoring.
     *
     * @param notifiQName qname of notification from listener
     * @param notifications list of notifications for find schema of notification by notifiQName
     * @param start start-time query parameter of notification
     * @param outputType output type of notification
     * @param uri location of registered listener for sending data of notification
     * @return mapped data of notification - map entry node if parent exists,
     *         container streams with list and map entry node if not
     */
    public static MapEntryNode mapYangNotificationStreamByIetfRestconfMonitoring(final QName notifiQName,
            final Collection<? extends NotificationDefinition> notifications, final Instant start,
            final String outputType, final URI uri) {
        for (final NotificationDefinition notificationDefinition : notifications) {
            if (notificationDefinition.getQName().equals(notifiQName)) {
                final String streamName = notifiQName.getLocalName();
                final DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> streamEntry =
                    Builders.mapEntryBuilder()
                        .withNodeIdentifier(NodeIdentifierWithPredicates.of(Stream.QNAME, NAME_QNAME, streamName))
                        .withChild(ImmutableNodes.leafNode(NAME_QNAME, streamName));

                notificationDefinition.getDescription().ifPresent(
                    desc -> streamEntry.withChild(ImmutableNodes.leafNode(DESCRIPTION_QNAME, desc)));
                streamEntry.withChild(ImmutableNodes.leafNode(REPLAY_SUPPORT_QNAME, Boolean.TRUE));
                if (start != null) {
                    streamEntry.withChild(ImmutableNodes.leafNode(REPLAY_LOG_CREATION_TIME,
                        DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(OffsetDateTime.ofInstant(start,
                            ZoneId.systemDefault()))));
                }

                return streamEntry
                    .withChild(createAccessList(outputType, uri))
                    .build();
            }
        }

        throw new RestconfDocumentedException(notifiQName + " doesn't exist in any modul");
    }

    private static MapNode createAccessList(final String outputType, final URI uriToWebsocketServer) {
        return Builders.mapBuilder()
            .withNodeIdentifier(new NodeIdentifier(Access.QNAME))
            .withChild(Builders.mapEntryBuilder()
                .withNodeIdentifier(NodeIdentifierWithPredicates.of(Access.QNAME, ENCODING_QNAME, outputType))
                .withChild(ImmutableNodes.leafNode(ENCODING_QNAME, outputType))
                .withChild(ImmutableNodes.leafNode(LOCATION_QNAME, uriToWebsocketServer.toString()))
                .build())
            .build();
    }

    /**
     * Map data of data change notification to normalized node according to ietf-restconf-monitoring.
     *
     * @param path path of data to listen on
     * @param start start-time query parameter of notification
     * @param outputType output type of notification
     * @param uri location of registered listener for sending data of notification
     * @param schemaContext schemaContext for parsing instance identifier to get schema node of data
     * @return mapped data of notification - map entry node if parent exists,
     *         container streams with list and map entry node if not
     */
    public static MapEntryNode mapDataChangeNotificationStreamByIetfRestconfMonitoring(
            final YangInstanceIdentifier path, final Instant start, final String outputType, final URI uri,
            final EffectiveModelContext schemaContext, final String streamName) {
        final SchemaNode schemaNode = ParserIdentifier
                .toInstanceIdentifier(ParserIdentifier.stringFromYangInstanceIdentifier(path, schemaContext),
                        schemaContext, Optional.empty())
                .getSchemaNode();
        final DataContainerNodeBuilder<NodeIdentifierWithPredicates, MapEntryNode> streamEntry =
            Builders.mapEntryBuilder()
                .withNodeIdentifier(NodeIdentifierWithPredicates.of(Stream.QNAME, NAME_QNAME, streamName))
                .withChild(ImmutableNodes.leafNode(NAME_QNAME, streamName));

        schemaNode.getDescription().ifPresent(desc ->
            streamEntry.withChild(ImmutableNodes.leafNode(DESCRIPTION_QNAME, desc)));

        return streamEntry
            .withChild(ImmutableNodes.leafNode(REPLAY_SUPPORT_QNAME, Boolean.TRUE))
            .withChild(ImmutableNodes.leafNode(REPLAY_LOG_CREATION_TIME,
                DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(OffsetDateTime.ofInstant(start, ZoneId.systemDefault()))))
            .withChild(createAccessList(outputType, uri))
            .build();
    }
}
