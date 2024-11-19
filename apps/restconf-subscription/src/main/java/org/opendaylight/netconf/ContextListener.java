package org.opendaylight.netconf;

import javax.inject.Inject;
import org.opendaylight.mdsal.dom.api.DOMNotificationService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

public final class ContextListener implements AutoCloseable {
    private final DOMSchemaService schemaService;
    private final DOMNotificationService notificationService;
    private volatile DefaultNotificationSource notificationSource;
    private final Registration registration;


    public ContextListener( final DOMSchemaService domSchemaService,
        final DOMNotificationService notificationService) {
        this.schemaService = domSchemaService;
        this.notificationService = notificationService;
        this.registration = schemaService.registerSchemaContextListener(this::onModelContextUpdated);
    }


    private synchronized void onModelContextUpdated(final EffectiveModelContext context) {
        if (notificationSource != null) {
            notificationSource.close();
        }

        notificationSource = new DefaultNotificationSource(schemaService, notificationService, context);
    }

    @Override
    public void close() {
        registration.close();
    }
}
