/*
 * Copyright (c) 2014, 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams.listeners;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import java.io.IOException;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.dom.DOMResult;
import org.json.XML;
import org.opendaylight.mdsal.dom.api.ClusteredDOMDataTreeChangeListener;
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
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextNode;
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
 * {@link ListenerAdapter} is responsible to track events, which occurred by changing data in data source.
 */
public class ListenerAdapter extends AbstractCommonSubscriber implements ClusteredDOMDataTreeChangeListener {

    private static final Logger LOG = LoggerFactory.getLogger(ListenerAdapter.class);

    private final YangInstanceIdentifier path;
    private final String streamName;
    private final NotificationOutputType outputType;

    /**
     * Creates new {@link ListenerAdapter} listener specified by path and stream name and register for subscribing.
     *
     * @param path       Path to data in data store.
     * @param streamName The name of the stream.
     * @param outputType Type of output on notification (JSON, XML).
     */
    ListenerAdapter(final YangInstanceIdentifier path, final String streamName,
            final NotificationOutputType outputType) {
        setLocalNameOfPath(path.getLastPathArgument().getNodeType().getLocalName());

        this.outputType = Preconditions.checkNotNull(outputType);
        this.path = Preconditions.checkNotNull(path);
        Preconditions.checkArgument(streamName != null && !streamName.isEmpty());
        this.streamName = streamName;
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
     * @param xml XML-formatted data.
     */
    private void prepareAndPostData(final String xml) {
        if (this.outputType.equals(NotificationOutputType.JSON)) {
            post(XML.toJSONObject(xml).toString());
        } else {
            post(xml);
        }
    }

    /**
     * Prepare data in printable form and transform it to String.
     *
     * @param dataTreeCandidates Data-tree candidates to be transformed.
     * @return Data in printable form.
     */
    private String prepareXml(final Collection<DataTreeCandidate> dataTreeCandidates) {
        final SchemaContext schemaContext = schemaHandler.get();
        final DataSchemaContextTree dataContextTree = DataSchemaContextTree.from(schemaContext);
        final Document doc = createDocument();
        final Element notificationElement = basePartDoc(doc);

        final Element dataChangedNotificationEventElement = doc.createElementNS(
                "urn:opendaylight:params:xml:ns:yang:controller:md:sal:remote", "data-changed-notification");

        addValuesToDataChangedNotificationEventElement(doc, dataChangedNotificationEventElement, dataTreeCandidates,
                schemaContext, dataContextTree);
        notificationElement.appendChild(dataChangedNotificationEventElement);
        return transformDoc(doc);
    }

    /**
     * Adds values to data changed notification event element.
     */
    @SuppressWarnings("checkstyle:hiddenField")
    private void addValuesToDataChangedNotificationEventElement(final Document doc,
            final Element dataChangedNotificationEventElement, final Collection<DataTreeCandidate> dataTreeCandidates,
            final SchemaContext schemaContext, final DataSchemaContextTree dataSchemaContextTree) {

        for (DataTreeCandidate dataTreeCandidate : dataTreeCandidates) {
            DataTreeCandidateNode candidateNode = dataTreeCandidate.getRootNode();
            if (candidateNode == null) {
                continue;
            }
            YangInstanceIdentifier yiid = dataTreeCandidate.getRootPath();
            addNodeToDataChangeNotificationEventElement(doc, dataChangedNotificationEventElement, candidateNode,
                    yiid.getParent(), schemaContext, dataSchemaContextTree);
        }
    }

    private void addNodeToDataChangeNotificationEventElement(final Document doc,
            final Element dataChangedNotificationEventElement, final DataTreeCandidateNode candidateNode,
            final YangInstanceIdentifier parentYiid, final SchemaContext schemaContext,
            final DataSchemaContextTree dataSchemaContextTree) {

        Optional<NormalizedNode<?, ?>> optionalNormalizedNode = Optional.empty();
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

        if (!optionalNormalizedNode.isPresent()) {
            LOG.error("No node present in notification for {}", candidateNode);
            return;
        }

        NormalizedNode<?, ?> normalizedNode = optionalNormalizedNode.get();
        YangInstanceIdentifier yiid = YangInstanceIdentifier.builder(parentYiid)
                .append(normalizedNode.getIdentifier()).build();

        final Optional<DataSchemaContextNode<?>> childrenSchemaNode = dataSchemaContextTree.findChild(yiid);
        Preconditions.checkState(childrenSchemaNode.isPresent());
        boolean isNodeMixin = childrenSchemaNode.get().isMixin();
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
                    node = createDataChangeEventElement(doc, yiid, schemaContext);
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
            addNodeToDataChangeNotificationEventElement(
                    doc, dataChangedNotificationEventElement, childNode, yiid, schemaContext, dataSchemaContextTree);
        }
    }

