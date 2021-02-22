/*
 * Copyright (c) 2014, 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.streams.listeners;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.time.Instant;
import java.util.Collection;
import java.util.Map.Entry;
import java.util.Optional;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.dom.DOMResult;
import org.json.XML;
import org.opendaylight.mdsal.dom.api.ClusteredDOMDataTreeChangeListener;
import org.opendaylight.netconf.sal.restconf.impl.ControllerContext;
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
import org.opendaylight.yangtools.yang.data.api.schema.tree.DataTreeCandidate;
import org.opendaylight.yangtools.yang.data.api.schema.tree.DataTreeCandidateNode;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextTree;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.Module;
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
public class ListenerAdapter extends AbstractCommonSubscriber implements ClusteredDOMDataTreeChangeListener {

    private static final Logger LOG = LoggerFactory.getLogger(ListenerAdapter.class);
    private static final String DATA_CHANGE_EVENT = "data-change-event";
    private static final String PATH = "path";
    private static final String OPERATION = "operation";

    private final ControllerContext controllerContext;
    private final YangInstanceIdentifier path;
    private final String streamName;
    private final NotificationOutputType outputType;

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
            final NotificationOutputType outputType, final ControllerContext controllerContext) {
        register(this);
        this.outputType = requireNonNull(outputType);
        this.path = requireNonNull(path);
        checkArgument(streamName != null && !streamName.isEmpty());
        this.streamName = streamName;
        this.controllerContext = controllerContext;
    }

    @Override
    public void onDataTreeChanged(final Collection<DataTreeCandidate> dataTreeCandidates) {
        final Instant now = Instant.now();
        if (!checkStartStop(now, this)) {
            return;
        }

        final String xml = prepareXml(dataTreeCandidates);
        if (checkFilter(xml)) {
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
            event.setData(XML.toJSONObject(xml).toString());
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
    private String prepareXml(final Collection<DataTreeCandidate> candidates) {
        final EffectiveModelContext schemaContext = controllerContext.getGlobalSchema();
        final DataSchemaContextTree dataContextTree = DataSchemaContextTree.from(schemaContext);
        final Document doc = createDocument();
        final Element notificationElement = basePartDoc(doc);

        final Element dataChangedNotificationEventElement = doc.createElementNS(
                "urn:opendaylight:params:xml:ns:yang:controller:md:sal:remote", "data-changed-notification");

        addValuesToDataChangedNotificationEventElement(doc, dataChangedNotificationEventElement, candidates,
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
     * @param dataTreeCandidates
     *            {@link DataTreeCandidate}
     */
    private void addValuesToDataChangedNotificationEventElement(final Document doc,
            final Element dataChangedNotificationEventElement,
            final Collection<DataTreeCandidate> dataTreeCandidates,
            final EffectiveModelContext schemaContext, final DataSchemaContextTree dataSchemaContextTree) {

        for (DataTreeCandidate dataTreeCandidate : dataTreeCandidates) {
            DataTreeCandidateNode candidateNode = dataTreeCandidate.getRootNode();
            if (candidateNode == null) {
                continue;
            }
            YangInstanceIdentifier yiid = dataTreeCandidate.getRootPath();

            boolean isSkipNotificationData = this.isSkipNotificationData();
            if (isSkipNotificationData) {
                createCreatedChangedDataChangeEventElementWithoutData(doc,
                        dataChangedNotificationEventElement, dataTreeCandidate.getRootNode());
            } else {
                addNodeToDataChangeNotificationEventElement(doc, dataChangedNotificationEventElement, candidateNode,
                        yiid.getParent(), schemaContext, dataSchemaContextTree);
            }
        }
    }

    private void addNodeToDataChangeNotificationEventElement(final Document doc,
            final Element dataChangedNotificationEventElement, final DataTreeCandidateNode candidateNode,
            final YangInstanceIdentifier parentYiid, final EffectiveModelContext schemaContext,
            final DataSchemaContextTree dataSchemaContextTree) {

        Optional<NormalizedNode<?,?>> optionalNormalizedNode = Optional.empty();
        switch (candidateNode.getModificationType()) {
            case APPEARED:
            case SUBTREE_MODIFIED:
            case WRITE:
                optionalNormalizedNode = candidateNode.getDataAfter();
                break;
            case DELETE:
            case DISAPPEARED:
                optionalNormalizedNode = candidateNode.getDataBefore();
                break;
            case UNMODIFIED:
            default:
                break;
        }

        if (optionalNormalizedNode.isEmpty()) {
            LOG.error("No node present in notification for {}", candidateNode);
            return;
        }

        NormalizedNode<?,?> normalizedNode = optionalNormalizedNode.get();
        YangInstanceIdentifier yiid = YangInstanceIdentifier.builder(parentYiid)
                                                            .append(normalizedNode.getIdentifier()).build();

        boolean isNodeMixin = controllerContext.isNodeMixin(yiid);
        boolean isSkippedNonLeaf = getLeafNodesOnly() && !(normalizedNode instanceof LeafNode);
        if (!isNodeMixin && !isSkippedNonLeaf) {
            Node node = null;
            switch (candidateNode.getModificationType()) {
                case APPEARED:
                case SUBTREE_MODIFIED:
                case WRITE:
                    Operation op = candidateNode.getDataBefore().isPresent() ? Operation.UPDATED : Operation.CREATED;
                    node = createCreatedChangedDataChangeEventElement(doc, yiid, normalizedNode, op,
                            schemaContext, dataSchemaContextTree);
                    break;
                case DELETE:
                case DISAPPEARED:
                    node = createDataChangeEventElement(doc, yiid, Operation.DELETED);
                    break;
                case UNMODIFIED:
                default:
                    break;
            }
            if (node != null) {
                dataChangedNotificationEventElement.appendChild(node);
            }
        }

        for (DataTreeCandidateNode childNode : candidateNode.getChildNodes()) {
            addNodeToDataChangeNotificationEventElement(doc, dataChangedNotificationEventElement, childNode,
                                                                        yiid, schemaContext, dataSchemaContextTree);
        }
    }

    /**
     * Creates changed event element from data.
     *
     * @param doc
     *            {@link Document}
     * @param dataPath
     *            Path to data in data store.
     * @param operation
     *            {@link Operation}
     * @return {@link Node} node represented by changed event element.
     */
    private Node createDataChangeEventElement(final Document doc, final YangInstanceIdentifier dataPath,
            final Operation operation) {
        final Element dataChangeEventElement = doc.createElement(DATA_CHANGE_EVENT);
        final Element pathElement = doc.createElement(PATH);
        addPathAsValueToElement(dataPath, pathElement);
        dataChangeEventElement.appendChild(pathElement);

        final Element operationElement = doc.createElement(OPERATION);
        operationElement.setTextContent(operation.value);
        dataChangeEventElement.appendChild(operationElement);

        return dataChangeEventElement;
    }

    /**
     * Creates data change notification element without data element.
     *
     * @param doc
     *       {@link Document}
     * @param dataChangedNotificationEventElement
     *       {@link Element}
     * @param candidateNode
     *       {@link DataTreeCandidateNode}
     */
    private void createCreatedChangedDataChangeEventElementWithoutData(final Document doc,
            final Element dataChangedNotificationEventElement, final DataTreeCandidateNode candidateNode) {
        final Operation operation;
        switch (candidateNode.getModificationType()) {
            case APPEARED:
            case SUBTREE_MODIFIED:
            case WRITE:
                operation = candidateNode.getDataBefore().isPresent() ? Operation.UPDATED : Operation.CREATED;
                break;
            case DELETE:
            case DISAPPEARED:
                operation = Operation.DELETED;
                break;
            case UNMODIFIED:
            default:
                return;
        }
        Node dataChangeEventElement = createDataChangeEventElement(doc, getPath(), operation);
        dataChangedNotificationEventElement.appendChild(dataChangeEventElement);

    }

    private Node createCreatedChangedDataChangeEventElement(final Document doc,
            final YangInstanceIdentifier eventPath, final NormalizedNode normalized, final Operation operation,
            final EffectiveModelContext schemaContext, final DataSchemaContextTree dataSchemaContextTree) {
        final Element dataChangeEventElement = doc.createElement(DATA_CHANGE_EVENT);
        final Element pathElement = doc.createElement(PATH);
        addPathAsValueToElement(eventPath, pathElement);
        dataChangeEventElement.appendChild(pathElement);

        final Element operationElement = doc.createElement(OPERATION);
        operationElement.setTextContent(operation.value);
        dataChangeEventElement.appendChild(operationElement);

        try {
            SchemaPath nodePath;
            if (normalized instanceof MapEntryNode || normalized instanceof UnkeyedListEntryNode) {
                nodePath = dataSchemaContextTree.findChild(eventPath).orElseThrow().getDataSchemaNode().getPath();
            } else {
                nodePath = dataSchemaContextTree.findChild(eventPath).orElseThrow().getDataSchemaNode().getPath()
                    .getParent();
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
     * @param dataPath
     *            Path to data in data store.
     * @param element
     *            {@link Element}
     */
    @SuppressWarnings("rawtypes")
    private void addPathAsValueToElement(final YangInstanceIdentifier dataPath, final Element element) {
        final YangInstanceIdentifier normalizedPath = controllerContext.toXpathRepresentation(dataPath);
        final StringBuilder textContent = new StringBuilder();

        for (final PathArgument pathArgument : normalizedPath.getPathArguments()) {
            if (pathArgument instanceof YangInstanceIdentifier.AugmentationIdentifier) {
                continue;
            }
            textContent.append("/");
            writeIdentifierWithNamespacePrefix(element, textContent, pathArgument.getNodeType());
            if (pathArgument instanceof NodeIdentifierWithPredicates) {
                for (final Entry<QName, Object> entry : ((NodeIdentifierWithPredicates) pathArgument).entrySet()) {
                    final QName keyValue = entry.getKey();
                    final String predicateValue = String.valueOf(entry.getValue());
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
     * @param qualifiedName
     *            QName
     */
    private void writeIdentifierWithNamespacePrefix(final Element element, final StringBuilder textContent,
            final QName qualifiedName) {
        final Module module = controllerContext.getGlobalSchema().findModule(qualifiedName.getModule())
                .get();

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
