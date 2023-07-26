/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.monitoring;

import static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.monitoring.rev170126.$YangModuleInfoImpl.qnameOf;

import com.google.common.annotations.VisibleForTesting;
import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.monitoring.rev170126.RestconfState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.monitoring.rev170126.restconf.state.Streams;
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
    private static final YangInstanceIdentifier RESTCONF_STATE_STREAMS = YangInstanceIdentifier.of(
        NodeIdentifier.create(RestconfState.QNAME), NodeIdentifier.create(Streams.QNAME),
        NodeIdentifier.create(Stream.QNAME));

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

    private RestconfStateStreams() {
        // Hidden on purpose
    }

    public static @NonNull YangInstanceIdentifier restconfStateStreamPath(final String streamName) {
        return restconfStateStreamPath(NodeIdentifierWithPredicates.of(Stream.QNAME, NAME_QNAME, streamName));
    }

    public static @NonNull YangInstanceIdentifier restconfStateStreamPath(final NodeIdentifierWithPredicates arg) {
        return RESTCONF_STATE_STREAMS.node(arg);
    }

    /**
     * Map data of yang notification to normalized node according to ietf-restconf-monitoring.
     *
     * @param context the {@link EffectiveModelContext}
     * @param notifiQName qname of notification from listener
     * @param start start-time query parameter of notification
     * @param outputType output type of notification
     * @param uri location of registered listener for sending data of notification
     * @return mapped data of notification - map entry node if parent exists,
     *         container streams with list and map entry node if not
     */
    public static MapEntryNode notificationStreamEntry(final EffectiveModelContext context, final QName notifiQName,
            final Instant start, final String outputType, final URI uri) {
        final var notificationDefinition = context.findNotification(notifiQName)
            .orElseThrow(() -> new RestconfDocumentedException(notifiQName + " not found"));

        final String streamName = notifiQName.getLocalName();
        final var streamEntry = Builders.mapEntryBuilder()
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

        return streamEntry.withChild(createAccessList(outputType, uri)).build();
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
    public static MapEntryNode dataChangeStreamEntry(final YangInstanceIdentifier path, final Instant start,
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
            .withChild(ImmutableNodes.leafNode(REPLAY_SUPPORT_QNAME, Boolean.TRUE))
            .withChild(ImmutableNodes.leafNode(REPLAY_LOG_CREATION_TIME,
                DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(OffsetDateTime.ofInstant(start, ZoneId.systemDefault()))))
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
