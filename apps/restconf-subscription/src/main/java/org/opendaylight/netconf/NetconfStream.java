/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.restconf.notifications.mdsal.RestconfSubscriptionsStreamRegistry;
import org.opendaylight.restconf.server.spi.RestconfStream;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.streams.Stream;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This singleton class is responsible for creation of netconf subscriptions stream.
 */
@Singleton
@Component
public final class NetconfStream {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfStream.class);
    private static final QName NAME_QNAME = QName.create(Stream.QNAME, "name").intern();
    private static final QName DESCRIPTION_QNAME = QName.create(Stream.QNAME, "description").intern();
    private static final QName REPLAY_LOG_CREATION_TIME_QNAME = QName.create(Stream.QNAME, "replay-log-creation-time");
    private static final String NAME = "NETCONF";
    private static final String DESCRIPTION = "Stream for subscription state change notifications";

    @Inject
    @Activate
    public NetconfStream(@Reference final DOMDataBroker dataBroker) {
        final var restconfSubscriptionsStreamRegistry = new RestconfSubscriptionsStreamRegistry(
            uri -> uri.resolve(URI.create(NAME)), dataBroker);

        Futures.addCallback(restconfSubscriptionsStreamRegistry.addStream(streamEntry(NAME, DESCRIPTION)),
            new FutureCallback<Object>() {
                @Override
                public void onSuccess(final Object result) {
                    LOG.debug("Stream {} added", NAME);
                }

                @Override
                public void onFailure(final Throwable cause) {
                    LOG.error("Failed to add stream {}", NAME, cause);
                }
            }, MoreExecutors.directExecutor());
    }

    private @NonNull MapEntryNode streamEntry(final String name, final String description) {
        return ImmutableNodes.newMapEntryBuilder()
            .withNodeIdentifier(YangInstanceIdentifier.NodeIdentifierWithPredicates.of(Stream.QNAME, NAME_QNAME, name))
            .withChild(ImmutableNodes.leafNode(NAME_QNAME, name))
            .withChild(ImmutableNodes.leafNode(DESCRIPTION_QNAME, description))
            //FIXME: We don't use replays, but without this node we get error:
            // "Node stream is missing mandatory descendant replay-log-creation-time"
            .withChild(ImmutableNodes.leafNode(REPLAY_LOG_CREATION_TIME_QNAME,
                Instant.now().truncatedTo(ChronoUnit.SECONDS).toString()))
            .build();
    }
}
