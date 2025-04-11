/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.mdsal;

import static java.util.Objects.requireNonNull;

import java.io.Closeable;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.mdsal.dom.api.DOMNotificationService;
import org.opendaylight.restconf.mdsal.spi.AbstractNotificationSource;
import org.opendaylight.restconf.mdsal.spi.NotificationSource;
import org.opendaylight.restconf.server.spi.RestconfStream.Sink;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.stmt.NotificationEffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier;

/**
 * The purpose of this class is to provide source of all controller's YANG notifications.
 */
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
        return reg = notificationService.registerNotificationListener(new Listener(sink, () -> context), notifications);
    }

    @Override
    public synchronized void close() {
        if (reg != null) {
            reg.close();
        }
    }
}