    /**
     * Creates data-changed event element from data.
     *
     * @param doc           {@link Document}
     * @param schemaContext Schema context.
     * @return {@link Node} represented by changed event element.
     */
    private Node createDataChangeEventElement(final Document doc, final YangInstanceIdentifier eventPath,
            final SchemaContext schemaContext) {
        final Element dataChangeEventElement = doc.createElement("data-change-event");
        final Element pathElement = doc.createElement("path");
        addPathAsValueToElement(eventPath, pathElement, schemaContext);
        dataChangeEventElement.appendChild(pathElement);

        final Element operationElement = doc.createElement("operation");
        operationElement.setTextContent(Operation.DELETED.value);
        dataChangeEventElement.appendChild(operationElement);

        return dataChangeEventElement;
    }

    private Node createCreatedChangedDataChangeEventElement(final Document doc, final YangInstanceIdentifier eventPath,
            final NormalizedNode<?, ?> normalized, final Operation operation, final SchemaContext schemaContext,
            final DataSchemaContextTree dataSchemaContextTree) {
        final Element dataChangeEventElement = doc.createElement("data-change-event");
        final Element pathElement = doc.createElement("path");
        addPathAsValueToElement(eventPath, pathElement, schemaContext);
        dataChangeEventElement.appendChild(pathElement);

        final Element operationElement = doc.createElement("operation");
        operationElement.setTextContent(operation.value);
        dataChangeEventElement.appendChild(operationElement);

        try {
            SchemaPath nodePath;
            final Optional<DataSchemaContextNode<?>> childrenSchemaNode = dataSchemaContextTree.findChild(eventPath);
            Preconditions.checkState(childrenSchemaNode.isPresent());
            if (normalized instanceof MapEntryNode || normalized instanceof UnkeyedListEntryNode) {
                nodePath = childrenSchemaNode.get().getDataSchemaNode().getPath();
            } else {
                nodePath = childrenSchemaNode.get().getDataSchemaNode().getPath().getParent();
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
     * @param eventPath     Path to data in data store.
     * @param element       {@link Element}
     * @param schemaContext Schema context.
     */
    @SuppressWarnings("rawtypes")
    private void addPathAsValueToElement(final YangInstanceIdentifier eventPath, final Element element,
            final SchemaContext schemaContext) {
        final StringBuilder textContent = new StringBuilder();

        for (final PathArgument pathArgument : eventPath.getPathArguments()) {
            if (pathArgument instanceof YangInstanceIdentifier.AugmentationIdentifier) {
                continue;
            }
            textContent.append("/");
            writeIdentifierWithNamespacePrefix(textContent, pathArgument.getNodeType(), schemaContext);
            if (pathArgument instanceof NodeIdentifierWithPredicates) {
                final Map<QName, Object> predicates = ((NodeIdentifierWithPredicates) pathArgument).getKeyValues();
                for (final Entry<QName, Object> entry : predicates.entrySet()) {
                    final QName keyValue = entry.getKey();
                    final String predicateValue = String.valueOf(entry.getValue());
                    textContent.append("[");
                    writeIdentifierWithNamespacePrefix(textContent, keyValue, schemaContext);
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
     * @param textContent   Text builder that should be supplemented by QName and its modules name.
     * @param qualifiedName QName of the element.
     * @param schemaContext Schema context that holds modules which should contain module specified in QName.
     */
    private static void writeIdentifierWithNamespacePrefix(final StringBuilder textContent, final QName qualifiedName,
            final SchemaContext schemaContext) {
        final Optional<Module> module = schemaContext.findModule(qualifiedName.getModule());
        if (module.isPresent()) {
            textContent.append(module.get().getName());
            textContent.append(":");
            textContent.append(qualifiedName.getLocalName());
        } else {
            LOG.error("Cannot write identifier with namespace prefix in data-change listener adapter: "
                    + "Cannot find module in schema context for input QName {}.", qualifiedName);
            throw new IllegalStateException(String.format("Cannot find module in schema context for input QName %s.",
                    qualifiedName));
        }
    }

    /**
     * Consists of three types {@link Operation#CREATED}, {@link Operation#UPDATED} and {@link Operation#DELETED}.
     */
    private enum Operation {
        CREATED("created"),
        UPDATED("updated"),
        DELETED("deleted");

        private final String value;

        Operation(final String value) {
            this.value = value;
        }
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