/*
 * Copyright (c) 2022 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams.listeners;

import java.time.Instant;
import java.util.Optional;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.dom.api.DOMEvent;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.mdsal.dom.api.DOMNotificationListener;
import org.opendaylight.yang.gen.v1.urn.sal.restconf.event.subscription.rev140708.NotificationOutputTypeGrouping.NotificationOutputType;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONCodecFactorySupplier;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract base class for functionality shared between {@link NotificationListenerAdapter} and
 * {@link DeviceNotificationListenerAdaptor}.
 */
abstract class AbstractNotificationListenerAdaptor extends AbstractCommonSubscriber<DOMNotification>
        implements DOMNotificationListener {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractNotificationListenerAdaptor.class);
    private static final NotificationFormatterFactory JSON_FORMATTER_FACTORY =
        JSONNotificationFormatter.createFactory(JSONCodecFactorySupplier.RFC7951);

    AbstractNotificationListenerAdaptor(final String streamName, final NotificationOutputType outputType) {
        super(streamName, outputType, getFormatterFactory(outputType));
    }

    private static NotificationFormatterFactory getFormatterFactory(final NotificationOutputType outputType) {
        return switch (outputType) {
            case JSON -> JSON_FORMATTER_FACTORY;
            case XML -> XMLNotificationFormatter.FACTORY;
        };
    }

    @Override
    @SuppressWarnings("checkstyle:IllegalCatch")
    public final void onNotification(final DOMNotification notification) {
        final var eventInstant = notification instanceof DOMEvent domEvent ? domEvent.getEventInstant() : Instant.now();
        if (!checkStartStop(eventInstant)) {
            return;
        }

        final Optional<String> maybeOutput;
        try {
            maybeOutput = formatter().eventData(effectiveModel(), notification, eventInstant, getLeafNodesOnly(),
                isSkipNotificationData(), getChangedLeafNodesOnly());
        } catch (Exception e) {
            LOG.error("Failed to process notification {}", notification, e);
            return;
        }
        maybeOutput.ifPresent(this::post);
    }

    abstract @NonNull EffectiveModelContext effectiveModel();
}
