/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.streams.listeners;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.time.Instant;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.dom.DOMResult;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.mdsal.dom.api.DOMNotificationListener;
import org.opendaylight.netconf.sal.restconf.impl.ControllerContext;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeWriter;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONCodecFactorySupplier;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.codec.gson.JsonWriterFactory;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * {@link NotificationListenerAdapter} is responsible to track events on notifications.
 */
public final class NotificationListenerAdapter extends AbstractCommonSubscriber implements DOMNotificationListener {
    private static final Logger LOG = LoggerFactory.getLogger(NotificationListenerAdapter.class);

    private final ControllerContext controllerContext;
    private final String streamName;
    private final Absolute path;
    private final String outputType;

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
    NotificationListenerAdapter(final Absolute path, final String streamName, final String outputType,
            final ControllerContext controllerContext) {
        register(this);
        this.outputType = requireNonNull(outputType);
        this.path = requireNonNull(path);
        checkArgument(streamName != null && !streamName.isEmpty());
        this.streamName = streamName;
        this.controllerContext = controllerContext;
    }

    /**
     * Get outputType of listener.
     *
     * @return the outputType
     */
    @Override
    public String getOutputType() {
        return outputType;
    }

    @Override
    public void onNotification(final DOMNotification notification) {
        final Instant now = Instant.now();
        if (!checkStartStop(now, this)) {
            return;
        }

        final EffectiveModelContext schemaContext = controllerContext.getGlobalSchema();
        final String xml = prepareXml(schemaContext, notification);
        if (checkFilter(xml)) {
            prepareAndPostData(outputType.equals("JSON") ? prepareJson(schemaContext, notification) : xml);
        }
    }

    /**
     * Get stream name of this listener.
     *
     * @return {@link String}
     */
    @Override
    public String getStreamName() {
        return streamName;
    }

    /**
     * Get schema path of notification.
     *
     * @return {@link Absolute} SchemaNodeIdentifier
     */
    public Absolute getSchemaPath() {
        return path;
    }

    /**
     * Prepare data of notification and data to client.
     *
     * @param data   data
     */
    private void prepareAndPostData(final String data) {
        final Event event = new Event(EventType.NOTIFY);
        event.setData(data);
        post(event);
    }

    /**
     * Prepare json from notification data.
     *
     * @return json as {@link String}
     */
    @VisibleForTesting
    String prepareJson(final EffectiveModelContext schemaContext, final DOMNotification notification) {
        final JsonObject json = new JsonObject();
        json.add("ietf-restconf:notification", JsonParser.parseString(writeBodyToString(schemaContext, notification)));
        json.addProperty("event-time", ListenerAdapter.toRFC3339(Instant.now()));
        return json.toString();
    }

    private static String writeBodyToString(final EffectiveModelContext schemaContext,
            final DOMNotification notification) {
        final Writer writer = new StringWriter();
        final NormalizedNodeStreamWriter jsonStream = JSONNormalizedNodeStreamWriter.createExclusiveWriter(
            JSONCodecFactorySupplier.DRAFT_LHOTKA_NETMOD_YANG_JSON_02.getShared(schemaContext),
            notification.getType(), null, JsonWriterFactory.createJsonWriter(writer));
        final NormalizedNodeWriter nodeWriter = NormalizedNodeWriter.forStreamWriter(jsonStream);
        try {
            nodeWriter.write(notification.getBody());
            nodeWriter.close();
        } catch (final IOException e) {
            throw new RestconfDocumentedException("Problem while writing body of notification to JSON. ", e);
        }
        return writer.toString();
    }

    private String prepareXml(final EffectiveModelContext schemaContext, final DOMNotification notification) {
        final Document doc = createDocument();
        final Element notificationElement = basePartDoc(doc);

        final Element notificationEventElement = doc.createElementNS(
                "urn:opendaylight:params:xml:ns:yang:controller:md:sal:remote", "create-notification-stream");
        addValuesToNotificationEventElement(doc, notificationEventElement, schemaContext, notification);
        notificationElement.appendChild(notificationEventElement);

        return transformDoc(doc);
    }

    private void addValuesToNotificationEventElement(final Document doc, final Element element,
            final EffectiveModelContext schemaContext, final DOMNotification notification) {
        try {
            final DOMResult domResult = writeNormalizedNode(notification.getBody(),
                SchemaInferenceStack.of(schemaContext, path).toInference());
            final Node result = doc.importNode(domResult.getNode().getFirstChild(), true);
            final Element dataElement = doc.createElement("notification");
            dataElement.appendChild(result);
            element.appendChild(dataElement);
        } catch (final IOException e) {
            LOG.error("Error in writer ", e);
        } catch (final XMLStreamException e) {
            LOG.error("Error processing stream", e);
        }
    }
}
