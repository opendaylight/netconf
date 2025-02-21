/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.mdsal;

import com.google.common.annotations.VisibleForTesting;
import java.net.URI;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteOperations;
import org.opendaylight.restconf.server.spi.RestconfStream;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.Streams;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.streams.Stream;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;

/**
 * {@link StreamSupport} for {@code ietf-subscribed-notifications} operational state.
 */
@NonNullByDefault
final class Rfc8639StreamSupport extends StreamSupport {
    private static final QName STREAM_NAME_QNAME =  QName.create(Stream.QNAME, "name").intern();
    private static final QName STREAM_DESCRIPTION_QNAME = QName.create(Stream.QNAME, "description").intern();
    private static final YangInstanceIdentifier RFC8639_STREAMS = YangInstanceIdentifier.of(
        NodeIdentifier.create(Streams.QNAME), NodeIdentifier.create(Stream.QNAME));

    @Override
    void putStream(final DOMDataTreeWriteOperations transaction, final RestconfStream<?> stream,
            final String description, final @Nullable URI restconfURI) {
        final var entry = streamEntry(stream, description);
        transaction.put(LogicalDatastoreType.OPERATIONAL, RFC8639_STREAMS.node(entry.name()), entry);
    }

    @Override
    void deleteStream(final DOMDataTreeWriteOperations transaction, final String streamName) {
        transaction.delete(LogicalDatastoreType.OPERATIONAL,
            RFC8639_STREAMS.node(NodeIdentifierWithPredicates.of(Stream.QNAME, STREAM_NAME_QNAME, streamName)));
    }

    @VisibleForTesting
    private static MapEntryNode streamEntry(final RestconfStream<?> stream, final String description) {
        final var streamName = stream.name();
        return ImmutableNodes.newMapEntryBuilder()
            .withNodeIdentifier(NodeIdentifierWithPredicates.of(Stream.QNAME, STREAM_NAME_QNAME, streamName))
            .withChild(ImmutableNodes.leafNode(STREAM_NAME_QNAME, streamName))
            .withChild(ImmutableNodes.leafNode(STREAM_DESCRIPTION_QNAME, description))
            // FIXME: way more information
            .build();
    }
}
