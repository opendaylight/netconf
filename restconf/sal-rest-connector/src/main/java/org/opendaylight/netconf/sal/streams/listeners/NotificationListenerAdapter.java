/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.streams.listeners;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.time.Instant;
import java.util.Collection;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.dom.DOMResult;
import org.opendaylight.controller.md.sal.dom.api.DOMNotification;
import org.opendaylight.controller.md.sal.dom.api.DOMNotificationListener;
import org.opendaylight.netconf.sal.restconf.impl.ControllerContext;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeWriter;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONCodecFactory;
import org.opendaylight.yangtools.yang.data.codec.gson.JSONNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.codec.gson.JsonWriterFactory;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * {@link NotificationListenerAdapter} is responsible to track events on
 * notifications.
 *
 */
public class NotificationListenerAdapter extends AbstractCommonSubscriber implements DOMNotificationListener {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationListenerAdapter.class);

    private final String streamName;
    private final SchemaPath path;
    private final String outputType;

    private SchemaContext schemaContext;
    private DOMNotification notification;

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
        super();
        register(this);
        setLocalNameOfPath(path.getLastComponent().getLocalName());

        this.outputType = Preconditions.checkNotNull(outputType);
        this.path = Preconditions.checkNotNull(path);
        Preconditions.checkArgument(streamName != null && !streamName.isEmpty());
        this.streamName = streamName;
    }

    /**
     * Get outputType of listener.
     *
     * @return the outputType
     */
    @Override
    public String getOutputType() {
        return this.outputType;
    }

    @Override
    public void onNotification(final DOMNotification notification) {
        this.schemaContext = ControllerContext.getInstance().getGlobalSchema();
        this.notification = notification;

        final String xml = prepareXml();
        if (checkQueryParams(xml, this)) {
            prepareAndPostData(xml);
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

    /**
     * Prepare data of notification and data to client.
     *
     * @param xml   data
     */
    private void prepareAndPostData(final String xml) {
        final Event event = new Event(EventType.NOTIFY);
        if (this.outputType.equals("JSON")) {
            event.setData(prepareJson());
        } else {
            event.setData(xml);
        }
        post(event);
    }

    /**
     * Prepare json from notification data.
     *
     * @return json as {@link String}
     */
    @VisibleForTesting
    String prepareJson() {
        JsonParser jsonParser = new JsonParser();
        JsonObject json = new JsonObject();
        json.add("ietf-restconf:notification", jsonParser.parse(writeBodyToString()));
        json.addProperty("event-time", ListenerAdapter.toRFC3339(Instant.now()));
        return json.toString();
    }

    @VisibleForTesting
    void setNotification(final DOMNotification notification) {
        this.notification = Preconditions.checkNotNull(notification);
    }

    @VisibleForTesting
    void setSchemaContext(final SchemaContext schemaContext) {
        this.schemaContext = Preconditions.checkNotNull(schemaContext);
    }

    private String writeBodyToString() {
        final Writer writer = new StringWriter();
        final NormalizedNodeStreamWriter jsonStream =
                JSONNormalizedNodeStreamWriter.createExclusiveWriter(JSONCodecFactory.getShared(this.schemaContext),
                        this.notification.getType(), null, JsonWriterFactory.createJsonWriter(writer));
        final NormalizedNodeWriter nodeWriter = NormalizedNodeWriter.forStreamWriter(jsonStream);
        try {
            nodeWriter.write(this.notification.getBody());
            nodeWriter.close();
        } catch (final IOException e) {
            throw new RestconfDocumentedException("Problem while writing body of notification to JSON. ", e);
        }
        return writer.toString();
    }

    private String prepareXml() {
        final Document doc = createDocument();
        final Element notificationElement = basePartDoc(doc);

        final Element notificationEventElement = doc.createElementNS(
                "urn:opendaylight:params:xml:ns:yang:controller:md:sal:remote", "create-notification-stream");
        addValuesToNotificationEventElement(doc, notificationEventElement, this.notification, this.schemaContext);
        notificationElement.appendChild(notificationEventElement);

        return transformDoc(doc);
    }

    private void addValuesToNotificationEventElement(final Document doc, final Element element,
            final DOMNotification notification, final SchemaContext schemaContext) {
        if (notification == null) {
            return;
        }

        final NormalizedNode<NodeIdentifier, Collection<DataContainerChild<? extends PathArgument, ?>>> body =
                notification.getBody();
        try {

            final DOMResult domResult = writeNormalizedNode(body, schemaContext, this.path);
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
