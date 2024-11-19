package org.opendaylight.netconf;

import java.util.function.Supplier;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.mdsal.dom.api.DOMNotificationService;
import org.opendaylight.mdsal.dom.spi.FixedDOMSchemaService;
import org.opendaylight.restconf.mdsal.spi.AbstractNotificationSource;
import org.opendaylight.restconf.mdsal.spi.NotificationSource;
import org.opendaylight.restconf.server.spi.RestconfStream;
import org.opendaylight.yangtools.concepts.Registration;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SubscriptionNotificationStream extends AbstractNotificationSource {
    private static final Logger LOG = LoggerFactory.getLogger(SubscriptionNotificationStream.class);
    private final DOMNotificationService notificationService;
    private final EffectiveModelContext modelContext;

    private RestconfStream.Sink<DOMNotification> sink;
    Registration regi;

    protected SubscriptionNotificationStream(DOMNotificationService notificationService, EffectiveModelContext modelContext) {
        super(NotificationSource.ENCODINGS);
        this.notificationService = notificationService;
        this.modelContext = modelContext;
    }

    @Override
    protected @NonNull Registration start(RestconfStream.Sink<DOMNotification> sink) {
        this.sink = sink;

        final var schemaService = new FixedDOMSchemaService(modelContext);
        final var reg = schemaService.registerSchemaContextListener(this::onModelContextUpdated);
        return reg;
    }

    private void onModelContextUpdated(final EffectiveModelContext newModelContext) {
        final var notifications = newModelContext.getNotifications();
        for (var notification : notifications){
        notification.
        }

        final var reg = notificationService.registerNotificationListener(new Listener(sink, (Supplier<EffectiveModelContext>) modelContext));
    }
}
