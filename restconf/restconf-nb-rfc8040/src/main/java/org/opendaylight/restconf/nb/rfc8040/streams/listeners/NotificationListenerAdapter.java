/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams.listeners;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
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
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link NotificationListenerAdapter} is responsible to track events on notifications.
 */
public class NotificationListenerAdapter extends AbstractCommonSubscriber implements DOMNotificationListener {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationListenerAdapter.class);
    private static final NotificationFormatterFactory JSON_FORMATTER_FACTORY = JSONNotificationFormatter.createFactory(
            JSONCodecFactorySupplier.RFC7951);

    private final String streamName;
    private final Absolute path;
    private final NotificationOutputType outputType;

    @VisibleForTesting NotificationFormatter formatter;


    /**
     * Set path of listener and stream name.
     *
     * @param path       Schema path of YANG notification.
     * @param streamName Name of the stream.
     * @param outputType Type of output on notification (JSON or XML).
     */
    NotificationListenerAdapter(final Absolute path, final String streamName, final String outputType) {
        setLocalNameOfPath(path.lastNodeIdentifier().getLocalName());

        this.outputType = NotificationOutputType.forName(requireNonNull(outputType)).get();
        this.path = requireNonNull(path);
        this.streamName = requireNonNull(streamName);
        checkArgument(!streamName.isEmpty());

        LOG.info("output type: {}, {}", outputType, this.outputType);

        this.formatter = getFormatterFactory().getFormatter();
    }

    private NotificationFormatterFactory getFormatterFactory() {
        switch (outputType) {
            case JSON:
                return JSON_FORMATTER_FACTORY;
            case XML:
                return XMLNotificationFormatter.FACTORY;
            default:
                throw new IllegalArgumentException(("Unsupported outputType" + outputType));
        }
    }

    private NotificationFormatter getFormatter(final String filter) throws XPathExpressionException {
        NotificationFormatterFactory factory = getFormatterFactory();
        return filter == null || filter.isEmpty() ? factory.getFormatter() : factory.getFormatter(filter);
    }

    @Override
    public void setQueryParams(Instant start, Instant stop, String filter, boolean leafNodesOnly,
                               boolean skipNotificationData) {
        super.setQueryParams(start, stop, filter, leafNodesOnly, skipNotificationData);
        try {
            this.formatter = getFormatter(filter);
        } catch (XPathExpressionException e) {
            throw new IllegalArgumentException("Failed to get filter", e);
        }
    }

    /**
     * Get output type of this listener.
     *
     * @return The configured output type (JSON or XML).
     */
    @Override
    public String getOutputType() {
        return this.outputType.getName();
    }

    @Override
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void onNotification(final DOMNotification notification) {
        final Instant now = Instant.now();
        if (!checkStartStop(now, this)) {
            return;
        }

        final Optional<String> maybeOutput;
        try {
            maybeOutput = formatter.eventData(schemaHandler.get(), notification, now, getLeafNodesOnly(),
                    isSkipNotificationData());
        } catch (Exception e) {
            LOG.error("Failed to process notification {}", notification, e);
            return;
        }
        if (maybeOutput.isPresent()) {
            post(maybeOutput.get());
        }
    }

    /**
     * Get stream name of this listener.
     *
     * @return The configured stream name.
     */
    @Override
    public String getStreamName() {
        return this.streamName;
    }

    /**
     * Get schema path of notification.
     *
     * @return The configured schema path that points to observing YANG notification schema node.
     */
    public Absolute getSchemaPath() {
        return this.path;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("path", path)
                .add("stream-name", streamName)
                .add("output-type", outputType)
                .toString();
    }
}
