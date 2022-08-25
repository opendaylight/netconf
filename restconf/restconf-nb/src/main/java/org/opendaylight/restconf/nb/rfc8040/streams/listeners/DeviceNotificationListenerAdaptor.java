/*
 * Copyright (c) 2022 Opendaylight, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams.listeners;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.opendaylight.mdsal.dom.api.DOMEvent;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.mdsal.dom.api.DOMNotificationListener;
import org.opendaylight.mdsal.dom.api.DOMNotificationService;
import org.opendaylight.restconf.nb.rfc8040.streams.StreamSessionHandler;
import org.opendaylight.yang.gen.v1.urn.sal.restconf.event.subscription.rev140708.NotificationOutputTypeGrouping.NotificationOutputType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONCodecFactorySupplier;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link DeviceNotificationListenerAdaptor} is responsible to track events on notifications.
 */
public class DeviceNotificationListenerAdaptor extends AbstractCommonSubscriber<SchemaNodeIdentifier.Absolute,
        DOMNotification> implements DOMNotificationListener {

    private static final Logger LOG = LoggerFactory.getLogger(DeviceNotificationListenerAdaptor.class);
    private static final NotificationFormatterFactory JSON_FORMATTER_FACTORY = JSONNotificationFormatter.createFactory(
            JSONCodecFactorySupplier.RFC7951);
    private static final Absolute NETCONF = Absolute.of(QName
            .create(QNameModule.create(SchemaContext.NAME.getNamespace(),Revision.of("2011-06-01")),
                    "NETCONF").intern());

    private final EffectiveModelContext refSchemaCtx;

    public DeviceNotificationListenerAdaptor(final Absolute path, final String streamName,
        final NotificationOutputType outputType, final EffectiveModelContext refSchemaCtx) {
        super(path.lastNodeIdentifier(), streamName, path, outputType, getFormatterFactory(outputType));
        this.refSchemaCtx = refSchemaCtx;
    }

    public DeviceNotificationListenerAdaptor(final String path, final NotificationOutputType outputType,
        final EffectiveModelContext refSchemaCtx) {
        super(NETCONF.lastNodeIdentifier(), path, NETCONF, outputType, getFormatterFactory(outputType));
        this.refSchemaCtx = refSchemaCtx;
    }

    private static NotificationFormatterFactory getFormatterFactory(final NotificationOutputType outputType) {
        switch (outputType) {
            case JSON:
                return JSON_FORMATTER_FACTORY;
            case XML:
                return XMLNotificationFormatter.FACTORY;
            default:
                throw new IllegalArgumentException("Unsupported outputType " + outputType);
        }
    }

    @Override
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void onNotification(final DOMNotification notification) {
        final Instant eventInstant = notification instanceof DOMEvent domEvent
            ? domEvent.getEventInstant() : Instant.now();
        if (!checkStartStop(eventInstant)) {
            return;
        }

        final Optional<String> maybeOutput;
        try {
            maybeOutput = formatter().eventData(refSchemaCtx, notification, eventInstant, getLeafNodesOnly(),
                isSkipNotificationData());
        } catch (Exception e) {
            LOG.error("Failed to process notification {}", notification, e);
            return;
        }
        if (maybeOutput.isPresent()) {
            post(maybeOutput.get());
        }
    }

    @Override
    public synchronized void close() throws InterruptedException, ExecutionException {
        if (registration != null) {
            registration.close();
            registration = null;
        }
        subscribers.clear();
    }

    @Override
    public synchronized void removeSubscriber(final StreamSessionHandler subscriber) {
        subscribers.remove(subscriber);
    }

    public synchronized void listen(final DOMNotificationService notificationService,
        SchemaNodeIdentifier.Absolute... path) {
        if (!isListening()) {
            setRegistration(notificationService.registerNotificationListener(this, path));
        }
    }
}
