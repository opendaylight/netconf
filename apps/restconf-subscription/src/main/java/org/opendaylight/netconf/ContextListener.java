/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf;

import javax.inject.Inject;
import org.opendaylight.mdsal.dom.api.DOMNotificationService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component
public final class ContextListener implements AutoCloseable {
    private final DOMNotificationService notificationService;
    private DefaultNotificationSource notificationSource;
    private final Registration registration;

    @Inject
    @Activate
    public ContextListener(@Reference final DOMSchemaService schemaService,
            @Reference final DOMNotificationService notificationService) {
        this.notificationService = notificationService;
        this.registration = schemaService.registerSchemaContextListener(this::onModelContextUpdated);
    }


    private synchronized void onModelContextUpdated(final EffectiveModelContext context) {
        if (notificationSource != null) {
            notificationSource.close();
        }

        notificationSource = new DefaultNotificationSource(notificationService, context);
    }

    @Override
    public void close() {
        registration.close();
    }
}
