/*
 * Copyright (c) 2014, 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams.listeners;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import java.io.IOException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.dom.DOMResult;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataChangeEvent;
import org.opendaylight.controller.md.sal.dom.api.DOMDataChangeListener;
import org.opendaylight.yang.gen.v1.urn.sal.restconf.event.subscription.rev140708.NotificationOutputTypeGrouping.NotificationOutputType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeWithValue;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.UnkeyedListEntryNode;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextTree;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * {@link ListenerAdapter} is responsible to track events, which occurred by
 * changing data in data source.
 */
public class ListenerAdapter extends AbstractCommonSubscriber implements DOMDataChangeListener {

    private static final Logger LOG = LoggerFactory.getLogger(ListenerAdapter.class);

    private final YangInstanceIdentifier path;
    private final String streamName;
    private final NotificationOutputType outputType;

    private AsyncDataChangeEvent<YangInstanceIdentifier, NormalizedNode<?, ?>> change;

    /**
     * Creates new {@link ListenerAdapter} listener specified by path and stream
     * name and register for subscribing.
     *
     * @param path
     *            Path to data in data store.
     * @param streamName
     *            The name of the stream.
     * @param outputType
     *            Type of output on notification (JSON, XML)
     */
    ListenerAdapter(final YangInstanceIdentifier path, final String streamName,
            final NotificationOutputType outputType) {
        super();
        register(this);
        setLocalNameOfPath(path.getLastPathArgument().getNodeType().getLocalName());

        this.outputType = Preconditions.checkNotNull(outputType);
        this.path = Preconditions.checkNotNull(path);
        Preconditions.checkArgument((streamName != null) && !streamName.isEmpty());
        this.streamName = streamName;
    }

    @Override
    public void onDataChanged(final AsyncDataChangeEvent<YangInstanceIdentifier, NormalizedNode<?, ?>> change) {
        this.change = change;
        final String xml = prepareXml();
        if (checkQueryParams(xml, this)) {
            prepareAndPostData(xml);
        }
    }

    /**
     * Gets the name of the stream.
     *
     * @return The name of the stream.
     */
    @Override
    public String getStreamName() {
        return this.streamName;
    }

    @Override
    public String getOutputType() {
        return this.outputType.getName();
    }

    /**
     * Get path pointed to data in data store.
     *
     * @return Path pointed to data in data store.
     */
    public YangInstanceIdentifier getPath() {
        return this.path;
    }

    /**
     * Prepare data of notification and data to client.
     *
     * @param xml   data
     */
    private void prepareAndPostData(final String xml) {
        final Event event = new Event(EventType.NOTIFY);
        if (this.outputType.equals(NotificationOutputType.JSON)) {
            try {
                final JsonNode node = new XmlMapper().readTree(xml.getBytes());
                event.setData(node.toString());
            } catch (final IOException e) {
                LOG.error("Error parsing XML {}", xml, e);
                Throwables.propagate(e);
            }
        } else {
            event.setData(xml);
        }
        post(event);
    }

    /**
     * Tracks events of data change by customer.
     */

