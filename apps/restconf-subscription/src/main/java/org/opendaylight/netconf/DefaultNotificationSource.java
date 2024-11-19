/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf;

import java.io.Closeable;
import javax.inject.Inject;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.mdsal.spi.AbstractNotificationSource;
import org.opendaylight.restconf.mdsal.spi.NotificationSource;
import org.opendaylight.restconf.server.spi.RestconfStream;
import org.opendaylight.restconf.server.spi.RestconfStream.Sink;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link RestconfStream} called "NETCONF" providing all controller's YANG notifications as described in
 * <a href="https://www.rfc-editor.org/rfc/rfc8040#section-6.3.1">RFC 8040</a> for purposes of subscribed notifications
 * defined in <a href="https://www.rfc-editor.org/rfc/rfc8639">RFC 8639</a>.
 * <p>
 * It automatically re-registers to current YANG notifications on controller's {@code EffectiveModelContext} change.
 */
final class DefaultNotificationSource extends AbstractNotificationSource implements Closeable {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultNotificationSource.class);

    private final DOMSchemaService schemaService;
    private final Registration registration;

    @Inject
    @Activate
    DefaultNotificationSource(@Reference final DOMSchemaService schemaService) {
        super(NotificationSource.ENCODINGS);
        this.schemaService = schemaService;
        registration = schemaService.registerSchemaContextListener(this::onModelContextUpdated);
    }

    private synchronized void onModelContextUpdated(final EffectiveModelContext newModelContext) {
    }

    @Override
    protected Registration start(final Sink<DOMNotification> sink) {
        return null;
    }

    @Deactivate
    @Override
    public synchronized void close() {
        registration.close();
    }
}
