/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.utils.schema.context;

import java.util.Collection;
import java.util.Set;
import org.opendaylight.netconf.sal.rest.api.Draft02;
import org.opendaylight.restconf.Draft09;
import org.opendaylight.yangtools.yang.model.api.DataNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.GroupingDefinition;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import com.google.common.base.Preconditions;

public class RestconfSchemaUtil {

    private RestconfSchemaUtil() {
        throw new UnsupportedOperationException("Util class");
    }

    /**
     * Get {@link DataSchemaNode} from {@link Module} Restconf module by
     * {@link String} schema node name.
     *
     * @param restconfModule
     * @param schemaNodeName
     * @return {@link DataSchemaNode}
     */
    public static DataSchemaNode getRestconfSchemaNode(final Module restconfModule, final String schemaNodeName) {

        final Set<GroupingDefinition> groupings = restconfModule.getGroupings();
        final GroupingDefinition restGroup = findSchemaNodeInCollection(groupings,
                Draft02.RestConfModule.RESTCONF_GROUPING_SCHEMA_NODE);
        final Collection<DataSchemaNode> childNodes = restGroup.getChildNodes();
        final DataSchemaNode restCont = childNodes.iterator().next();

        return findSchemaNode(restCont, schemaNodeName);
    }

    /**
     * Find specific {@link DataSchemaNode} child in {@link DataNodeContainer}
     * by {@link String} schema node name.
     *
     * @param restCont
     * @param schemaNodeName
     * @return {@link DataSchemaNode}
     */
    private static DataSchemaNode findSchemaNode(final DataSchemaNode restCont, final String schemaNodeName) {
        switch (schemaNodeName) {
            //MODULES
            case Draft09.RestconfModule.MODULE_LIST_SCHEMA_NODE:
                final DataSchemaNode moduleListSchNode = findSchemaNodeInCollection(
                        ((DataNodeContainer) findSchemaNode(restCont,
                                Draft09.RestconfModule.MODULES_CONTAINER_SCHEMA_NODE)).getChildNodes(),
                        Draft09.RestconfModule.MODULE_LIST_SCHEMA_NODE);
                Preconditions.checkNotNull(moduleListSchNode);
                return moduleListSchNode;
            case Draft09.RestconfModule.MODULES_CONTAINER_SCHEMA_NODE:
                final DataSchemaNode modulesContSchNode = findSchemaNodeInCollection(((DataNodeContainer) restCont).getChildNodes(),
                        Draft09.RestconfModule.MODULES_CONTAINER_SCHEMA_NODE);
                Preconditions.checkNotNull(modulesContSchNode);
                return modulesContSchNode;
            //STREAMS
            case Draft09.MonitoringModule.STREAM_LIST_SCHEMA_NODE:
                final DataSchemaNode streamListSchNode = findSchemaNodeInCollection(
                        ((DataNodeContainer) findSchemaNode(restCont,
                                Draft09.MonitoringModule.STREAMS_CONTAINER_SCHEMA_NODE)).getChildNodes(),
                        Draft09.MonitoringModule.STREAM_LIST_SCHEMA_NODE);
                Preconditions.checkNotNull(streamListSchNode);
                return streamListSchNode;
            case Draft09.MonitoringModule.STREAMS_CONTAINER_SCHEMA_NODE:
                final DataSchemaNode streamsContSchNode = findSchemaNodeInCollection(
                        ((DataNodeContainer) restCont).getChildNodes(),
                        Draft09.MonitoringModule.STREAMS_CONTAINER_SCHEMA_NODE);
                Preconditions.checkNotNull(streamsContSchNode);
                return streamsContSchNode;
            default:
                return null;
        }
    }

    /**
     * Find child of {@link SchemaNode} in {@link Collection} by {@link String}
     * schema node name.
     * 
     * @param collection
     * @param nameSchemaNode
     * @return {@link SchemaNode}
     */
    public static <T extends SchemaNode> T findSchemaNodeInCollection(final Collection<T> collection,
            final String nameSchemaNode) {
        for (final T child : collection) {
            if (child.getQName().getLocalName().equals(nameSchemaNode)) {
                return child;
            }
        }
        throw new NullPointerException("Schema node " + nameSchemaNode + " does not exist in module.");
    }

}
