/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.streams.listeners;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.internal.ConcurrentSet;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.Executors;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.json.JSONObject;
import org.json.XML;
import org.opendaylight.controller.md.sal.dom.api.DOMNotification;
import org.opendaylight.controller.md.sal.dom.api.DOMNotificationListener;
import org.opendaylight.netconf.sal.restconf.impl.ControllerContext;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeWriter;
import org.opendaylight.yangtools.yang.data.impl.codec.xml.XMLStreamNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.codec.xml.XmlDocumentUtils;
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
public class NotificationListenerAdapter implements DOMNotificationListener {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationListenerAdapter.class);
    private static final TransformerFactory FACTORY = TransformerFactory.newInstance();

    private final String streamName;
    private ListenerRegistration<DOMNotificationListener> registration;
    private Set<Channel> subscribers = new ConcurrentSet<>();
    private final EventBus eventBus;
    private final EventBusChangeRecorder eventBusChangeRecorder;

    private final SchemaPath path;
    private final String outputType;

    /**
     * Set path of listener and stream name, register event bus.
     *
     * @param path
     *            - path of notification
     * @param streamName
     *            - stream name of listener
     * @param outputType
     *            - type of output on notification (JSON, XML)
     */
    NotificationListenerAdapter(final SchemaPath path, final String streamName, final String outputType) {
        this.outputType = outputType;
        Preconditions.checkArgument((streamName != null) && !streamName.isEmpty());
        Preconditions.checkArgument(path != null);
        this.path = path;
        this.streamName = streamName;
        this.eventBus = new AsyncEventBus(Executors.newSingleThreadExecutor());
        this.eventBusChangeRecorder = new EventBusChangeRecorder();
        this.eventBus.register(this.eventBusChangeRecorder);
    }

    @Override
    public void onNotification(final DOMNotification notification) {
        final String xml = prepareXmlFrom(notification);
        final Event event = new Event(EventType.NOTIFY);
        if (this.outputType.equals("JSON")) {
            final JSONObject jsonObject = XML.toJSONObject(xml);
            event.setData(jsonObject.toString());
        } else {
            event.setData(xml);
        }
        this.eventBus.post(event);
    }

    /**
     * Checks if exists at least one {@link Channel} subscriber.
     *
     * @return True if exist at least one {@link Channel} subscriber, false
     *         otherwise.
     */
    public boolean hasSubscribers() {
        return !this.subscribers.isEmpty();
    }

    /**
     * Reset lists, close registration and unregister bus event.
     */
    public void close() {
        this.subscribers = new ConcurrentSet<>();
        this.registration.close();
        this.registration = null;
        this.eventBus.unregister(this.eventBusChangeRecorder);
    }

    /**
     * Get stream name of this listener
     *
     * @return {@link String}
     */
    public String getStreamName() {
        return this.streamName;
    }

    /**
     * Check if is this listener registered.
     *
     * @return - true if is registered, otherwise null
     */
    public boolean isListening() {
        return this.registration != null;
    }

    /**
     * Get schema path of notification
     *
     * @return {@link SchemaPath}
     */
    public SchemaPath getSchemaPath() {
        return this.path;
    }

    /**
     * Set registration for close after closing connection and check if this
     * listener is registered
     *
     * @param registration
     *            - registered listener
     */
    public void setRegistration(final ListenerRegistration<DOMNotificationListener> registration) {
        Preconditions.checkNotNull(registration);
        this.registration = registration;
    }

    /**
     * Creates event of type {@link EventType#REGISTER}, set {@link Channel}
     * subscriber to the event and post event into event bus.
     *
     * @param subscriber
     *            Channel
     */
    public void addSubscriber(final Channel subscriber) {
        if (!subscriber.isActive()) {
            LOG.debug("Channel is not active between websocket server and subscriber {}" + subscriber.remoteAddress());
        }
        final Event event = new Event(EventType.REGISTER);
        event.setSubscriber(subscriber);
        this.eventBus.post(event);
    }

    /**
     * Creates event of type {@link EventType#DEREGISTER}, sets {@link Channel}
     * subscriber to the event and posts event into event bus.
     *
     * @param subscriber
     */
    public void removeSubscriber(final Channel subscriber) {
        LOG.debug("Subscriber {} is removed.", subscriber.remoteAddress());
        final Event event = new Event(EventType.DEREGISTER);
        event.setSubscriber(subscriber);
        this.eventBus.post(event);
    }

    private String prepareXmlFrom(final DOMNotification notification) {
        final SchemaContext schemaContext = ControllerContext.getInstance().getGlobalSchema();
        final Document doc = ListenerAdapter.createDocument();
        final Element notificationElement =
                doc.createElementNS("urn:ietf:params:xml:ns:netconf:notification:1.0",
                "notification");
        doc.appendChild(notificationElement);

        final Element eventTimeElement = doc.createElement("eventTime");
        eventTimeElement.setTextContent(ListenerAdapter.toRFC3339(new Date()));
        notificationElement.appendChild(eventTimeElement);
        final String notificationNamespace = notification.getType().getLastComponent().getNamespace().toString();
        final Element notificationEventElement = doc.createElementNS(
                notificationNamespace, "event");
        addValuesToNotificationEventElement(doc, notificationEventElement, notification, schemaContext);
        notificationElement.appendChild(notificationEventElement);

        try {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final Transformer transformer = FACTORY.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
            transformer.transform(new DOMSource(doc), new StreamResult(new OutputStreamWriter(out, Charsets.UTF_8)));
            final byte[] charData = out.toByteArray();
            return new String(charData, "UTF-8");
        } catch (TransformerException | UnsupportedEncodingException e) {
            final String msg = "Error during transformation of Document into String";
            LOG.error(msg, e);
            return msg;
        }
    }

    private void addValuesToNotificationEventElement(final Document doc, final Element element,
            final DOMNotification notification, final SchemaContext schemaContext) {
        if (notification == null) {
            return;
        }

        final NormalizedNode<NodeIdentifier, Collection<DataContainerChild<? extends PathArgument, ?>>> body = notification
                .getBody();
        try {
            final DOMResult domResult = writeNormalizedNode(body,
                    YangInstanceIdentifier.create(body.getIdentifier()), schemaContext);
            final Node result = doc.importNode(domResult.getNode().getFirstChild(), true);
            element.appendChild(result);
        } catch (final IOException e) {
            LOG.error("Error in writer ", e);
        } catch (final XMLStreamException e) {
            LOG.error("Error processing stream", e);
        }
    }

    private DOMResult writeNormalizedNode(final NormalizedNode<?, ?> normalized, final YangInstanceIdentifier path,
            final SchemaContext context) throws IOException, XMLStreamException {
        final XMLOutputFactory XML_FACTORY = XMLOutputFactory.newFactory();
        final Document doc = XmlDocumentUtils.getDocument();
        final DOMResult result = new DOMResult(doc);
        NormalizedNodeWriter normalizedNodeWriter = null;
        NormalizedNodeStreamWriter normalizedNodeStreamWriter = null;
        XMLStreamWriter writer = null;

        try {
            writer = XML_FACTORY.createXMLStreamWriter(result);
            normalizedNodeStreamWriter = XMLStreamNormalizedNodeStreamWriter.create(writer, context,
                    this.getSchemaPath());
            normalizedNodeWriter = NormalizedNodeWriter.forStreamWriter(normalizedNodeStreamWriter);

            normalizedNodeWriter.write(normalized);

            normalizedNodeWriter.flush();
        } finally {
            if (normalizedNodeWriter != null) {
                normalizedNodeWriter.close();
            }
            if (normalizedNodeStreamWriter != null) {
                normalizedNodeStreamWriter.close();
            }
            if (writer != null) {
                writer.close();
            }
        }

        return result;
    }

    /**
     * Tracks events of data change by customer.
     */
    private final class EventBusChangeRecorder {
        @Subscribe
        public void recordCustomerChange(final Event event) {
            if (event.getType() == EventType.REGISTER) {
                final Channel subscriber = event.getSubscriber();
                if (!NotificationListenerAdapter.this.subscribers.contains(subscriber)) {
                    NotificationListenerAdapter.this.subscribers.add(subscriber);
                }
            } else if (event.getType() == EventType.DEREGISTER) {
                NotificationListenerAdapter.this.subscribers.remove(event.getSubscriber());
                Notificator.removeNotificationListenerIfNoSubscriberExists(NotificationListenerAdapter.this);
            } else if (event.getType() == EventType.NOTIFY) {
                for (final Channel subscriber : NotificationListenerAdapter.this.subscribers) {
                    if (subscriber.isActive()) {
                        LOG.debug("Data are sent to subscriber {}:", subscriber.remoteAddress());
                        subscriber.writeAndFlush(new TextWebSocketFrame(event.getData()));
                    } else {
                        LOG.debug("Subscriber {} is removed - channel is not active yet.", subscriber.remoteAddress());
                        NotificationListenerAdapter.this.subscribers.remove(subscriber);
                    }
                }
            }
        }
    }

    /**
     * Represents event of specific {@link EventType} type, holds data and
     * {@link Channel} subscriber.
     */
    private final class Event {
        private final EventType type;
        private Channel subscriber;
        private String data;

        /**
         * Creates new event specified by {@link EventType} type.
         *
         * @param type
         *            EventType
         */
        public Event(final EventType type) {
            this.type = type;
        }

        /**
         * Gets the {@link Channel} subscriber.
         *
         * @return Channel
         */
        public Channel getSubscriber() {
            return this.subscriber;
        }

        /**
         * Sets subscriber for event.
         *
         * @param subscriber
         *            Channel
         */
        public void setSubscriber(final Channel subscriber) {
            this.subscriber = subscriber;
        }

        /**
         * Gets event String.
         *
         * @return String representation of event data.
         */
        public String getData() {
            return this.data;
        }

        /**
         * Sets event data.
         *
         * @param data
         *            String.
         */
        public void setData(final String data) {
            this.data = data;
        }

        /**
         * Gets event type.
         *
         * @return The type of the event.
         */
        public EventType getType() {
            return this.type;
        }
    }

    /**
     * Type of the event.
     */
    private enum EventType {
        REGISTER, DEREGISTER, NOTIFY
    }
}
