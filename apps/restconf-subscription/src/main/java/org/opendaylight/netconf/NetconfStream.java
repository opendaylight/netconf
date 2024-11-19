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
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.restconf.mdsal.spi.NotificationSource;
import org.opendaylight.restconf.notifications.mdsal.RestconfSubscriptionsStreamRegistry;
import org.opendaylight.restconf.server.spi.RestconfStream;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.monitoring.rev170126.restconf.state.streams.Stream;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.restconf.monitoring.rev170126.restconf.state.streams.stream.Access;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Singleton
@Component
public final class NetconfStream {
    public static final QName NAME_QNAME =  QName.create(Stream.QNAME, "name").intern();
    public static final QName DESCRIPTION_QNAME = QName.create(Stream.QNAME, "description").intern();
    public static final QName ENCODING_QNAME = QName.create(Stream.QNAME, "encoding").intern();
    public static final QName LOCATION_QNAME = QName.create(Stream.QNAME, "location").intern();
    private static final QName REPLAY_LOG_CREATION_TIME_QNAME =
        QName.create("urn:ietf:params:xml:ns:yang:ietf-subscribed-notifications", "2019-09-09", "replay-log-creation-time");


    @Inject
    @Activate
    public NetconfStream(@Reference final DOMDataBroker dataBroker) {
        final var restconfSubscriptionsStreamRegistry = new RestconfSubscriptionsStreamRegistry(
            uri -> uri.resolve(URI.create("subscriptionStream")), dataBroker);

        Futures.addCallback(restconfSubscriptionsStreamRegistry.addStream(streamEntry("streamName", "description",
            "location", NotificationSource.ENCODINGS.keySet())), new FutureCallback<Object>() {
                @Override
                public void onSuccess(final Object result) {

                }

                @Override
                public void onFailure(final Throwable cause) {

                }
            }, MoreExecutors.directExecutor());
    }

    public static @NonNull MapEntryNode streamEntry(final String name, final String description,
        final String baseStreamLocation, final Set<RestconfStream.EncodingName> encodings) {
        final var accessBuilder = ImmutableNodes.newSystemMapBuilder()
            .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(Access.QNAME));
        for (var encoding : encodings) {
            final var encodingName = encoding.name();
            accessBuilder.withChild(ImmutableNodes.newMapEntryBuilder()
                .withNodeIdentifier(YangInstanceIdentifier.NodeIdentifierWithPredicates.of(Access.QNAME, ENCODING_QNAME, encodingName))
                .withChild(ImmutableNodes.leafNode(ENCODING_QNAME, encodingName))
                .withChild(ImmutableNodes.leafNode(LOCATION_QNAME,
                    baseStreamLocation + '/' + encodingName + '/' + name))
                .build());
        }

        return ImmutableNodes.newMapEntryBuilder()
            .withNodeIdentifier(YangInstanceIdentifier.NodeIdentifierWithPredicates.of(Stream.QNAME, NAME_QNAME, name))
            .withChild(ImmutableNodes.leafNode(NAME_QNAME, name))
            .withChild(ImmutableNodes.leafNode(DESCRIPTION_QNAME, description))
            .withChild(ImmutableNodes.leafNode(REPLAY_LOG_CREATION_TIME_QNAME, "2024-12-03T15:30:00Z"))
            .withChild(accessBuilder.build())
            .build();
    }
}
