/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.subscription;

import static java.util.Objects.requireNonNull;

import java.io.Closeable;
import java.time.Instant;
import java.util.function.Supplier;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.dom.api.DOMEvent;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.mdsal.dom.api.DOMNotificationListener;
import org.opendaylight.mdsal.dom.api.DOMNotificationService;
import org.opendaylight.netconf.common.mdsal.DOMNotificationEvent;
import org.opendaylight.restconf.mdsal.spi.AbstractNotificationSource;
import org.opendaylight.restconf.mdsal.spi.NotificationSource;
import org.opendaylight.restconf.server.spi.RestconfStream.Sink;
import org.opendaylight.restconf.server.spi.SubtreeFilterRestconf;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.stmt.NotificationEffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier;

final class DefaultNotificationSource extends AbstractNotificationSource implements Closeable {
    private final @NonNull DOMNotificationService notificationService;
    private final @NonNull EffectiveModelContext context;

    private Registration reg;

    DefaultNotificationSource(final DOMNotificationService notificationService, final EffectiveModelContext context) {
        super(NotificationSource.ENCODINGS);
        this.notificationService = requireNonNull(notificationService);
        this.context = requireNonNull(context);
    }

    @Override
    protected @NonNull Registration start(final Sink<DOMNotification> sink) {
        final var notifications = context.getModuleStatements().values().stream()
            .flatMap(module -> module.streamEffectiveSubstatements(NotificationEffectiveStatement.class))
            .map(notification -> SchemaNodeIdentifier.Absolute.of(notification.argument()))
            .toList();
        return reg = notificationService.registerNotificationListener(new FilteredListener(sink, () -> context),
            notifications);
    }

    @Override
    public synchronized void close() {
        if (reg != null) {
            reg.close();
        }
    }

    private static final class FilteredListener implements DOMNotificationListener {
        private final Sink<DOMNotification> sink;
        private final Supplier<EffectiveModelContext> modelContext;

        public FilteredListener(final Sink<DOMNotification> sink, final Supplier<EffectiveModelContext> modelContext) {
            this.sink = requireNonNull(sink);
            this.modelContext = requireNonNull(modelContext);
        }

        @Override
        public void onNotification(final DOMNotification notification) {
            // FIXME should be filter node instead on null
            final var filteredNode = (ContainerNode) SubtreeFilterRestconf.applyFilter(null, notification.getBody());
            if (filteredNode == null) {
                return;
            }
            final var notificationEventInstant = notification instanceof DOMEvent domEvent ? domEvent.getEventInstant()
                : Instant.now();
            final var filteredNotification = new DOMNotificationEvent.Rfc6020(filteredNode,
                notificationEventInstant);
            sink.publish(modelContext.get(), filteredNotification, notificationEventInstant);
        }
    }
}
