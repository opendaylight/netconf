/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.mdsal;

import static java.util.Objects.requireNonNull;

import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.dom.api.DOMNotificationService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.server.spi.RestconfStream;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * This class recreates DefaultNotificationSource when model context is updated.
 */
@Singleton
@Component(service = { })
@NonNullByDefault
public final class ContextListener implements Registration {
    private final DOMNotificationService notificationService;
    private final Registration registration;
    private final RestconfStream.Registry streamRegistry;

    private @Nullable DefaultNotificationSource notificationSource;

    @Inject
    @Activate
    public ContextListener(@Reference final DOMNotificationService notificationService,
            @Reference final DOMSchemaService schemaService, @Reference final RestconfStream.Registry streamRegistry) {
        this.notificationService = requireNonNull(notificationService);
        this.streamRegistry = requireNonNull(streamRegistry);

        notificationSource = new DefaultNotificationSource(notificationService, schemaService.getGlobalContext());
        streamRegistry.start(notificationSource);
        registration = schemaService.registerSchemaContextListener(this::onModelContextUpdated);
    }

    synchronized void onModelContextUpdated(final EffectiveModelContext context) {
        if (notificationSource != null) {
            notificationSource.close();
        }
        notificationSource = new DefaultNotificationSource(notificationService, context);
        streamRegistry.start(notificationSource);
    }

    @Override
    public void close() {
        registration.close();
        if (notificationSource != null) {
            notificationSource.close();
        }
    }
}
