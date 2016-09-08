/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.utils.mapping;

import com.google.common.base.Preconditions;
import java.util.Collection;
import java.util.Set;
import org.opendaylight.restconf.Draft16;
import org.opendaylight.restconf.utils.RestconfConstants;
import org.opendaylight.restconf.utils.schema.context.RestconfSchemaUtil;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.CollectionNodeBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.DataContainerNodeAttrBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.ListNodeBuilder;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.FeatureDefinition;
import org.opendaylight.yangtools.yang.model.api.LeafListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;

/**
 * Util class for mapping nodes
 *
 */
public final class RestconfMappingNodeUtil {

    private RestconfMappingNodeUtil() {
        throw new UnsupportedOperationException("Util class");
    }

    /**
     * Mapping {@link Module} from {@link Set} of {@link Module} to
     * {@link ListSchemaNode} of {@link Module} list.
     *
     * @param restconfModule
     *            - restconf module
     * @param modules
     *            - all modules
     * @return {@link MapNode}
     */
    public static MapNode restconfMappingNode(final Module restconfModule, final Set<Module> modules) {
        final DataSchemaNode modulListSchemaNode = RestconfSchemaUtil.getRestconfSchemaNode(restconfModule,
                Draft16.RestconfModule.MODULE_LIST_SCHEMA_NODE);
        Preconditions.checkState(modulListSchemaNode instanceof ListSchemaNode);

        final CollectionNodeBuilder<MapEntryNode, MapNode> listModuleBuilder = Builders
                .mapBuilder((ListSchemaNode) modulListSchemaNode);
        for (final Module module : modules) {
            listModuleBuilder.withChild(toModuleEntryNode(module, modulListSchemaNode));
        }
        return listModuleBuilder.build();
    }

    /**
     * Mapping {@link MapEntryNode} entries of {@link Module} to
     * {@link ListSchemaNode}.
     *
     * @param module
     *            - module for mapping
     * @param modulListSchemaNode
     *            - mapped {@link DataSchemaNode}
     * @return {@link MapEntryNode}
     */
    private static MapEntryNode toModuleEntryNode(final Module module, final DataSchemaNode modulListSchemaNode) {
        final ListSchemaNode listSchemaNode = (ListSchemaNode) modulListSchemaNode;
        final Collection<DataSchemaNode> childListSchemaNode = listSchemaNode.getChildNodes();
        final DataContainerNodeAttrBuilder<NodeIdentifierWithPredicates, MapEntryNode> moduleNodeValues = Builders
                .mapEntryBuilder(listSchemaNode);

        // MODULE NAME SCHEMA NODE
        fillListWithLeaf(listSchemaNode, moduleNodeValues, RestconfMappingNodeConstants.NAME, module.getName());

        // MODULE REVISION SCHEMA NODE
        fillListWithLeaf(listSchemaNode, moduleNodeValues, RestconfMappingNodeConstants.REVISION,
                RestconfConstants.REVISION_FORMAT.format(module.getRevision()));

        // MODULE NAMESPACE SCHEMA NODE
        fillListWithLeaf(listSchemaNode, moduleNodeValues, RestconfMappingNodeConstants.NAMESPACE,
                module.getNamespace().toString());

        // MODULE FEATURES SCHEMA NODES
        final DataSchemaNode schemaNode = RestconfSchemaUtil.findSchemaNodeInCollection(childListSchemaNode,
                RestconfMappingNodeConstants.FEATURE);
        Preconditions.checkState(schemaNode instanceof LeafListSchemaNode);
        final ListNodeBuilder<Object, LeafSetEntryNode<Object>> featureBuilder = Builders
                .leafSetBuilder((LeafListSchemaNode) schemaNode);
        for (final FeatureDefinition feature : module.getFeatures()) {
            featureBuilder.withChild(Builders.leafSetEntryBuilder((LeafListSchemaNode) schemaNode)
                    .withValue(feature.getQName().getLocalName()).build());
        }
        moduleNodeValues.withChild(featureBuilder.build());

        return moduleNodeValues.build();
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
}
