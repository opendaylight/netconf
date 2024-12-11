/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.notifications.mdsal;

import static java.util.Objects.requireNonNull;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.net.URI;
import java.util.UUID;
import javax.inject.Inject;
import javax.inject.Singleton;
import com.google.common.util.concurrent.MoreExecutors;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.restconf.server.api.ServerException;
import org.opendaylight.restconf.server.api.ServerRequest;
import org.opendaylight.restconf.server.spi.AbstractRestconfStreamRegistry;
import org.opendaylight.restconf.server.spi.RestconfStream;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.Streams;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.Subscriptions;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.streams.Stream;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This singleton class is responsible for creation, removal and searching for RFC 8639 {@link RestconfStream}s.
 */
@Singleton
@Component(service = RestconfStream.Registry.class)
public final class RestconfSubscriptionsStreamRegistry extends AbstractRestconfStreamRegistry {
    private static final Logger LOG = LoggerFactory.getLogger(RestconfSubscriptionsStreamRegistry.class);
    private static final YangInstanceIdentifier RFC8639_STREAMS = YangInstanceIdentifier.of(
        NodeIdentifier.create(Streams.QNAME),
        NodeIdentifier.create(Stream.QNAME));
    private static final String BASE_SUBSCRIPTION_LOCATION = Subscriptions.QNAME.toString();

    private final DOMDataBroker dataBroker;

    @Inject
    @Activate
    public RestconfSubscriptionsStreamRegistry(@Reference final DOMDataBroker dataBroker) {
        this.dataBroker = requireNonNull(dataBroker);
    }

    @Override
    public @NonNull ListenableFuture<?> putStream(final @NonNull MapEntryNode stream) {
        // Now issue a put operation
        final var tx = dataBroker.newWriteOnlyTransaction();
        tx.put(LogicalDatastoreType.OPERATIONAL, RFC8639_STREAMS.node(stream.name()), stream);
        return tx.commit();
    }

    @Override
    protected @NonNull ListenableFuture<?> deleteStream(final @NonNull NodeIdentifierWithPredicates streamName) {
        // Now issue a delete operation while the name is still protected by being associated in the map.
        final var tx = dataBroker.newWriteOnlyTransaction();
        tx.delete(LogicalDatastoreType.OPERATIONAL, RFC8639_STREAMS.node(streamName));
        return tx.commit();
    }

    @Override
    public <T> void createStream(final ServerRequest<RestconfStream<T>> request, final URI restconfURI,
            final RestconfStream.Source<T> source, final String description) {
        final var name = "urn:uuid:" + UUID.randomUUID();
        final var stream = new RestconfStream<>(this, source, name);
        if (description.isBlank()) {
            throw new IllegalArgumentException("Description must be descriptive");
        }

        Futures.addCallback(putStream(streamEntry(name, description, BASE_SUBSCRIPTION_LOCATION,
                stream.encodings())), new FutureCallback<Object>() {
            @Override
            public void onSuccess(final Object result) {
                LOG.debug("Stream {} added", name);
                request.completeWith(stream);
            }

            @Override
            public void onFailure(final Throwable cause) {
                LOG.debug("Failed to add stream {}", name, cause);
                removeStream(name, stream);
                request.completeWith(new ServerException("Failed to allocate stream " + name, cause));
            }
        }, MoreExecutors.directExecutor());
    }
}
