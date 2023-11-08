/*
 * Copyright © 2019 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.MoreExecutors;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.common.api.CommitInfo;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfFuture;
import org.opendaylight.restconf.common.errors.SettableRestconfFuture;
import org.opendaylight.restconf.nb.rfc8040.URLConstants;
import org.opendaylight.restconf.nb.rfc8040.streams.RestconfStream.EncodingName;
import org.opendaylight.restconf.nb.rfc8040.streams.RestconfStream.Source;
import org.opendaylight.restconf.nb.rfc8040.streams.dtcl.DataTreeChangeSource;
import org.opendaylight.restconf.nb.rfc8040.streams.notif.NotificationSource;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.monitoring.rev170126.RestconfState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.monitoring.rev170126.restconf.state.Streams;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.monitoring.rev170126.restconf.state.streams.Stream;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.monitoring.rev170126.restconf.state.streams.stream.Access;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This singleton class is responsible for creation, removal and searching for {@link DataTreeChangeSource} or
 * {@link NotificationSource} listeners.
 */
// FIXME: furthermore, this should be tied to ietf-restconf-monitoring, as the Strings used in its maps are stream
//        names. We essentially need a component which deals with allocation of stream names and their lifecycle and
//        the contents of /restconf-state/streams.
public abstract sealed class ListenersBroker {
    /**
     * A ListenersBroker working with Server-Sent Events.
     */
    public static final class ServerSentEvents extends ListenersBroker {
        public ServerSentEvents(final DOMDataBroker dataBroker) {
            super(dataBroker);
        }
    }

    /**
     * A ListenersBroker working with WebSockets.
     */
    public static final class WebSockets extends ListenersBroker {
        public WebSockets(final DOMDataBroker dataBroker) {
            super(dataBroker);
        }

