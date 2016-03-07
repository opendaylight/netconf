/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.rest.impl.services;

import java.util.List;
import java.util.Set;
import javax.ws.rs.core.UriInfo;
import org.opendaylight.netconf.sal.restconf.impl.InstanceIdentifierContext;
import org.opendaylight.netconf.sal.restconf.impl.NormalizedNodeContext;
import org.opendaylight.netconf.sal.restconf.impl.QueryParametersParser;
import org.opendaylight.netconf.sal.streams.listeners.Notificator;
import org.opendaylight.restconf.rest.api.Draft09;
import org.opendaylight.restconf.rest.api.connector.RestSchemaController;
import org.opendaylight.restconf.rest.api.services.RestconfStreamsService;
import org.opendaylight.restconf.rest.impl.services.modules.RestconfModul;
import org.opendaylight.restconf.utils.RestSchemaControllerUtil;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.CollectionNodeBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.DataContainerNodeAttrBuilder;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;

/*
 * TODO AFTER ADD NEW YANGS FOR RESTCONF TO THE MDSAL USE MONITORING MODULE FOR STREAMS (INSTEAD OF RESTCONF MODULE)
 */

public class RestconfStreamsServiceImpl extends RestconfModul implements RestconfStreamsService {

    public RestconfStreamsServiceImpl(final RestSchemaController restSchemaController) {
        super(restSchemaController);
    }

    @Override
    public NormalizedNodeContext getAvailableStreams(final UriInfo uriInfo) {
        final SchemaContext schemaContext = this.restSchemaController.getGlobalSchema();
        final Set<String> availableStreams = Notificator.getStreamNames();
        final Module restconfModule = getRestconfModule();
        final DataSchemaNode streamSchemaNode = this.restSchemaController
                .getRestconfModuleRestConfSchemaNode(restconfModule, Draft09.RestConfModule.STREAM_LIST_SCHEMA_NODE);
        Preconditions.checkState(streamSchemaNode instanceof ListSchemaNode);

        final CollectionNodeBuilder<MapEntryNode, MapNode> listStreamsBuilder = Builders
                .mapBuilder((ListSchemaNode) streamSchemaNode);

        for (final String streamName : availableStreams) {
            listStreamsBuilder.withChild(toStreamEntryNode(streamName, streamSchemaNode));
        }

        final DataSchemaNode streamsContainerSchemaNode = this.restSchemaController.getRestconfModuleRestConfSchemaNode(
                restconfModule, Draft09.RestConfModule.STREAMS_CONTAINER_SCHEMA_NODE);
        Preconditions.checkState(streamsContainerSchemaNode instanceof ContainerSchemaNode);

        final DataContainerNodeAttrBuilder<NodeIdentifier, ContainerNode> streamsContainerBuilder = Builders
                .containerBuilder((ContainerSchemaNode) streamsContainerSchemaNode);
        streamsContainerBuilder.withChild(listStreamsBuilder.build());

        return new NormalizedNodeContext(
                new InstanceIdentifierContext<>(null, streamsContainerSchemaNode, null, schemaContext),
                streamsContainerBuilder.build(), QueryParametersParser.parseWriterParameters(uriInfo));
    }

    private MapEntryNode toStreamEntryNode(final String streamName, final DataSchemaNode streamSchemaNode) {
        Preconditions.checkArgument(streamSchemaNode instanceof ListSchemaNode,
                "streamSchemaNode has to be of type ListSchemaNode");
        final ListSchemaNode listStreamSchemaNode = (ListSchemaNode) streamSchemaNode;
        final DataContainerNodeAttrBuilder<NodeIdentifierWithPredicates, MapEntryNode> streamNodeValues = Builders
                .mapEntryBuilder(listStreamSchemaNode);

        List<DataSchemaNode> instanceDataChildrenByName = RestSchemaControllerUtil
                .findInstanceDataChildrenByName((listStreamSchemaNode), "name");
        final DataSchemaNode nameSchemaNode = Iterables.getFirst(instanceDataChildrenByName, null);
        Preconditions.checkState(nameSchemaNode instanceof LeafSchemaNode);
        streamNodeValues.withChild(Builders.leafBuilder((LeafSchemaNode) nameSchemaNode).withValue(streamName).build());

        instanceDataChildrenByName = RestSchemaControllerUtil.findInstanceDataChildrenByName((listStreamSchemaNode),
                "description");
        final DataSchemaNode descriptionSchemaNode = Iterables.getFirst(instanceDataChildrenByName, null);
        Preconditions.checkState(descriptionSchemaNode instanceof LeafSchemaNode);
        streamNodeValues.withChild(
                Builders.leafBuilder((LeafSchemaNode) nameSchemaNode).withValue("DESCRIPTION_PLACEHOLDER").build());

        instanceDataChildrenByName = RestSchemaControllerUtil.findInstanceDataChildrenByName((listStreamSchemaNode),
                "replay-support");
        final DataSchemaNode replaySupportSchemaNode = Iterables.getFirst(instanceDataChildrenByName, null);
        Preconditions.checkState(replaySupportSchemaNode instanceof LeafSchemaNode);
        streamNodeValues.withChild(Builders.leafBuilder((LeafSchemaNode) replaySupportSchemaNode)
                .withValue(Boolean.valueOf(true)).build());

        instanceDataChildrenByName = RestSchemaControllerUtil.findInstanceDataChildrenByName((listStreamSchemaNode),
                "replay-log-creation-time");
        final DataSchemaNode replayLogCreationTimeSchemaNode = Iterables.getFirst(instanceDataChildrenByName, null);
        Preconditions.checkState(replayLogCreationTimeSchemaNode instanceof LeafSchemaNode);
        streamNodeValues.withChild(
                Builders.leafBuilder((LeafSchemaNode) replayLogCreationTimeSchemaNode).withValue("").build());

        instanceDataChildrenByName = RestSchemaControllerUtil.findInstanceDataChildrenByName((listStreamSchemaNode),
                "events");
        final DataSchemaNode eventsSchemaNode = Iterables.getFirst(instanceDataChildrenByName, null);
        Preconditions.checkState(eventsSchemaNode instanceof LeafSchemaNode);
        streamNodeValues.withChild(Builders.leafBuilder((LeafSchemaNode) eventsSchemaNode).withValue("").build());

        return streamNodeValues.build();
    }

}
