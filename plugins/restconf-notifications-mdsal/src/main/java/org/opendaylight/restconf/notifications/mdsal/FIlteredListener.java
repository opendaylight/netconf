/*
 * Copyright (c) 2025 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.restconf.notifications.mdsal;

import static java.util.Objects.requireNonNull;

import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;
import org.opendaylight.mdsal.dom.api.DOMEvent;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.mdsal.dom.api.DOMNotificationListener;
import org.opendaylight.netconf.common.mdsal.DOMNotificationEvent;
import org.opendaylight.restconf.server.spi.RestconfStream.Sink;
import org.opendaylight.restconf.server.spi.SubtreeFilterRestconf;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;

final class FilteredListener implements DOMNotificationListener {
    private final Sink<DOMNotification> sink;
    private final Supplier<EffectiveModelContext> modelContext;
    private final ContainerNode filter;

    public FilteredListener(final Sink<DOMNotification> sink, final Supplier<EffectiveModelContext> modelContext,
            final Optional<ContainerNode> filter) {
        this.sink = requireNonNull(sink);
        this.modelContext = requireNonNull(modelContext);
        this.filter = filter.orElse(null);
    }

    @Override
    public void onNotification(final DOMNotification notification) {
        final var notificationEventInstant = notification instanceof DOMEvent domEvent ? domEvent.getEventInstant()
            : Instant.now();

        // just return notification if there is no filter
        if (filter == null) {
            sink.publish(modelContext.get(), notification, notificationEventInstant);
            return;
        }

        // filter notification
        final var filteredNode = (ContainerNode) SubtreeFilterRestconf.applyFilter(filter, notification.getBody());
        // check if there is still data left after filter was applied and send it if so
        if (filteredNode != null) {
            final var filteredNotification = new DOMNotificationEvent.Rfc6020(filteredNode,
                notificationEventInstant);
            sink.publish(modelContext.get(), filteredNotification, notificationEventInstant);
        }
    }
}
