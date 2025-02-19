/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.subscription;

import static java.util.Objects.requireNonNull;

import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.dom.api.DOMNotificationService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.server.spi.RestconfStream;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.subscribed.notifications.rev190909.streams.Stream;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.spi.node.ImmutableNodes;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

/**
 * This class recreates DefaultNotificationSource when model context is updated.
 */
final class ContextListener implements Registration {
    private static final String NAME = "NETCONF";
    private static final QName NAME_QNAME = QName.create(Stream.QNAME, "name").intern();
    private static final QName DESCRIPTION_QNAME = QName.create(Stream.QNAME, "description").intern();
    private static final String DESCRIPTION = "Stream for subscription state change notifications";

    private final @NonNull DOMNotificationService notificationService;
    private final @NonNull Registration registration;
    private final RestconfStream.@NonNull Registry streamRegistry;

    private DefaultNotificationSource notificationSource;

    ContextListener(final DOMNotificationService notificationService, final DOMSchemaService schemaService,
            final RestconfStream.Registry streamRegistry) {
        this.notificationService = requireNonNull(notificationService);
        this.streamRegistry = streamRegistry;
        notificationSource = new DefaultNotificationSource(notificationService, schemaService.getGlobalContext());

        streamRegistry.createStream(null, null, notificationSource, DESCRIPTION);
        registration = schemaService.registerSchemaContextListener(this::onModelContextUpdated);
    }

    synchronized void onModelContextUpdated(final EffectiveModelContext context) {
        if (notificationSource != null) {
            notificationSource.close();
        }
        notificationSource = new DefaultNotificationSource(notificationService, context);

        streamRegistry.createStream(null, null, notificationSource, DESCRIPTION);
    }

    public static @NonNull MapEntryNode streamEntry() {
        return ImmutableNodes.newMapEntryBuilder()
            .withNodeIdentifier(YangInstanceIdentifier.NodeIdentifierWithPredicates.of(Stream.QNAME, NAME_QNAME, NAME))
            .withChild(ImmutableNodes.leafNode(NAME_QNAME, NAME))
            .withChild(ImmutableNodes.leafNode(DESCRIPTION_QNAME, DESCRIPTION))
            .build();
    }

    @Override
    public void close() {
        registration.close();
        if (notificationSource != null) {
            notificationSource.close();
        }
    }
}
