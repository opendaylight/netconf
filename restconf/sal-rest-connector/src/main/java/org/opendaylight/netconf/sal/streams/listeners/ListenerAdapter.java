/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.streams.listeners;

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
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
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
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import org.json.JSONObject;
import org.json.XML;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataChangeEvent;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.dom.api.DOMDataChangeListener;
import org.opendaylight.controller.md.sal.dom.api.DOMDataWriteTransaction;
import org.opendaylight.netconf.sal.restconf.impl.ControllerContext;
import org.opendaylight.netconf.sal.restconf.impl.RestconfDocumentedException;
import org.opendaylight.restconf.Draft18.MonitoringModule;
import org.opendaylight.restconf.handlers.SchemaContextHandler;
import org.opendaylight.restconf.handlers.TransactionChainHandler;
import org.opendaylight.restconf.parser.IdentifierCodec;
import org.opendaylight.yang.gen.v1.urn.sal.restconf.event.subscription.rev140708.NotificationOutputTypeGrouping.NotificationOutputType;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeWithValue;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.UnkeyedListEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeWriter;
import org.opendaylight.yangtools.yang.data.impl.codec.xml.XMLStreamNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.codec.xml.XmlDocumentUtils;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextTree;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

/**
 * {@link ListenerAdapter} is responsible to track events, which occurred by changing data in data source.
 */
public class ListenerAdapter implements DOMDataChangeListener {

    private static final Logger LOG = LoggerFactory.getLogger(ListenerAdapter.class);
    private static final DocumentBuilderFactory DBF = DocumentBuilderFactory.newInstance();
    private static final TransformerFactory FACTORY = TransformerFactory.newInstance();
    private static final Pattern RFC3339_PATTERN = Pattern.compile("(\\d\\d)(\\d\\d)$");

    private static final SimpleDateFormat RFC3339 = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ssZ");

    private final YangInstanceIdentifier path;
    private ListenerRegistration<DOMDataChangeListener> registration;
    private final String streamName;
    private Set<Channel> subscribers = new ConcurrentSet<>();
    private final EventBus eventBus;
    private final EventBusChangeRecorder eventBusChangeRecorder;
    private final NotificationOutputType outputType;
    private Date start = null;
    private Date stop = null;
    private String filter = null;
    private TransactionChainHandler transactionChainHandler;
    private SchemaContextHandler schemaHandler;

    /**
     * Creates new {@link ListenerAdapter} listener specified by path and stream
     * name.
     *
     * @param path
     *            Path to data in data store.
     * @param streamName
     *            The name of the stream.
     * @param outputType
     *            - type of output on notification (JSON, XML)
     */
    ListenerAdapter(final YangInstanceIdentifier path, final String streamName,
            final NotificationOutputType outputType) {
        this.outputType = outputType;
        Preconditions.checkNotNull(path);
        Preconditions.checkArgument((streamName != null) && !streamName.isEmpty());
        this.path = path;
        this.streamName = streamName;
        this.eventBus = new AsyncEventBus(Executors.newSingleThreadExecutor());
        this.eventBusChangeRecorder = new EventBusChangeRecorder();
        this.eventBus.register(this.eventBusChangeRecorder);
    }

    @Override
    public void onDataChanged(final AsyncDataChangeEvent<YangInstanceIdentifier, NormalizedNode<?, ?>> change) {
        final Date now = new Date();
        if (this.stop != null) {
            if ((this.start.compareTo(now) < 0) && (this.stop.compareTo(now) > 0)) {
                checkFilter(change);
            }
            if (this.stop.compareTo(now) < 0) {
                try {
                    this.close();
                } catch (final Exception e) {
                    throw new RestconfDocumentedException("Problem with unregister listener." + e);
                }
            }
        } else if (this.start != null) {
            if (this.start.compareTo(now) < 0) {
                this.start = null;
                checkFilter(change);
            }
        } else {
            checkFilter(change);
        }
    }

    /**
     * Check if is filter used and then prepare and post data do client
     *
     * @param change
     *            - data of notification
     */
    private void checkFilter(final AsyncDataChangeEvent<YangInstanceIdentifier, NormalizedNode<?, ?>> change) {
        final String xml = prepareXmlFrom(change);
        if (this.filter == null) {
            prepareAndPostData(xml);
        } else {
            try {
                if (parseFilterParam(xml)) {
                    prepareAndPostData(xml);
                }
            } catch (final Exception e) {
                throw new RestconfDocumentedException("Problem while parsing filter.", e);
            }
        }
    }

