/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams.listeners;

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import javax.xml.xpath.XPathExpressionException;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.mdsal.dom.api.DOMNotificationListener;
import org.opendaylight.restconf.common.formatters.JSONNotificationFormatter;
import org.opendaylight.restconf.common.formatters.NotificationFormatter;
import org.opendaylight.restconf.common.formatters.NotificationFormatterFactory;
import org.opendaylight.restconf.common.formatters.XMLNotificationFormatter;
import org.opendaylight.yang.gen.v1.urn.sal.restconf.event.subscription.rev140708.NotificationOutputTypeGrouping.NotificationOutputType;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONCodecFactorySupplier;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link NotificationListenerAdapter} is responsible to track events on
 * notifications.
 *
 */
public class NotificationListenerAdapter extends AbstractCommonSubscriber implements DOMNotificationListener {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationListenerAdapter.class);
    private static final NotificationFormatterFactory JSON_FORMATTER_FACTORY = JSONNotificationFormatter.createFactory(
        JSONCodecFactorySupplier.RFC7951);

    private final String streamName;
    private final SchemaPath path;
    private final NotificationOutputType outputType;

    @VisibleForTesting NotificationFormatter formatter;

    /**
     * Set path of listener and stream name, register event bus.
     *
     * @param path
     *             path of notification
     * @param streamName
     *             stream name of listener
     * @param outputType
     *             type of output on notification (JSON, XML)
     */
    NotificationListenerAdapter(final SchemaPath path, final String streamName, final String outputType) {
        register(this);
        setLocalNameOfPath(path.getLastComponent().getLocalName());

        this.outputType = NotificationOutputType.forName(outputType).get();
        this.path = requireNonNull(path);
        Preconditions.checkArgument(streamName != null && !streamName.isEmpty());
        this.streamName = streamName;
        this.formatter = getFormatterFactory().getFormatter();
    }

    private NotificationFormatterFactory getFormatterFactory() {
        switch (outputType) {
            case JSON:
                return JSON_FORMATTER_FACTORY;
            case XML:
                return XMLNotificationFormatter.FACTORY;
            default:
                throw new IllegalArgumentException("Unsupported outputType " + outputType);
        }
    }

    private NotificationFormatter getFormatter(final Optional<String> filter) throws XPathExpressionException {
        final NotificationFormatterFactory factory = getFormatterFactory();
        return filter.isPresent() ? factory.getFormatter(filter.get()) : factory.getFormatter();
    }

    @Override
    public void setQueryParams(final Instant start, final Optional<Instant> stop, final Optional<String> filter,
            final boolean leafNodesOnly) {
        super.setQueryParams(start, stop, filter, leafNodesOnly);
        try {
            this.formatter = getFormatter(filter);
        } catch (XPathExpressionException e) {
            throw new IllegalArgumentException("Failed to get filter", e);
        }
    }


    /**
     * Get outputType of listener.
     *
     * @return the outputType
     */
    @Override
    public String getOutputType() {
        return this.outputType.getName();
    }

    @Override
    public void onNotification(final DOMNotification notification) {
        final Instant now = Instant.now();
        if (!checkStartStop(now, this)) {
            return;
        }

        final Optional<String> maybeOutput;
        try {
            maybeOutput = formatter.eventData(schemaHandler.get(), notification, now);
        } catch (IOException e) {
            LOG.error("Failed to process notification {}", notification, e);
            return;
        }
        if (maybeOutput.isPresent()) {
            final Event event = new Event(EventType.NOTIFY);
            event.setData(maybeOutput.get());
            post(event);
        }
    }

    /**
     * Get stream name of this listener.
     *
     * @return {@link String}
     */
    @Override
    public String getStreamName() {
        return this.streamName;
    }

    /**
     * Get schema path of notification.
     *
     * @return {@link SchemaPath}
     */
    public SchemaPath getSchemaPath() {
        return this.path;
    }
}