        @Override
        String streamsScheme(final URI baseURI) {
            return switch (super.streamsScheme(baseURI)) {
                // Secured HTTP goes to Secured WebSockets
                case "https" -> "wss";
                // Unsecured HTTP and others go to unsecured WebSockets
                default -> "ws";
            };
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(ListenersBroker.class);
    private static final YangInstanceIdentifier RESTCONF_STATE_STREAMS = YangInstanceIdentifier.of(
        NodeIdentifier.create(RestconfState.QNAME),
        NodeIdentifier.create(Streams.QNAME),
        NodeIdentifier.create(Stream.QNAME));

    @VisibleForTesting
    static final QName NAME_QNAME =  QName.create(Stream.QNAME, "name").intern();
    @VisibleForTesting
    static final QName DESCRIPTION_QNAME = QName.create(Stream.QNAME, "description").intern();
    @VisibleForTesting
    static final QName ENCODING_QNAME =  QName.create(Stream.QNAME, "encoding").intern();
    @VisibleForTesting
    static final QName LOCATION_QNAME =  QName.create(Stream.QNAME, "location").intern();

    private final ConcurrentMap<String, RestconfStream<?>> streams = new ConcurrentHashMap<>();
    private final DOMDataBroker dataBroker;

    private ListenersBroker(final DOMDataBroker dataBroker) {
        this.dataBroker = requireNonNull(dataBroker);
    }

    /**
     * Get a {@link RestconfStream} by its name.
     *
     * @param streamName Stream name.
     * @return A {@link RestconfStream}, or {@code null} if the stream with specified name does not exist.
     * @throws NullPointerException if {@code streamName} is {@code null}
     */
    public final @Nullable RestconfStream<?> getStream(final String streamName) {
        return streams.get(streamName);
    }

    /**
     * Create a {@link RestconfStream} with a unique name. This method will atomically generate a stream name, create
     * the corresponding instance and register it.
     *
     * @param <T> Stream type
     * @param baseStreamLocation base streams location
     * @param factory Factory for creating the actual stream instance
     * @return A {@link RestconfStream} instance
     * @throws NullPointerException if {@code factory} is {@code null}
     */
    public final <T> @NonNull RestconfFuture<RestconfStream<T>> createStream(final String description,
            final String baseStreamLocation, final Source<T> source) {
        final var stream = allocateStream(source);
        final var name = stream.name();

        // Now issue a put operation
        final var ret = new SettableRestconfFuture<RestconfStream<T>>();
        final var tx = dataBroker.newWriteOnlyTransaction();
        tx.put(LogicalDatastoreType.OPERATIONAL, restconfStateStreamPath(name),
            streamEntry(name, description, baseStreamLocation, stream.encodings()));
        tx.commit().addCallback(new FutureCallback<CommitInfo>() {
            @Override
            public void onSuccess(final CommitInfo result) {
                LOG.debug("Stream {} added", name);
                ret.set(stream);
            }

            @Override
            public void onFailure(final Throwable cause) {
                LOG.debug("Failed to add stream {}", name, cause);
                streams.remove(name, stream);
                ret.setFailure(new RestconfDocumentedException("Failed to allocate stream " + name, cause));
            }
        }, MoreExecutors.directExecutor());
        return ret;
    }

    private <T> @NonNull RestconfStream<T> allocateStream(final Source<T> source) {
        String name;
        RestconfStream<T> stream;
        do {
            // Use Type 4 (random) UUID. While we could just use it as a plain string, be nice to observers and anchor
            // it into UUID URN namespace as defined by RFC4122
            name = "urn:uuid:" + UUID.randomUUID().toString();
            stream = new RestconfStream<>(this, source, name);
        } while (streams.putIfAbsent(name, stream) != null);

        return stream;
    }

    /**
     * Remove a particular stream and remove its entry from operational datastore.
     *
     * @param stream Stream to remove
     */
    final void removeStream(final RestconfStream<?> stream) {
        // Defensive check to see if we are still tracking the stream
        final var streamName = stream.name();
        if (streams.get(streamName) != stream) {
            LOG.warn("Stream {} does not match expected instance {}, skipping datastore update", streamName, stream);
            return;
        }

        // Now issue a delete operation while the name is still protected by being associated in the map.
        final var tx = dataBroker.newWriteOnlyTransaction();
        tx.delete(LogicalDatastoreType.OPERATIONAL, restconfStateStreamPath(streamName));
        tx.commit().addCallback(new FutureCallback<CommitInfo>() {
            @Override
            public void onSuccess(final CommitInfo result) {
                LOG.debug("Stream {} removed", streamName);
                streams.remove(streamName, stream);
            }

            @Override
            public void onFailure(final Throwable cause) {
                LOG.warn("Failed to remove stream {}, operational datastore may be inconsistent", streamName, cause);
                streams.remove(streamName, stream);
            }
        }, MoreExecutors.directExecutor());
    }

    private static @NonNull YangInstanceIdentifier restconfStateStreamPath(final String streamName) {
        return RESTCONF_STATE_STREAMS.node(NodeIdentifierWithPredicates.of(Stream.QNAME, NAME_QNAME, streamName));
    }

    /**
     * Return the base location URL of the streams service based on request URI.
     *
     * @param baseURI request base URI
     * @throws IllegalArgumentException if the result would have been malformed
     */
    public final @NonNull String baseStreamLocation(final URI baseURI) {
        try {
            return new URI(streamsScheme(baseURI), baseURI.getRawUserInfo(), baseURI.getHost(), baseURI.getPort(),
                URLConstants.BASE_PATH + '/' + URLConstants.STREAMS_SUBPATH, null, null)
                .toString();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Cannot derive streams location", e);
        }
    }

    String streamsScheme(final URI baseURI) {
        return baseURI.getScheme();
    }

    @VisibleForTesting
    static @NonNull MapEntryNode streamEntry(final String name, final String description,
            final String baseStreamLocation, final Set<EncodingName> encodings) {
        final var accessBuilder = Builders.mapBuilder().withNodeIdentifier(new NodeIdentifier(Access.QNAME));
        for (var encoding : encodings) {
            final var encodingName = encoding.name();
            accessBuilder.withChild(Builders.mapEntryBuilder()
                .withNodeIdentifier(NodeIdentifierWithPredicates.of(Access.QNAME, ENCODING_QNAME, encodingName))
                .withChild(ImmutableNodes.leafNode(ENCODING_QNAME, encodingName))
                .withChild(ImmutableNodes.leafNode(LOCATION_QNAME,
                    baseStreamLocation + '/' + encodingName + '/' + name))
                .build());
        }

        return Builders.mapEntryBuilder()
            .withNodeIdentifier(NodeIdentifierWithPredicates.of(Stream.QNAME, NAME_QNAME, name))
            .withChild(ImmutableNodes.leafNode(NAME_QNAME, name))
            .withChild(ImmutableNodes.leafNode(DESCRIPTION_QNAME, description))
            .withChild(accessBuilder.build())
            .build();
    }
}