    /**
     * Parse and evaluate filter value by xml
     *
     * @param xml
     *            - notification data in xml
     * @return true or false - depends on filter expression and data of
     *         notifiaction
     * @throws Exception
     */
    private boolean parseFilterParam(final String xml) throws Exception {
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        final DocumentBuilder builder = factory.newDocumentBuilder();
        final Document docOfXml = builder.parse(new InputSource(new StringReader(xml)));
        final XPath xPath = XPathFactory.newInstance().newXPath();
        return (boolean) xPath.compile(this.filter).evaluate(docOfXml, XPathConstants.BOOLEAN);
    }

    /**
     * Prepare data of notification and data to client
     *
     * @param xml
     */
    private void prepareAndPostData(final String xml) {
            final Event event = new Event(EventType.NOTIFY);
            if (this.outputType.equals(NotificationOutputType.JSON)) {
                final JSONObject jsonObject = XML.toJSONObject(xml);
                event.setData(jsonObject.toString());
            } else {
                event.setData(xml);
            }
            this.eventBus.post(event);
    }

    /**
     * Tracks events of data change by customer.
     */
    private final class EventBusChangeRecorder {
        @Subscribe
        public void recordCustomerChange(final Event event) {
            if (event.getType() == EventType.REGISTER) {
                final Channel subscriber = event.getSubscriber();
                if (!ListenerAdapter.this.subscribers.contains(subscriber)) {
                    ListenerAdapter.this.subscribers.add(subscriber);
                }
            } else if (event.getType() == EventType.DEREGISTER) {
                ListenerAdapter.this.subscribers.remove(event.getSubscriber());
                Notificator.removeListenerIfNoSubscriberExists(ListenerAdapter.this);
            } else if (event.getType() == EventType.NOTIFY) {
                for (final Channel subscriber : ListenerAdapter.this.subscribers) {
                    if (subscriber.isActive()) {
                        LOG.debug("Data are sent to subscriber {}:", subscriber.remoteAddress());
                        subscriber.writeAndFlush(new TextWebSocketFrame(event.getData()));
                    } else {
                        LOG.debug("Subscriber {} is removed - channel is not active yet.", subscriber.remoteAddress());
                        ListenerAdapter.this.subscribers.remove(subscriber);
                    }
                }
            }
        }
    }

    /**
     * Represents event of specific {@link EventType} type, holds data and {@link Channel} subscriber.
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
         * @param data String.
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
        REGISTER,
        DEREGISTER,
        NOTIFY;
    }

    /**
     * Prepare data in printable form and transform it to String.
     *
     * @param change
     *            DataChangeEvent
     * @return Data in printable form.
     */
    private String prepareXmlFrom(final AsyncDataChangeEvent<YangInstanceIdentifier, NormalizedNode<?, ?>> change) {
        final SchemaContext schemaContext = ControllerContext.getInstance().getGlobalSchema();
        final DataSchemaContextTree dataContextTree =  DataSchemaContextTree.from(schemaContext);
        final Document doc = createDocument();
        final Element notificationElement = doc.createElementNS("urn:ietf:params:xml:ns:netconf:notification:1.0",
                "notification");

        doc.appendChild(notificationElement);

        final Element eventTimeElement = doc.createElement("eventTime");
        eventTimeElement.setTextContent(toRFC3339(new Date()));
        notificationElement.appendChild(eventTimeElement);

        final Element dataChangedNotificationEventElement = doc.createElementNS(
                "urn:opendaylight:params:xml:ns:yang:controller:md:sal:remote", "data-changed-notification");

        addValuesToDataChangedNotificationEventElement(doc, dataChangedNotificationEventElement, change,
                schemaContext, dataContextTree);
        notificationElement.appendChild(dataChangedNotificationEventElement);

        try {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final Transformer transformer = FACTORY.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
            transformer.transform(new DOMSource(doc),
                    new StreamResult(new OutputStreamWriter(out, StandardCharsets.UTF_8)));
            final byte[] charData = out.toByteArray();
            return new String(charData, "UTF-8");
        } catch (TransformerException | UnsupportedEncodingException e) {
            final String msg = "Error during transformation of Document into String";
            LOG.error(msg, e);
            return msg;
        }
    }

