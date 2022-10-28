/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams.listeners;

import java.time.Instant;
import java.util.Optional;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.mdsal.dom.api.DOMNotificationListener;
import org.opendaylight.mdsal.dom.api.DOMNotificationService;
import org.opendaylight.yang.gen.v1.urn.sal.restconf.event.subscription.rev140708.NotificationOutputTypeGrouping.NotificationOutputType;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONCodecFactorySupplier;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link NotificationListenerAdapter} is responsible to track events on notifications.
 */
public final class NotificationListenerAdapter extends AbstractCommonSubscriber<Absolute, DOMNotification>
        implements DOMNotificationListener {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationListenerAdapter.class);
    private static final NotificationFormatterFactory JSON_FORMATTER_FACTORY = JSONNotificationFormatter.createFactory(
            JSONCodecFactorySupplier.RFC7951);

    /**
     * Set path of listener and stream name.
     *
     * @param path       Schema path of YANG notification.
     * @param streamName Name of the stream.
     * @param outputType Type of output on notification (JSON or XML).
     */
    NotificationListenerAdapter(final Absolute path, final String streamName, final NotificationOutputType outputType) {
        super(path.lastNodeIdentifier(), streamName, path, outputType, getFormatterFactory(outputType));
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
        final Instant now = Instant.now();
        if (!checkStartStop(now)) {
            return;
        }

        final Optional<String> maybeOutput;
        try {
            maybeOutput = formatter().eventData(schemaHandler.get(), notification, now, getLeafNodesOnly(),
                    isSkipNotificationData(), getChangedLeafNodesOnly());
        } catch (Exception e) {
            LOG.error("Failed to process notification {}", notification, e);
            return;
        }
        if (maybeOutput.isPresent()) {
            post(maybeOutput.get());
        }
    }

    /**
     * Get schema path of notification.
     *
     * @return The configured schema path that points to observing YANG notification schema node.
     */
    public Absolute getSchemaPath() {
        return path();
    }

    public synchronized void listen(final DOMNotificationService notificationService) {
        if (!isListening()) {
            setRegistration(notificationService.registerNotificationListener(this, getSchemaPath()));
        }
    }
}