    /**
     * Prepare data in printable form and transform it to String.
     *
     * @return Data in printable form.
     */
    private String prepareXml() {
        final SchemaContext schemaContext = schemaHandler.get();
        final DataSchemaContextTree dataContextTree = DataSchemaContextTree.from(schemaContext);
        final Document doc = createDocument();
        final Element notificationElement = basePartDoc(doc);

        final Element dataChangedNotificationEventElement = doc.createElementNS(
                "urn:opendaylight:params:xml:ns:yang:controller:md:sal:remote", "data-changed-notification");

        addValuesToDataChangedNotificationEventElement(doc, dataChangedNotificationEventElement, this.change,
                schemaContext, dataContextTree);
        notificationElement.appendChild(dataChangedNotificationEventElement);
        return transformDoc(doc);
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
            final SchemaContext schemaContext, final DataSchemaContextTree dataSchemaContextTree) {

        addCreatedChangedValuesFromDataToElement(doc, change.getCreatedData().entrySet(),
                dataChangedNotificationEventElement, Operation.CREATED, schemaContext, dataSchemaContextTree);

        addCreatedChangedValuesFromDataToElement(doc, change.getUpdatedData().entrySet(),
                dataChangedNotificationEventElement, Operation.UPDATED, schemaContext, dataSchemaContextTree);

        addValuesFromDataToElement(doc, change.getRemovedPaths(), dataChangedNotificationEventElement,
                Operation.DELETED, schemaContext, dataSchemaContextTree);
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
     * @param schemaContext
     * @param dataSchemaContextTree
     */
    private void addValuesFromDataToElement(final Document doc, final Set<YangInstanceIdentifier> data,
            final Element element, final Operation operation, final SchemaContext schemaContext,
            final DataSchemaContextTree dataSchemaContextTree) {
        if ((data == null) || data.isEmpty()) {
            return;
        }
        for (final YangInstanceIdentifier path : data) {
            if (!dataSchemaContextTree.getChild(path).isMixin()) {
                final Node node = createDataChangeEventElement(doc, path, operation, schemaContext);
                element.appendChild(node);
            }
        }
    }

    private void addCreatedChangedValuesFromDataToElement(final Document doc,
            final Set<Entry<YangInstanceIdentifier, NormalizedNode<?, ?>>> data, final Element element,
            final Operation operation, final SchemaContext schemaContext,
            final DataSchemaContextTree dataSchemaContextTree) {
        if ((data == null) || data.isEmpty()) {
            return;
        }
        for (final Entry<YangInstanceIdentifier, NormalizedNode<?, ?>> entry : data) {
            if (!dataSchemaContextTree.getChild(entry.getKey()).isMixin()
                    && (!getLeafNodesOnly() || entry.getValue() instanceof LeafNode)) {
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
     * @param schemaContext
     * @return {@link Node} node represented by changed event element.
     */
    private Node createDataChangeEventElement(final Document doc, final YangInstanceIdentifier path,
            final Operation operation, final SchemaContext schemaContext) {
        final Element dataChangeEventElement = doc.createElement("data-change-event");
        final Element pathElement = doc.createElement("path");
        addPathAsValueToElement(path, pathElement, schemaContext);
        dataChangeEventElement.appendChild(pathElement);

        final Element operationElement = doc.createElement("operation");
        operationElement.setTextContent(operation.value);
        dataChangeEventElement.appendChild(operationElement);

        return dataChangeEventElement;
    }

    private Node createCreatedChangedDataChangeEventElement(final Document doc,
            final Entry<YangInstanceIdentifier, NormalizedNode<?, ?>> entry, final Operation operation,
            final SchemaContext schemaContext, final DataSchemaContextTree dataSchemaContextTree) {
        final Element dataChangeEventElement = doc.createElement("data-change-event");
        final Element pathElement = doc.createElement("path");
        final YangInstanceIdentifier path = entry.getKey();
        addPathAsValueToElement(path, pathElement, schemaContext);
        dataChangeEventElement.appendChild(pathElement);

        final Element operationElement = doc.createElement("operation");
        operationElement.setTextContent(operation.value);
        dataChangeEventElement.appendChild(operationElement);

        try {
            SchemaPath nodePath;
            final NormalizedNode<?, ?> normalized = entry.getValue();
            if ((normalized instanceof MapEntryNode) || (normalized instanceof UnkeyedListEntryNode)) {
                nodePath = dataSchemaContextTree.getChild(path).getDataSchemaNode().getPath();
            } else {
                nodePath = dataSchemaContextTree.getChild(path).getDataSchemaNode().getPath().getParent();
            }
            final DOMResult domResult = writeNormalizedNode(normalized, schemaContext, nodePath);
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

    /**
     * Adds path as value to element.
     *
     * @param path
     *            Path to data in data store.
     * @param element
     *            {@link Element}
     * @param schemaContext
     */
    @SuppressWarnings("rawtypes")
    private void addPathAsValueToElement(final YangInstanceIdentifier path, final Element element,
            final SchemaContext schemaContext) {
        final StringBuilder textContent = new StringBuilder();

        for (final PathArgument pathArgument : path.getPathArguments()) {
            if (pathArgument instanceof YangInstanceIdentifier.AugmentationIdentifier) {
                continue;
            }
            textContent.append("/");
            writeIdentifierWithNamespacePrefix(element, textContent, pathArgument.getNodeType(), schemaContext);
            if (pathArgument instanceof NodeIdentifierWithPredicates) {
                final Map<QName, Object> predicates = ((NodeIdentifierWithPredicates) pathArgument).getKeyValues();
                for (final QName keyValue : predicates.keySet()) {
                    final String predicateValue = String.valueOf(predicates.get(keyValue));
                    textContent.append("[");
                    writeIdentifierWithNamespacePrefix(element, textContent, keyValue, schemaContext);
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
     * @param qualifiedName
     *            QName
     * @param schemaContext
     */
    private static void writeIdentifierWithNamespacePrefix(final Element element, final StringBuilder textContent,
            final QName qualifiedName, final SchemaContext schemaContext) {
        final Module module = schemaContext.findModuleByNamespaceAndRevision(qualifiedName.getNamespace(),
                qualifiedName.getRevision());

        textContent.append(module.getName());
        textContent.append(":");
        textContent.append(qualifiedName.getLocalName());
    }

    /**
     * Consists of three types {@link Operation#CREATED},
     * {@link Operation#UPDATED} and {@link Operation#DELETED}.
     */
    private enum Operation {
        CREATED("created"), UPDATED("updated"), DELETED("deleted");

        private final String value;

        Operation(final String value) {
            this.value = value;
        }
    }
}
