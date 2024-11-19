/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf;

import java.io.Closeable;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.mdsal.dom.api.DOMNotificationService;
import org.opendaylight.restconf.mdsal.spi.AbstractNotificationSource;
import org.opendaylight.restconf.mdsal.spi.NotificationSource;
import org.opendaylight.restconf.server.spi.RestconfStream;
import org.opendaylight.restconf.server.spi.RestconfStream.Sink;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.stmt.NotificationEffectiveStatement;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link RestconfStream} called "NETCONF" providing all controller's YANG notifications as described in
 * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-6.3.1">RFC 8040</a> for purposes of subscribed notifications
 * defined in <a href="https://www.rfc-editor.org/rfc/rfc8639">RFC 8639</a>.
 *
 * <p>It automatically re-registers to current YANG notifications on controller's {@code EffectiveModelContext} change.
 */
// FIXME crate class ContextListener which listens on onModelContextUpdated and creates a new instance
// FIXME of DefaultNotificationSource.
// FIXME create a new class NetconfStream which uses ContextListener and writes NETCONF stream into mdsal.
// FIXME Use RestconfSubscriptionsStreamRegistry utility.
final class DefaultNotificationSource extends AbstractNotificationSource implements Closeable {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultNotificationSource.class);

    private final DOMNotificationService notificationService;
    private final EffectiveModelContext context;
    private Sink<DOMNotification> sink;

    DefaultNotificationSource(final DOMNotificationService notificationService, EffectiveModelContext context) {
        super(NotificationSource.ENCODINGS);
        this.notificationService = notificationService;
        this.context = context;
    }

    @Override
    protected Registration start(final Sink<DOMNotification> notifSink) {
        this.sink = notifSink;
        final var notifications = context.getModuleStatements().values().stream()
            .flatMap(module -> module.streamEffectiveSubstatements(NotificationEffectiveStatement.class))
            .map(notification -> SchemaNodeIdentifier.Absolute.of(notification.argument()))
            .toList();
        return notificationService.registerNotificationListener(new Listener(sink, () -> context), notifications);
    }

    @Override
    public synchronized void close() {
        if (sink != null) {
            sink.endOfStream();
        }
    }
}