    /**
     * Formats data specified by RFC3339.
     *
     * @param d
     *            Date
     * @return Data specified by RFC3339.
     */
    public static String toRFC3339(final Date d) {
        return RFC3339_PATTERN.matcher(RFC3339.format(d)).replaceAll("$1:$2");
    }

    /**
     * Creates {@link Document} document.
     * @return {@link Document} document.
     */
    public static Document createDocument() {
        final DocumentBuilder bob;
        try {
            bob = DBF.newDocumentBuilder();
        } catch (final ParserConfigurationException e) {
            return null;
        }
        return bob.newDocument();
    }

    /**
     * Adds values to data changed notification event element.
     *
     * @param doc
     *            {@link Document}
     * @param dataChangedNotificationEventElement
     *            {@link Element}
     * @param change
     *            {@link AsyncDataChangeEvent}
     */
    private void addValuesToDataChangedNotificationEventElement(final Document doc,
            final Element dataChangedNotificationEventElement,
            final AsyncDataChangeEvent<YangInstanceIdentifier, NormalizedNode<?, ?>> change,
            final SchemaContext  schemaContext, final DataSchemaContextTree dataSchemaContextTree) {

        addCreatedChangedValuesFromDataToElement(doc, change.getCreatedData().entrySet(),
                dataChangedNotificationEventElement,
                Operation.CREATED, schemaContext, dataSchemaContextTree);

        addCreatedChangedValuesFromDataToElement(doc, change.getUpdatedData().entrySet(),
                    dataChangedNotificationEventElement,
                    Operation.UPDATED, schemaContext, dataSchemaContextTree);

        addValuesFromDataToElement(doc, change.getRemovedPaths(), dataChangedNotificationEventElement,
                Operation.DELETED);
    }

    /**
     * Adds values from data to element.
     *
     * @param doc
     *            {@link Document}
     * @param data
     *            Set of {@link YangInstanceIdentifier}.
     * @param element
     *            {@link Element}
     * @param operation
     *            {@link Operation}
     */
    private void addValuesFromDataToElement(final Document doc, final Set<YangInstanceIdentifier> data,
            final Element element, final Operation operation) {
        if ((data == null) || data.isEmpty()) {
            return;
        }
        for (final YangInstanceIdentifier path : data) {
            if (!ControllerContext.getInstance().isNodeMixin(path)) {
                final Node node = createDataChangeEventElement(doc, path, operation);
                element.appendChild(node);
            }
        }
    }

    private void addCreatedChangedValuesFromDataToElement(final Document doc, final Set<Entry<YangInstanceIdentifier,
                NormalizedNode<?,?>>> data, final Element element, final Operation operation, final SchemaContext
            schemaContext, final DataSchemaContextTree dataSchemaContextTree) {
        if ((data == null) || data.isEmpty()) {
            return;
        }
        for (final Entry<YangInstanceIdentifier, NormalizedNode<?, ?>> entry : data) {
            if (!ControllerContext.getInstance().isNodeMixin(entry.getKey())) {
                final Node node = createCreatedChangedDataChangeEventElement(doc, entry, operation, schemaContext,
                        dataSchemaContextTree);
                element.appendChild(node);
            }
        }
    }

    /**
     * Creates changed event element from data.
     *
     * @param doc
     *            {@link Document}
     * @param path
     *            Path to data in data store.
     * @param operation
     *            {@link Operation}
     * @return {@link Node} node represented by changed event element.
     */
    private Node createDataChangeEventElement(final Document doc, final YangInstanceIdentifier path,
            final Operation operation) {
        final Element dataChangeEventElement = doc.createElement("data-change-event");
        final Element pathElement = doc.createElement("path");
        addPathAsValueToElement(path, pathElement);
        dataChangeEventElement.appendChild(pathElement);

        final Element operationElement = doc.createElement("operation");
        operationElement.setTextContent(operation.value);
        dataChangeEventElement.appendChild(operationElement);

        return dataChangeEventElement;
    }

