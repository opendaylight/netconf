/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.rest.impl.services;

import java.util.Set;
import javax.ws.rs.core.UriInfo;
import org.opendaylight.netconf.sal.restconf.impl.InstanceIdentifierContext;
import org.opendaylight.netconf.sal.restconf.impl.NormalizedNodeContext;
import org.opendaylight.netconf.sal.streams.listeners.Notificator;
import org.opendaylight.restconf.Draft09;
import org.opendaylight.restconf.rest.api.schema.context.SchemaContextHandler;
import org.opendaylight.restconf.rest.api.services.RestconfStreamsService;
import org.opendaylight.restconf.utils.mapping.RestconfMappingNodeUtil;
import org.opendaylight.restconf.utils.schema.context.RestconfSchemaUtil;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.CollectionNodeBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.DataContainerNodeAttrBuilder;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import com.google.common.base.Preconditions;

/**
 * Implementation of {@link RestconfStreamsService}
 *
 */
public class RestconfStreamsServiceImpl implements RestconfStreamsService {

    private final SchemaContextHandler schemaContextHandler;

    public RestconfStreamsServiceImpl(final SchemaContextHandler schemaContextHandler) {
        this.schemaContextHandler = schemaContextHandler;
    }

    @Override
    public NormalizedNodeContext getAvailableStreams(final UriInfo uriInfo) {
        final SchemaContextRef schemaContextRef = new SchemaContextRef(this.schemaContextHandler.getSchemaContext());
        final Set<String> availableStreams = Notificator.getStreamNames();

        final DataSchemaNode streamListSchemaNode = RestconfSchemaUtil.getRestconfSchemaNode(
                schemaContextRef.getRestconfModule(), Draft09.MonitoringModule.STREAM_LIST_SCHEMA_NODE);
        Preconditions.checkState(streamListSchemaNode instanceof ListSchemaNode);
        final CollectionNodeBuilder<MapEntryNode, MapNode> listStreamBuilder = Builders
                .mapBuilder((ListSchemaNode) streamListSchemaNode);

        for (final String streamValue : availableStreams) {
            listStreamBuilder.withChild(RestconfMappingNodeUtil.toStreamEntryNode(streamValue, streamListSchemaNode));
        }

        final DataSchemaNode streamContSchemaNode = RestconfSchemaUtil.getRestconfSchemaNode(
                schemaContextRef.getRestconfModule(), Draft09.MonitoringModule.STREAMS_CONTAINER_SCHEMA_NODE);
        Preconditions.checkState(streamContSchemaNode instanceof ContainerSchemaNode);
        final DataContainerNodeAttrBuilder<NodeIdentifier, ContainerNode> streamsContainerBuilder = Builders
                .containerBuilder((ContainerSchemaNode) streamContSchemaNode);

        streamsContainerBuilder.withChild(listStreamBuilder.build());

        return new NormalizedNodeContext(
                new InstanceIdentifierContext<>(null, streamContSchemaNode, null, schemaContextRef.get()),
                streamsContainerBuilder.build());
    }
}
