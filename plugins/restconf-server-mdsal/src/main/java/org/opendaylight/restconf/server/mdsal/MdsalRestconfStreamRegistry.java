/*
 * Copyright © 2019 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.mdsal;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.net.URI;
import java.util.Set;
import java.util.UUID;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.restconf.server.api.ServerException;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.restconf.server.spi.AbstractRestconfStreamRegistry;
import org.opendaylight.restconf.server.spi.RestconfStream;
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
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This singleton class is responsible for creation, removal and searching for {@link RestconfStream}s.
 */
@Singleton
@Component(service = RestconfStream.Registry.class)
public final class MdsalRestconfStreamRegistry extends AbstractRestconfStreamRegistry {
    @VisibleForTesting
    public static final QName NAME_QNAME =  QName.create(Stream.QNAME, "name").intern();
    @VisibleForTesting
    public static final QName DESCRIPTION_QNAME = QName.create(Stream.QNAME, "description").intern();
    @VisibleForTesting
    public static final QName ENCODING_QNAME =  QName.create(Stream.QNAME, "encoding").intern();
    @VisibleForTesting
    public static final QName LOCATION_QNAME =  QName.create(Stream.QNAME, "location").intern();

    private static final Logger LOG = LoggerFactory.getLogger(MdsalRestconfStreamRegistry.class);
    private static final YangInstanceIdentifier RESTCONF_STATE_STREAMS = YangInstanceIdentifier.of(
        NodeIdentifier.create(RestconfState.QNAME),
        NodeIdentifier.create(Streams.QNAME),
        NodeIdentifier.create(Stream.QNAME));

    private final DOMDataBroker dataBroker;
    private final RestconfStream.LocationProvider locationProvider;

    @Inject
    @Activate
    public MdsalRestconfStreamRegistry(@Reference final RestconfStream.LocationProvider locationProvider,
            @Reference final DOMDataBroker dataBroker) {
        super(Stream.QNAME);
        this.locationProvider = requireNonNull(locationProvider);
        this.dataBroker = requireNonNull(dataBroker);
    }

    @Override
    public ListenableFuture<?> putStream(final MapEntryNode stream) {
        // Now issue a put operation
        final var tx = dataBroker.newWriteOnlyTransaction();
        tx.put(LogicalDatastoreType.OPERATIONAL, RESTCONF_STATE_STREAMS.node(stream.name()), stream);
        return tx.commit();
    }

    @Override
    protected ListenableFuture<?> deleteStream(final NodeIdentifierWithPredicates streamName) {
        // Now issue a delete operation while the name is still protected by being associated in the map.
        final var tx = dataBroker.newWriteOnlyTransaction();
        tx.delete(LogicalDatastoreType.OPERATIONAL, RESTCONF_STATE_STREAMS.node(streamName));
        return tx.commit();
    }

    @Override
    public <T> void createStream(final ServerRequest<RestconfStream<T>> request, final URI restconfURI,
            final RestconfStream.Source<T> source, final String description) {
        final var baseStreamLocation = locationProvider.baseStreamLocation(restconfURI);
        final var stream = allocateStream(source);
        final var name = stream.name();
        if (description.isBlank()) {
            throw new IllegalArgumentException("Description must be descriptive");
        }

        Futures.addCallback(putStream(streamEntry(name, description, baseStreamLocation.toString(),
            stream.encodings())), new FutureCallback<Object>() {
                @Override
                public void onSuccess(final Object result) {
                    LOG.debug("Stream {} added", name);
                    request.completeWith(stream);
                }

                @Override
                public void onFailure(final Throwable cause) {
                    LOG.debug("Failed to add stream {}", name, cause);
                    unregisterStream(name, stream);
                    request.completeWith(new ServerException("Failed to allocate stream " + name, cause));
                }
            }, MoreExecutors.directExecutor());
    }

    @Override
    public <T> void createStream(String name, RestconfStream.Source<T> source, String description) {
        final var stream = new RestconfStream<>(this, source, name);
        registerStream(name, stream);
        if (description.isBlank()) {
            throw new IllegalArgumentException("Description must be descriptive");
        }

        Futures.addCallback(putStream(streamEntry(name, description, locationProvider.relativeStreamLocation().toString(),
                stream.encodings())), new FutureCallback<Object>() {
            @Override
            public void onSuccess(final Object result) {
                LOG.debug("Stream {} added", name);
            }

            @Override
            public void onFailure(final Throwable cause) {
                LOG.debug("Failed to add stream {}", name, cause);
                unregisterStream(name, stream);
            }
        }, MoreExecutors.directExecutor());
    }


    @VisibleForTesting
    public static @NonNull MapEntryNode streamEntry(final String name, final String description,
            final String baseStreamLocation, final Set<RestconfStream.EncodingName> encodings) {
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

    protected <T> RestconfStream<T> allocateStream(final RestconfStream.Source<T> source) {
        String name;
        RestconfStream<T> stream;
        do {
            // Use Type 4 (random) UUID. While we could just use it as a plain string, be nice to observers and anchor
            // it into UUID URN namespace as defined by RFC4122
            name = "urn:uuid:" + UUID.randomUUID();
            stream = new RestconfStream<>(this, source, name);
        } while (registerStream(name, stream) != null);

        return stream;
    }
}
