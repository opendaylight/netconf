/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams;

import static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.monitoring.rev170126.$YangModuleInfoImpl.qnameOf;

import com.google.common.annotations.VisibleForTesting;
import java.net.URI;
import java.util.Set;
import java.util.stream.Collectors;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.monitoring.rev170126.restconf.state.streams.Stream;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.monitoring.rev170126.restconf.state.streams.stream.Access;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContext;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextTree;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

/**
 * Utilities for creating the content of {@code /ietf-restconf-monitoring:restconf-state/streams}.
 */
public final class RestconfStateStreams {
    @VisibleForTesting
    static final QName DESCRIPTION_QNAME = qnameOf("description");
    @VisibleForTesting
    static final QName ENCODING_QNAME = qnameOf("encoding");
    @VisibleForTesting
    static final QName LOCATION_QNAME = qnameOf("location");
    static final QName NAME_QNAME = qnameOf("name");

    private RestconfStateStreams() {
        // Hidden on purpose
    }

    /**
     * Map data of YANG notification stream to a {@link MapEntryNode} according to {@code ietf-restconf-monitoring}.
     *
     * @param streamName stream name
     * @param qnames Notification QNames to listen on
     * @param outputType output type of notification
     * @param uri location of registered listener for sending data of notification
     * @return mapped data of notification - map entry node if parent exists,
     *         container streams with list and map entry node if not
     */
    public static MapEntryNode notificationStreamEntry(final String streamName, final Set<QName> qnames,
            final String outputType, final URI uri) {
        return Builders.mapEntryBuilder()
            .withNodeIdentifier(NodeIdentifierWithPredicates.of(Stream.QNAME, NAME_QNAME, streamName))
            .withChild(ImmutableNodes.leafNode(NAME_QNAME, streamName))
            .withChild(ImmutableNodes.leafNode(DESCRIPTION_QNAME, qnames.stream()
                .map(QName::toString)
                .collect(Collectors.joining(","))))
            .withChild(createAccessList(outputType, uri))
            .build();
    }

    /**
     * Map data of data change notification to normalized node according to ietf-restconf-monitoring.
     *
     * @param path path of data to listen on
     * @param outputType output type of notification
     * @param uri location of registered listener for sending data of notification
     * @param schemaContext schemaContext for parsing instance identifier to get schema node of data
     * @return mapped data of notification - map entry node if parent exists,
     *         container streams with list and map entry node if not
     */
    public static MapEntryNode dataChangeStreamEntry(final YangInstanceIdentifier path,
            final String outputType, final URI uri, final EffectiveModelContext schemaContext,
            final String streamName) {
        final var streamEntry = Builders.mapEntryBuilder()
                .withNodeIdentifier(NodeIdentifierWithPredicates.of(Stream.QNAME, NAME_QNAME, streamName))
                .withChild(ImmutableNodes.leafNode(NAME_QNAME, streamName));

        DataSchemaContextTree.from(schemaContext).findChild(path)
            .map(DataSchemaContext::dataSchemaNode)
            .flatMap(DataSchemaNode::getDescription)
            .ifPresent(desc -> streamEntry.withChild(ImmutableNodes.leafNode(DESCRIPTION_QNAME, desc)));

        return streamEntry
            .withChild(createAccessList(outputType, uri))
            .build();
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
}
