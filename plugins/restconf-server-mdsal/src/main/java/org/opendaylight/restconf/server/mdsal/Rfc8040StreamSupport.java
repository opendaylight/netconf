/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.mdsal;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import java.net.URI;
import java.util.Set;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteOperations;
import org.opendaylight.restconf.server.spi.RestconfStream;
import org.opendaylight.restconf.server.spi.RestconfStream.LocationProvider;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.monitoring.rev170126.RestconfState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.monitoring.rev170126.restconf.state.Streams;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.monitoring.rev170126.restconf.state.streams.Stream;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.monitoring.rev170126.restconf.state.streams.stream.Access;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link StreamSupport} for {@code ietf-restconf-monitoring} operational state.
 */
@NonNullByDefault
final class Rfc8040StreamSupport extends StreamSupport {
    private static final Logger LOG = LoggerFactory.getLogger(Rfc8040StreamSupport.class);

    @VisibleForTesting
    static final QName NAME_QNAME =  QName.create(Stream.QNAME, "name").intern();
    @VisibleForTesting
    static final QName DESCRIPTION_QNAME = QName.create(Stream.QNAME, "description").intern();
    @VisibleForTesting
    static final QName ENCODING_QNAME =  QName.create(Stream.QNAME, "encoding").intern();
    @VisibleForTesting
    static final QName LOCATION_QNAME =  QName.create(Stream.QNAME, "location").intern();

    private static final YangInstanceIdentifier RESTCONF_STATE_STREAMS = YangInstanceIdentifier.of(
        NodeIdentifier.create(RestconfState.QNAME),
        NodeIdentifier.create(Streams.QNAME),
        NodeIdentifier.create(Stream.QNAME));

    private final LocationProvider locationProvider;

    Rfc8040StreamSupport(final RestconfStream.LocationProvider locationProvider) {
        this.locationProvider = requireNonNull(locationProvider);
    }

    @Override
    void putStream(final DOMDataTreeWriteOperations transaction, final RestconfStream<?> stream,
            final String description, final @Nullable URI restconfURI) {
        // ietf-restconf-monitoring requires a location, if we do not have base location we use relative location
        final var baseStreamLocation = restconfURI != null ? locationProvider.baseStreamLocation(restconfURI)
            : locationProvider.relativeStreamLocation();
        final var entry = streamEntry(stream.name(), description, baseStreamLocation.toString(),
            stream.encodings());
        transaction.put(LogicalDatastoreType.OPERATIONAL, RESTCONF_STATE_STREAMS.node(entry.name()), entry);
    }

    @Override
    void deleteStream(final DOMDataTreeWriteOperations transaction, final String streamName) {
        transaction.delete(LogicalDatastoreType.OPERATIONAL,
            RESTCONF_STATE_STREAMS.node(NodeIdentifierWithPredicates.of(Stream.QNAME, NAME_QNAME, streamName)));
    }

    @VisibleForTesting
    static MapEntryNode streamEntry(final String name, final String description, final String baseStreamLocation,
            final Set<RestconfStream.EncodingName> encodings) {
        final var accessBuilder = ImmutableNodes.newSystemMapBuilder()
            .withNodeIdentifier(new NodeIdentifier(Access.QNAME));
        for (var encoding : encodings) {
            final var encodingName = encoding.name();
            accessBuilder.withChild(ImmutableNodes.newMapEntryBuilder()
                .withNodeIdentifier(NodeIdentifierWithPredicates.of(Access.QNAME, ENCODING_QNAME, encodingName))
                .withChild(ImmutableNodes.leafNode(ENCODING_QNAME, encodingName))
                .withChild(ImmutableNodes.leafNode(LOCATION_QNAME,
                    baseStreamLocation + '/' + encodingName + '/' + name))
                .build());
        }

        return ImmutableNodes.newMapEntryBuilder()
            .withNodeIdentifier(NodeIdentifierWithPredicates.of(Stream.QNAME, NAME_QNAME, name))
            .withChild(ImmutableNodes.leafNode(NAME_QNAME, name))
            .withChild(ImmutableNodes.leafNode(DESCRIPTION_QNAME, description))
            .withChild(accessBuilder.build())
            .build();
    }
}
