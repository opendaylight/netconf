/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.spi;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.common.errors.RestconfFuture;
import org.opendaylight.restconf.common.errors.SettableRestconfFuture;
import org.opendaylight.restconf.nb.rfc8040.URLConstants;
import org.opendaylight.restconf.server.spi.RestconfStream.EncodingName;
import org.opendaylight.restconf.server.spi.RestconfStream.Source;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.monitoring.rev170126.restconf.state.streams.Stream;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.monitoring.rev170126.restconf.state.streams.stream.Access;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reference base class for {@link RestconfStream.Registry} implementations.
 */
public abstract class AbstractRestconfStreamRegistry implements RestconfStream.Registry {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractRestconfStreamRegistry.class);

    @VisibleForTesting
    public static final QName NAME_QNAME =  QName.create(Stream.QNAME, "name").intern();
    @VisibleForTesting
    public static final QName DESCRIPTION_QNAME = QName.create(Stream.QNAME, "description").intern();
    @VisibleForTesting
    public static final QName ENCODING_QNAME =  QName.create(Stream.QNAME, "encoding").intern();
    @VisibleForTesting
    public static final QName LOCATION_QNAME =  QName.create(Stream.QNAME, "location").intern();

    private final ConcurrentMap<String, RestconfStream<?>> streams = new ConcurrentHashMap<>();
    private final boolean useWebsockets;

    protected AbstractRestconfStreamRegistry(final boolean useWebsockets) {
        this.useWebsockets = useWebsockets;
    }

    @Override
    public final @Nullable RestconfStream<?> lookupStream(final String name) {
        return streams.get(requireNonNull(name));
    }

    @Override
    public final <T> RestconfFuture<RestconfStream<T>> createStream(final URI restconfURI, final Source<T> source,
            final String description) {
        final var baseStreamLocation = baseStreamLocation(restconfURI);
        final var stream = allocateStream(source);
        final var name = stream.name();
        if (description.isBlank()) {
            throw new IllegalArgumentException("Description must be descriptive");
        }

        final var ret = new SettableRestconfFuture<RestconfStream<T>>();
        Futures.addCallback(putStream(streamEntry(name, description, baseStreamLocation, stream.encodings())),
            new FutureCallback<Object>() {
                @Override
                public void onSuccess(final Object result) {
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

    private <T> RestconfStream<T> allocateStream(final Source<T> source) {
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

    protected abstract @NonNull ListenableFuture<?> putStream(@NonNull MapEntryNode stream);

    /**
     * Remove a particular stream and remove its entry from operational datastore.
     *
     * @param stream Stream to remove
     */
    final void removeStream(final RestconfStream<?> stream) {
        // Defensive check to see if we are still tracking the stream
        final var name = stream.name();
        if (streams.get(name) != stream) {
            LOG.warn("Stream {} does not match expected instance {}, skipping datastore update", name, stream);
            return;
        }

        Futures.addCallback(deleteStream(NodeIdentifierWithPredicates.of(Stream.QNAME, NAME_QNAME, name)),
            new FutureCallback<Object>() {
                @Override
                public void onSuccess(final Object result) {
                    LOG.debug("Stream {} removed", name);
                    streams.remove(name, stream);
                }

                @Override
                public void onFailure(final Throwable cause) {
                    LOG.warn("Failed to remove stream {}, operational datastore may be inconsistent", name, cause);
                    streams.remove(name, stream);
                }
            }, MoreExecutors.directExecutor());
    }

    protected abstract @NonNull ListenableFuture<?> deleteStream(@NonNull NodeIdentifierWithPredicates streamName);

    /**
     * Return the base location URL of the streams service based on request URI.
     *
     * @param restconfURI request base URI
     * @throws IllegalArgumentException if the result would have been malformed
     */
    protected final @NonNull String baseStreamLocation(final URI restconfURI) {
        var scheme = restconfURI.getScheme();
        if (useWebsockets) {
            scheme = switch (scheme) {
                // Secured HTTP goes to Secured WebSockets
                case "https" -> "wss";
                // Unsecured HTTP and others go to unsecured WebSockets
                default -> "ws";
            };
        }

        try {
            return new URI(scheme, restconfURI.getRawUserInfo(), restconfURI.getHost(), restconfURI.getPort(),
                restconfURI.getPath() + '/' + URLConstants.STREAMS_SUBPATH, null, null)
                .toString();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Cannot derive streams location", e);
        }
    }

    @VisibleForTesting
    public static final @NonNull MapEntryNode streamEntry(final String name, final String description,
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
