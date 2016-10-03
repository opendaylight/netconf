/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.utils.schema.context;

import com.google.common.base.Preconditions;
import java.util.Collection;
import java.util.Set;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorTag;
import org.opendaylight.netconf.sal.restconf.impl.RestconfError.ErrorType;
import org.opendaylight.restconf.Draft17;
import org.opendaylight.yangtools.yang.model.api.DataNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.GroupingDefinition;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;

/**
 * Util class for finding {@link DataSchemaNode}.
 *
 */
public final class RestconfSchemaUtil {

    private RestconfSchemaUtil() {
        throw new UnsupportedOperationException("Util class");
    }

    /**
     * Get {@link DataSchemaNode} from {@link Module} Restconf module by
     * {@link String} schema node name.
     *
     * @param restconfModule
     *            - restconf module
     * @param schemaNodeName
     *            - schema node name
     * @return {@link DataSchemaNode}
     */
    public static DataSchemaNode getRestconfSchemaNode(final Module restconfModule, final String schemaNodeName) {

        final Set<GroupingDefinition> groupings = restconfModule.getGroupings();
        final GroupingDefinition restGroup = findSchemaNodeInCollection(groupings,
                Draft17.RestconfModule.RESTCONF_GROUPING_SCHEMA_NODE);
        final Collection<DataSchemaNode> childNodes = restGroup.getChildNodes();
        final DataSchemaNode restCont = childNodes.iterator().next();

        return findSchemaNode(restCont, schemaNodeName);
    }

    /**
     * Find specific {@link DataSchemaNode} child in {@link DataNodeContainer}
     * by {@link String} schema node name.
     *
     * @param restCont
     *            - restconf container
     * @param schemaNodeName
     *            - schema node name
     * @return {@link DataSchemaNode}
     */
    private static DataSchemaNode findSchemaNode(final DataSchemaNode restCont, final String schemaNodeName) {
        switch (schemaNodeName) {
            //MODULES
            case Draft17.RestconfModule.MODULE_LIST_SCHEMA_NODE:
                final DataSchemaNode moduleListSchNode = findSchemaNodeInCollection(
                        ((DataNodeContainer) findSchemaNode(restCont,
                                Draft17.RestconfModule.MODULES_CONTAINER_SCHEMA_NODE)).getChildNodes(),
                        Draft17.RestconfModule.MODULE_LIST_SCHEMA_NODE);
                Preconditions.checkNotNull(moduleListSchNode);
                return moduleListSchNode;
            case Draft17.RestconfModule.MODULES_CONTAINER_SCHEMA_NODE:
                final DataSchemaNode modulesContSchNode = findSchemaNodeInCollection(((DataNodeContainer) restCont).getChildNodes(),
                        Draft17.RestconfModule.MODULES_CONTAINER_SCHEMA_NODE);
                Preconditions.checkNotNull(modulesContSchNode);
                return modulesContSchNode;

            //STREAMS
            case Draft17.MonitoringModule.STREAM_LIST_SCHEMA_NODE:
                final DataSchemaNode streamListSchNode = findSchemaNodeInCollection(
                        ((DataNodeContainer) findSchemaNode(restCont,
                                Draft17.MonitoringModule.STREAMS_CONTAINER_SCHEMA_NODE)).getChildNodes(),
                        Draft17.MonitoringModule.STREAM_LIST_SCHEMA_NODE);
                Preconditions.checkNotNull(streamListSchNode);
                return streamListSchNode;
            case Draft17.MonitoringModule.STREAMS_CONTAINER_SCHEMA_NODE:
                final DataSchemaNode streamsContSchNode = findSchemaNodeInCollection(
                        ((DataNodeContainer) restCont).getChildNodes(),
                        Draft17.MonitoringModule.STREAMS_CONTAINER_SCHEMA_NODE);
                Preconditions.checkNotNull(streamsContSchNode);
                return streamsContSchNode;
            default:
                throw new RestconfDocumentedException("Schema node " + schemaNodeName + " does not exist in module.",
                        ErrorType.PROTOCOL, ErrorTag.DATA_MISSING);
        }
    }

    /**
     * Find child of {@link SchemaNode} in {@link Collection} by {@link String}
     * schema node name.
     *
     * @param <T>
     *            - child of SchemaNode
     * @param collection
     *            - child of node
     * @param schemaNodeName
     *            - schema node name
     * @return {@link SchemaNode}
     */
    public static <T extends SchemaNode> T findSchemaNodeInCollection(final Collection<T> collection,
            final String schemaNodeName) {
        for (final T child : collection) {
            if (child.getQName().getLocalName().equals(schemaNodeName)) {
                return child;
            }
        }
        throw new RestconfDocumentedException("Schema node " + schemaNodeName + " does not exist in module.",
                ErrorType.PROTOCOL, ErrorTag.DATA_MISSING);
    }

}