    private Node createCreatedChangedDataChangeEventElement(final Document doc, final Entry<YangInstanceIdentifier,
            NormalizedNode<?, ?>> entry, final Operation operation, final SchemaContext
            schemaContext, final DataSchemaContextTree dataSchemaContextTree) {
        final Element dataChangeEventElement = doc.createElement("data-change-event");
        final Element pathElement = doc.createElement("path");
        final YangInstanceIdentifier path = entry.getKey();
        addPathAsValueToElement(path, pathElement);
        dataChangeEventElement.appendChild(pathElement);

        final Element operationElement = doc.createElement("operation");
        operationElement.setTextContent(operation.value);
        dataChangeEventElement.appendChild(operationElement);

        try {
            final DOMResult domResult = writeNormalizedNode(entry.getValue(), path,
                    schemaContext, dataSchemaContextTree);
            final Node result = doc.importNode(domResult.getNode().getFirstChild(), true);
            final Element dataElement = doc.createElement("data");
            dataElement.appendChild(result);
            dataChangeEventElement.appendChild(dataElement);
        } catch (final IOException e) {
            LOG.error("Error in writer ", e);
        } catch (final XMLStreamException e) {
            LOG.error("Error processing stream", e);
        }

        return dataChangeEventElement;
    }

    private static DOMResult writeNormalizedNode(final NormalizedNode<?, ?> normalized,
                                                 final YangInstanceIdentifier path, final SchemaContext context,
                                                 final DataSchemaContextTree dataSchemaContextTree)
            throws IOException, XMLStreamException {
        final XMLOutputFactory XML_FACTORY = XMLOutputFactory.newFactory();
        final Document doc = XmlDocumentUtils.getDocument();
        final DOMResult result = new DOMResult(doc);
        NormalizedNodeWriter normalizedNodeWriter = null;
        NormalizedNodeStreamWriter normalizedNodeStreamWriter = null;
        XMLStreamWriter writer = null;
        final SchemaPath nodePath;

        if ((normalized instanceof MapEntryNode) || (normalized instanceof UnkeyedListEntryNode)) {
            nodePath = dataSchemaContextTree.getChild(path).getDataSchemaNode().getPath();
        } else {
            nodePath = dataSchemaContextTree.getChild(path).getDataSchemaNode().getPath().getParent();
        }

        try {
            writer = XML_FACTORY.createXMLStreamWriter(result);
            normalizedNodeStreamWriter = XMLStreamNormalizedNodeStreamWriter.create(writer, context, nodePath);
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
     * Adds path as value to element.
     *
     * @param path
     *            Path to data in data store.
     * @param element
     *            {@link Element}
     */
    private void addPathAsValueToElement(final YangInstanceIdentifier path, final Element element) {
        final YangInstanceIdentifier normalizedPath = ControllerContext.getInstance().toXpathRepresentation(path);
        final StringBuilder textContent = new StringBuilder();

        for (final PathArgument pathArgument : normalizedPath.getPathArguments()) {
            if (pathArgument instanceof YangInstanceIdentifier.AugmentationIdentifier) {
                continue;
            }
            textContent.append("/");
            writeIdentifierWithNamespacePrefix(element, textContent, pathArgument.getNodeType());
            if (pathArgument instanceof NodeIdentifierWithPredicates) {
                final Map<QName, Object> predicates = ((NodeIdentifierWithPredicates) pathArgument).getKeyValues();
                for (final QName keyValue : predicates.keySet()) {
                    final String predicateValue = String.valueOf(predicates.get(keyValue));
                    textContent.append("[");
                    writeIdentifierWithNamespacePrefix(element, textContent, keyValue);
                    textContent.append("='");
                    textContent.append(predicateValue);
                    textContent.append("'");
                    textContent.append("]");
                }
            } else if (pathArgument instanceof NodeWithValue) {
                textContent.append("[.='");
                textContent.append(((NodeWithValue) pathArgument).getValue());
                textContent.append("'");
                textContent.append("]");
            }
        }
        element.setTextContent(textContent.toString());
    }

    /**
     * Writes identifier that consists of prefix and QName.
     *
     * @param element
     *            {@link Element}
     * @param textContent
     *            StringBuilder
     * @param qName
     *            QName
     */
    private static void writeIdentifierWithNamespacePrefix(final Element element, final StringBuilder textContent,
            final QName qName) {
        final Module module = ControllerContext.getInstance().getGlobalSchema()
                .findModuleByNamespaceAndRevision(qName.getNamespace(), qName.getRevision());

        textContent.append(module.getName());
        textContent.append(":");
        textContent.append(qName.getLocalName());
    }

    /**
     * Gets path pointed to data in data store.
     *
     * @return Path pointed to data in data store.
     */
    public YangInstanceIdentifier getPath() {
        return this.path;
    }

    /**
     * Sets {@link ListenerRegistration} registration.
     *
     * @param registration DOMDataChangeListener registration
     */
    public void setRegistration(final ListenerRegistration<DOMDataChangeListener> registration) {
        this.registration = registration;
    }

    /**
     * Gets the name of the stream.
     *
     * @return The name of the stream.
     */
    public String getStreamName() {
        return this.streamName;
    }

    /**
     * Removes all subscribers and unregisters event bus change recorder form
     * event bus and delete data in DS
     */
    public void close() throws Exception {
        final DOMDataWriteTransaction wTx = this.transactionChainHandler.get().newWriteOnlyTransaction();
        wTx.delete(LogicalDatastoreType.OPERATIONAL, IdentifierCodec.deserialize(MonitoringModule.PATH_TO_STREAM_WITHOUT_KEY
                        + this.path.getLastPathArgument().getNodeType().getLocalName(), this.schemaHandler.get()));
        wTx.submit().checkedGet();

        this.subscribers = new ConcurrentSet<>();
        this.registration.close();
        this.registration = null;
        this.eventBus.unregister(this.eventBusChangeRecorder);
    }

    /**
     * Checks if {@link ListenerRegistration} registration exist.
     *
     * @return True if exist, false otherwise.
     */
    public boolean isListening() {
        return this.registration == null ? false : true;
    }

    /**
     * Creates event of type {@link EventType#REGISTER}, set {@link Channel} subscriber to the event and post event into
     * event bus.
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
     * Creates event of type {@link EventType#DEREGISTER}, sets {@link Channel} subscriber to the event and posts event
     * into event bus.
     *
     * @param subscriber
     */
    public void removeSubscriber(final Channel subscriber) {
        LOG.debug("Subscriber {} is removed.", subscriber.remoteAddress());
        final Event event = new Event(EventType.DEREGISTER);
        event.setSubscriber(subscriber);
        this.eventBus.post(event);
    }

    /**
     * Checks if exists at least one {@link Channel} subscriber.
     *
     * @return True if exist at least one {@link Channel} subscriber, false otherwise.
     */
    public boolean hasSubscribers() {
        return !this.subscribers.isEmpty();
    }

    /**
     * Consists of two types {@link Store#CONFIG} and {@link Store#OPERATION}.
     */
    private static enum Store {
        CONFIG("config"),
        OPERATION("operation");

        private final String value;

        private Store(final String value) {
            this.value = value;
        }
    }

    /**
     * Consists of three types {@link Operation#CREATED}, {@link Operation#UPDATED} and {@link Operation#DELETED}.
     */
    private static enum Operation {
        CREATED("created"),
        UPDATED("updated"),
        DELETED("deleted");

        private final String value;

        private Operation(final String value) {
            this.value = value;
        }
    }

    /**
     * Set query parameters for listener
     *
     * @param start
     *            - start-time of getting notification
     * @param stop
     *            - stop-time of getting notification
     * @param filter
     *            - indicate which subset of all possible events are of interest
     */
    public void setQueryParams(final Date start, final Date stop, final String filter) {
        this.start = start;
        this.stop = stop;
        this.filter = filter;
    }

    /**
     * Get output type
     *
     * @return outputType
     */
    public String getOutputType() {
        return this.outputType.getName();
    }

    /**
     * Transaction chain to delete data in DS on close()
     *
     * @param transactionChainHandler
     *            - creating new write transaction to delete data on close
     * @param schemaHandler
     *            - for getting schema to deserialize
     *            {@link MonitoringModule#PATH_TO_STREAM_WITHOUT_KEY} to
     *            {@link YangInstanceIdentifier}
     */
    public void setCloseVars(final TransactionChainHandler transactionChainHandler,
            final SchemaContextHandler schemaHandler) {
        this.transactionChainHandler = transactionChainHandler;
        this.schemaHandler = schemaHandler;
    }

}
