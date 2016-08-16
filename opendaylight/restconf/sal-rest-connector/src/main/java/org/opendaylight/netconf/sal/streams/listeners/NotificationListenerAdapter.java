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
import org.json.JSONArray;
import org.json.JSONObject;
import org.opendaylight.controller.md.sal.dom.api.DOMNotification;
import org.opendaylight.controller.md.sal.dom.api.DOMNotificationListener;
import org.opendaylight.netconf.sal.rest.impl.RestUtil;
import org.opendaylight.netconf.sal.restconf.impl.ControllerContext;
import org.opendaylight.netconf.sal.restconf.impl.RestCodec;
import org.opendaylight.yangtools.concepts.Codec;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.AugmentationNode;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeWriter;
import org.opendaylight.yangtools.yang.data.impl.codec.xml.XMLStreamNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.codec.xml.XmlDocumentUtils;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.GroupingDefinition;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.NotificationDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.opendaylight.yangtools.yang.model.api.TypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.IdentityrefTypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.LeafrefTypeDefinition;
import org.opendaylight.yangtools.yang.model.util.SchemaContextUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.internal.ConcurrentSet;

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
    private SchemaContext schemaContext;
    private DOMNotification notification;

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
        this.schemaContext = ControllerContext.getInstance().getGlobalSchema();
        this.notification = notification;
        final Event event = new Event(EventType.NOTIFY);
        if (this.outputType.equals("JSON")) {
            event.setData(prepareJson());
        } else {
            event.setData(prepareXmlFrom());
        }
        this.eventBus.post(event);
    }

    private String prepareJson() {
        final JSONObject json = new JSONObject();
        final QName compQName = this.notification.getType().getLastComponent();
        final Module compModule =
                this.schemaContext.findModuleByNamespaceAndRevision(compQName.getNamespace(), compQName.getRevision());
        json.put("ietf-restconf:notification",
                new JSONObject().put("event-time", ListenerAdapter.toRFC3339(new Date()))
                        .put(compModule.getName() + ":" + compQName.getLocalName(),
                                addContainerNodeToJSON(this.notification.getBody(), new JSONObject())));
        return json.toString();
    }

    private JSONObject addContainerNodeToJSON(final ContainerNode child, final JSONObject dataJSON) {
        if (child == null) {
            return dataJSON;
        }
        innerChildToJSON(child.getValue(), dataJSON);

        return dataJSON;
    }

    private void innerChildToJSON(final Collection<DataContainerChild<? extends PathArgument, ?>> collection,
            final JSONObject dataJSON) {
        for (final DataContainerChild<? extends PathArgument, ?> innerChild : collection) {
            if(innerChild instanceof AugmentationNode){
                addAugmentNodeToJSON((AugmentationNode) innerChild, dataJSON);
            } else if (innerChild instanceof MapNode) {
                dataJSON.put(innerChild.getNodeType().getLocalName(),
                        addMapNodeToJSON((MapNode) innerChild));
            } else if(innerChild instanceof LeafNode){
                dataJSON.put(innerChild.getNodeType().getLocalName(), prepareValueByType(innerChild));
            } else if (innerChild instanceof ContainerNode) {
                dataJSON.put(
                        this.schemaContext.findModuleByNamespaceAndRevision(innerChild.getNodeType().getNamespace(),
                                innerChild.getNodeType().getRevision()).getName() + ":"
                                + innerChild.getNodeType().getLocalName(),
                        addContainerNodeToJSON((ContainerNode) innerChild, new JSONObject()));
            }
        }
    }

    private JSONArray addMapNodeToJSON(final MapNode child) {
        final JSONArray jsonArry = new JSONArray();
        for (final MapEntryNode mapEntryNode : child.getValue()) {
            for (final DataContainerChild<? extends PathArgument, ?> innerChild : mapEntryNode.getValue()) {
                if (innerChild instanceof LeafNode) {
                    jsonArry.put(new JSONObject().put(innerChild.getNodeType().getLocalName(),
                            prepareValueByType(innerChild)));
                } else if (innerChild instanceof AugmentationNode) {
                    addAugmentNodeToJSON((AugmentationNode) innerChild, new JSONObject());
                } else if (innerChild instanceof MapNode) {
                    jsonArry.put(new JSONObject().put(innerChild.getNodeType().getLocalName(),
                            addMapNodeToJSON((MapNode) innerChild)));
                } else if (innerChild instanceof ContainerNode) {
                    jsonArry.put(new JSONObject().put(
                            this.schemaContext.findModuleByNamespaceAndRevision(innerChild.getNodeType().getNamespace(),
                                    innerChild.getNodeType().getRevision()).getName() + ":"
                                    + innerChild.getNodeType().getLocalName(),
                            addContainerNodeToJSON((ContainerNode) innerChild, new JSONObject())));
                }
            }
        }
        return jsonArry;
    }

    private Object prepareValueByType(final DataContainerChild<? extends PathArgument, ?> innerChild) {
        Object decoded = null;
        final Module moduleOfLeaf = this.schemaContext.findModuleByNamespaceAndRevision(
                innerChild.getNodeType().getNamespace(), innerChild.getNodeType().getRevision());
        DataSchemaNode dataSchemaNode = moduleOfLeaf.getDataChildByName(innerChild.getNodeType());
        if (dataSchemaNode == null) {
            for (final GroupingDefinition groupingDefinition : moduleOfLeaf.getGroupings()) {
                dataSchemaNode = groupingDefinition.getDataChildByName(innerChild.getNodeType());
                if (dataSchemaNode != null) {
                    break;
                }
            }
            if (dataSchemaNode == null) {
                for (final NotificationDefinition notificationDefinition : moduleOfLeaf.getNotifications()) {
                    dataSchemaNode = notificationDefinition.getDataChildByName(innerChild.getNodeType());
                    if (dataSchemaNode != null) {
                        break;
                    }
                }
            }
        }
        TypeDefinition<?> typedef = ((LeafSchemaNode) dataSchemaNode).getType();
        final TypeDefinition<?> baseType = RestUtil.resolveBaseTypeFrom(typedef);
        if (baseType instanceof LeafrefTypeDefinition) {
            typedef = SchemaContextUtil.getBaseTypeForLeafRef((LeafrefTypeDefinition) baseType, this.schemaContext,
                    dataSchemaNode);
        }
        final Codec<Object, Object> codec = RestCodec.from(typedef, null);
        decoded = codec.deserialize(((LeafNode) innerChild).getValue());
        if (decoded == null) {
            if ((baseType instanceof IdentityrefTypeDefinition)) {
                decoded = toQName(((LeafNode) innerChild).getValue().toString(), null);
            }
        }
        return decoded;
    }

    private QName toQName(final String name, final Date revisionDate) {
        final String module = toModuleName(name);
        final String node = toNodeName(name);
        final Module m = this.schemaContext.findModuleByName(module, revisionDate);
        return m == null ? null : QName.create(m.getQNameModule(), node);
    }

    private static String toModuleName(final String str) {
        final int idx = str.indexOf(':');
        if (idx == -1) {
            return null;
        }

        if (str.indexOf(':', idx + 1) != -1) {
            return null;
        }

        return str.substring(0, idx);
    }

    private static String toNodeName(final String str) {
        final int idx = str.indexOf(':');
        if (idx == -1) {
            return str;
        }

        if (str.indexOf(':', idx + 1) != -1) {
            return str;
        }

        return str.substring(idx + 1);
    }

    private JSONObject addAugmentNodeToJSON(final AugmentationNode child, final JSONObject dataJSON) {

        for (final DataContainerChild<? extends PathArgument, ?> innerChild : child.getValue()) {
            final String moduleName =
                    this.schemaContext.findModuleByNamespaceAndRevision(innerChild.getNodeType().getNamespace(),
                            innerChild.getNodeType().getRevision()).getName();
            if (innerChild instanceof MapNode) {
                dataJSON.put(moduleName + ":" + innerChild.getNodeType().getLocalName(),
                        addMapNodeToJSON((MapNode) innerChild));
            } else if (innerChild instanceof LeafNode) {
                dataJSON.put(moduleName + ":" + innerChild.getNodeType().getLocalName(),
                        ((LeafNode) innerChild).getValue());
            } else if (innerChild instanceof ContainerNode) {
                dataJSON.put(moduleName + ":" + innerChild.getNodeType().getLocalName(),
                        addContainerNodeToJSON((ContainerNode) innerChild, new JSONObject()));
            }
        }
        return dataJSON;
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
        return this.registration == null ? false : true;
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

    private String prepareXmlFrom() {
        final Document doc = ListenerAdapter.createDocument();
        final Element notificationElement = doc.createElementNS("urn:ietf:params:xml:ns:netconf:notification:1.0",
                "notification");
        doc.appendChild(notificationElement);

        final Element eventTimeElement = doc.createElement("eventTime");
        eventTimeElement.setTextContent(ListenerAdapter.toRFC3339(new Date()));
        notificationElement.appendChild(eventTimeElement);

        final Element notificationEventElement = doc.createElementNS(
                "urn:opendaylight:params:xml:ns:yang:controller:md:sal:remote", "create-notification-stream");
        addValuesToNotificationEventElement(doc, notificationEventElement, this.notification, this.schemaContext);
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
            final Element dataElement = doc.createElement("notification");
            dataElement.appendChild(result);
            element.appendChild(dataElement);
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
        REGISTER, DEREGISTER, NOTIFY;
    }
}
