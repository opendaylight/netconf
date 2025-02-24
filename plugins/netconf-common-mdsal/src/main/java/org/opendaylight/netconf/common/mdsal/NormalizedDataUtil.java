/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.common.mdsal;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.api.NamespaceURN;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.databind.DatabindContext;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.schema.MountPointContext;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedMetadata;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedMetadataWriter;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter.MetadataExtension;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeWriter;
import org.opendaylight.yangtools.yang.data.codec.xml.XMLStreamNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.codec.xml.XmlParserStream;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.NormalizationResultHolder;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

/**
 * Utility methods to work with {@link NormalizedNode} and related constructs in the context of NETCONF protocol.
 */
public final class NormalizedDataUtil {
    /**
     * Shim interface to handle differences around namespace handling between various XMLStreamWriter implementations.
     * Specifically:
     * <ul>
     *   <li>OpenJDK DOM writer (com.sun.xml.internal.stream.writers.XMLDOMWriterImpl) throws
     *       UnsupportedOperationException from its setNamespaceContext() method</li>
     *   <li>Woodstox DOM writer (com.ctc.wstx.dom.WstxDOMWrappingWriter) works with namespace context, but treats
     *       setPrefix() calls as hints -- which are not discoverable.</li>
     * </ul>
     *
     * <p>Due to this we perform a quick test for behavior and decide the appropriate strategy.
     */
    @FunctionalInterface
    private interface NamespaceSetter {
        void initializeNamespace(XMLStreamWriter writer) throws XMLStreamException;

        static NamespaceSetter forFactory(final XMLOutputFactory xmlFactory) {
            final var namespaceContext = new AnyXmlNamespaceContext(Map.of("op", NamespaceURN.BASE));

            try {
                final var testWriter = xmlFactory.createXMLStreamWriter(new DOMResult(XmlUtil.newDocument()));
                testWriter.setNamespaceContext(namespaceContext);
            } catch (final UnsupportedOperationException e) {
                // This happens with JDK's DOM writer, which we may be using
                LOG.debug("Unable to set namespace context, falling back to setPrefix()", e);
                return writer -> writer.setPrefix("op", NamespaceURN.BASE);
            } catch (XMLStreamException e) {
                throw new ExceptionInInitializerError(e);
            }

            // Success, we can use setNamespaceContext()
            return writer -> writer.setNamespaceContext(namespaceContext);
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(NormalizedDataUtil.class);

    public static final XMLOutputFactory XML_FACTORY;

    static {
        final var factory = XMLOutputFactory.newFactory();
        // FIXME: not repairing namespaces is probably common, this should be availabe as common XML constant.
        factory.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES, false);
        XML_FACTORY = factory;
    }

    // FIXME: this needs to be called when and why?
    private static final NamespaceSetter XML_NAMESPACE_SETTER = NamespaceSetter.forFactory(XML_FACTORY);

    private final @NonNull DatabindContext databind;

    public NormalizedDataUtil(final DatabindContext databind) {
        this.databind = requireNonNull(databind);
    }

    /**
     * Write {@code normalized} data along with corresponding {@code metadata} into {@link DOMResult}.
     *
     * @param data data to be written
     * @param metadata  optional metadata to be written
     * @param result DOM result holder
     * @param parent optional schema node identifier of the parent node
     * @throws IOException when failed to write data into {@link NormalizedNodeStreamWriter}
     * @throws XMLStreamException when failed to serialize data into XML document
     */
    @NonNullByDefault
    public void writeNode(final NormalizedNode data, final @Nullable NormalizedMetadata metadata,
            final DOMResult result, final @Nullable Absolute parent) throws IOException, XMLStreamException {
        if (metadata != null) {
            writeNode(databind.modelContext(), data, metadata, result, parent);
        } else {
            writeNode(databind.modelContext(), data, result, parent);
        }
    }

    @NonNullByDefault
    private static void writeNode(final EffectiveModelContext modelContext, final NormalizedNode data,
            final DOMResult result, final @Nullable Absolute parent) throws IOException, XMLStreamException {
        final var xmlWriter = XML_FACTORY.createXMLStreamWriter(result);
        try (var streamWriter = XMLStreamNormalizedNodeStreamWriter.create(xmlWriter, modelContext, parent);
             var writer = NormalizedNodeWriter.forStreamWriter(streamWriter)) {
            writer.write(data);
            writer.flush();
        } finally {
            xmlWriter.close();
        }
    }

    @NonNullByDefault
    private static void writeNode(final EffectiveModelContext modelContext, final NormalizedNode data,
            final NormalizedMetadata metadata, final DOMResult result, final @Nullable Absolute parent)
                throws IOException, XMLStreamException {
        final var xmlWriter = XML_FACTORY.createXMLStreamWriter(result);
        XML_NAMESPACE_SETTER.initializeNamespace(xmlWriter);
        try (var streamWriter = XMLStreamNormalizedNodeStreamWriter.create(xmlWriter, modelContext, parent);
             var writer = NormalizedMetadataWriter.forStreamWriter(streamWriter)) {
            writer.write(data, metadata);
            writer.flush();
        } finally {
            xmlWriter.close();
        }
    }

    /**
     * Write elements equivalent to specified by {@link YangInstanceIdentifier} along with corresponding
     * {@code metadata} into {@link DOMResult}.
     *
     * @param path path to write
     * @param metadata optional metadata to be written
     * @param result DOM result holder
     * @param parent optional schema node identifier of the parent node
     * @throws IOException when failed to write data into {@link NormalizedNodeStreamWriter}
     * @throws XMLStreamException when failed to serialize data into XML document
     */
    @NonNullByDefault
    public void writePath(final YangInstanceIdentifier path, final @Nullable NormalizedMetadata metadata,
            final DOMResult result, final @Nullable Absolute parent) throws IOException, XMLStreamException {
        if (metadata != null) {
            writePath(databind.modelContext(), path, metadata, result, parent);
        } else {
            writePath(databind.modelContext(), path, result, parent);
        }
    }

    @NonNullByDefault
    private static void writePath(final EffectiveModelContext modelContext, final YangInstanceIdentifier path,
            final DOMResult result, final @Nullable Absolute parent) throws IOException, XMLStreamException {
        final var xmlWriter = XML_FACTORY.createXMLStreamWriter(result);
        XML_NAMESPACE_SETTER.initializeNamespace(xmlWriter);
        try (var streamWriter = XMLStreamNormalizedNodeStreamWriter.create(xmlWriter, modelContext, parent);
             var writer = new EmptyListXmlWriter(streamWriter, xmlWriter)) {
            final var it = path.getPathArguments().iterator();
            final var first = it.next();
            StreamingContext.fromSchemaAndQNameChecked(modelContext, first.getNodeType())
                .streamToWriter(writer, first, it);
        } finally {
            xmlWriter.close();
        }
    }

    @NonNullByDefault
    private static void writePath(final EffectiveModelContext modelContext, final YangInstanceIdentifier path,
            final NormalizedMetadata metadata, final DOMResult result, final @Nullable Absolute parent)
                throws IOException, XMLStreamException {
        final var xmlWriter = XML_FACTORY.createXMLStreamWriter(result);
        XML_NAMESPACE_SETTER.initializeNamespace(xmlWriter);
        try (var streamWriter = XMLStreamNormalizedNodeStreamWriter.create(xmlWriter, modelContext, parent);
             var writer = new EmptyListXmlMetadataWriter(streamWriter, xmlWriter,
                 streamWriter.extension(MetadataExtension.class), metadata)) {
            final var it = path.getPathArguments().iterator();
            final var first = it.next();
            StreamingContext.fromSchemaAndQNameChecked(modelContext, first.getNodeType())
                .streamToWriter(writer, first, it);
        } finally {
            xmlWriter.close();
        }
    }

    /**
     * Write {@code normalized} data into {@link DOMResult}.
     *
     * @param normalized data to be written
     * @param result     DOM result holder
     * @param context    mountpoint schema context
     * @param path       optional schema node identifier of the parent node
     * @throws IOException        when failed to write data into {@link NormalizedNodeStreamWriter}
     * @throws XMLStreamException when failed to serialize data into XML document
     */
    @Deprecated
    public static void writeNormalizedNode(final NormalizedNode normalized, final DOMResult result,
            final EffectiveModelContext context, final @Nullable Absolute path) throws IOException, XMLStreamException {
        writeNode(context, normalized, result, path);
    }

    /**
     * Write {@code normalized} data along with corresponding {@code metadata} into {@link DOMResult}.
     *
     * @param normalized data to be written
     * @param metadata   metadata to be written
     * @param result     DOM result holder
     * @param context    mountpoint schema context
     * @param path       optional schema node identifier of the parent node
     * @throws IOException        when failed to write data into {@link NormalizedNodeStreamWriter}
     * @throws XMLStreamException when failed to serialize data into XML document
     */
    @Deprecated
    public static void writeNormalizedNode(final NormalizedNode normalized, final @Nullable NormalizedMetadata metadata,
            final DOMResult result, final EffectiveModelContext context, final @Nullable Absolute path)
                throws IOException, XMLStreamException {
        if (metadata != null) {
            writeNode(context, normalized, metadata, result, path);
        } else {
            writeNode(context, normalized, result, path);
        }
    }

    /**
     * Write data specified by {@link YangInstanceIdentifier} into {@link DOMResult}.
     *
     * @param query      path to the root node
     * @param result     DOM result holder
     * @param context    mountpoint schema context
     * @param path       optional schema node identifier of the parent node
     * @throws IOException        when failed to write data into {@link NormalizedNodeStreamWriter}
     * @throws XMLStreamException when failed to serialize data into XML document
     */
    @Deprecated
    public static void writeNormalizedNode(final YangInstanceIdentifier query, final DOMResult result,
            final EffectiveModelContext context, final @Nullable Absolute path) throws IOException, XMLStreamException {
        writePath(context, query, result, path);
    }

    /**
     * Write data specified by {@link YangInstanceIdentifier} along with corresponding {@code metadata}
     * into {@link DOMResult}.
     *
     * @param query      path to the root node
     * @param metadata   metadata to be written
     * @param result     DOM result holder
     * @param context    mountpoint schema context
     * @param path       optional schema node identifier of the parent node
     * @throws IOException        when failed to write data into {@link NormalizedNodeStreamWriter}
     * @throws XMLStreamException when failed to serialize data into XML document
     */
    @Deprecated
    public static void writeNormalizedNode(final YangInstanceIdentifier query,
            final @Nullable NormalizedMetadata metadata, final DOMResult result, final EffectiveModelContext context,
            final @Nullable Absolute path) throws IOException, XMLStreamException {
        if (metadata != null) {
            writePath(context, query, metadata, result, path);
        } else {
            writePath(context, query, result, path);
        }
    }

    /**
     * Writing subtree filter specified by {@link YangInstanceIdentifier} into {@link DOMResult}.
     *
     * @param query      path to the root node
     * @param result     DOM result holder
     * @param context    mountpoint schema context
     * @param path       optional schema node identifier of the parent node
     * @throws IOException        failed to write filter into {@link NormalizedNodeStreamWriter}
     * @throws XMLStreamException failed to serialize filter into XML document
     */
    public static void writeFilter(final YangInstanceIdentifier query, final DOMResult result,
            final EffectiveModelContext context, final @Nullable Absolute path) throws IOException, XMLStreamException {
        if (query.isEmpty()) {
            // No query at all
            return;
        }

        final var xmlWriter = XML_FACTORY.createXMLStreamWriter(result);
        try (var streamWriter = XMLStreamNormalizedNodeStreamWriter.create(xmlWriter, context, path);
             var writer = new EmptyListXmlWriter(streamWriter, xmlWriter)) {
            final var it = query.getPathArguments().iterator();
            final var first = it.next();
            StreamingContext.fromSchemaAndQNameChecked(context, first.getNodeType()).streamToWriter(writer, first, it);
        } finally {
            xmlWriter.close();
        }
    }

    /**
     * Writing subtree filter specified by parent {@link YangInstanceIdentifier} and specific fields
     * into {@link DOMResult}. Field paths are relative to parent query path.
     *
     * @param query      path to the root node
     * @param result     DOM result holder
     * @param context    mountpoint schema context
     * @param path       optional schema node identifier of the parent node
     * @param fields     list of specific fields for which the filter should be created
     * @throws IOException        failed to write filter into {@link NormalizedNodeStreamWriter}
     * @throws XMLStreamException failed to serialize filter into XML document
     * @throws NullPointerException if any argument is null
     */
    public static void writeFilter(final YangInstanceIdentifier query, final DOMResult result,
                                   final EffectiveModelContext context, final @Nullable Absolute path,
                                   final List<YangInstanceIdentifier> fields) throws IOException, XMLStreamException {
        if (query.isEmpty() || fields.isEmpty()) {
            // No query at all
            return;
        }

        final var aggregatedFields = aggregateFields(fields);
        final var rootNode = constructPathArgumentTree(query, aggregatedFields);

        final var xmlWriter = XML_FACTORY.createXMLStreamWriter(result);
        try {
            try (var streamWriter = XMLStreamNormalizedNodeStreamWriter.create(xmlWriter, context, path);
                 var writer = new EmptyListXmlWriter(streamWriter, xmlWriter)) {
                final var first = rootNode.element();
                StreamingContext.fromSchemaAndQNameChecked(context, first.getNodeType())
                        .streamToWriter(writer, first, rootNode);
            }
        } finally {
            xmlWriter.close();
        }
    }

    /**
     * Writing subtree filter specified by parent {@link YangInstanceIdentifier} and specific fields
     * into {@link Element}. Field paths are relative to parent query path. Filter is created without following
     * {@link EffectiveModelContext}.
     *
     * @param query         path to the root node
     * @param fields        list of specific fields for which the filter should be created
     * @param filterElement XML filter element to which the created filter will be written
     */
    public static void writeSchemalessFilter(final YangInstanceIdentifier query,
                                             final List<YangInstanceIdentifier> fields, final Element filterElement) {
        pathArgumentTreeToXmlStructure(constructPathArgumentTree(query, aggregateFields(fields)), filterElement);
    }

    private static void pathArgumentTreeToXmlStructure(final PathNode pathArgumentTree, final Element data) {
        final var pathArg = pathArgumentTree.element();

        final var nodeType = pathArg.getNodeType();
        final var elementNamespace = nodeType.getNamespace().toString();

        if (data.getElementsByTagNameNS(elementNamespace, nodeType.getLocalName()).getLength() != 0) {
            // element has already been written as list key
            return;
        }

        final var childElement = data.getOwnerDocument().createElementNS(elementNamespace, nodeType.getLocalName());
        data.appendChild(childElement);
        if (pathArg instanceof NodeIdentifierWithPredicates nip) {
            appendListKeyNodes(childElement, nip);
        }
        for (var childNode : pathArgumentTree.children()) {
            pathArgumentTreeToXmlStructure(childNode, childElement);
        }
    }

    /**
     * Appending list key elements to parent element.
     *
     * @param parentElement parent XML element to which children elements are appended
     * @param listEntryId   list entry identifier
     */
    public static void appendListKeyNodes(final Element parentElement, final NodeIdentifierWithPredicates listEntryId) {
        for (var key : listEntryId.entrySet()) {
            final var qname = key.getKey();
            final var keyElement = parentElement.getOwnerDocument()
                .createElementNS(qname.getNamespace().toString(), qname.getLocalName());
            keyElement.setTextContent(key.getValue().toString());
            parentElement.appendChild(keyElement);
        }
    }

    /**
     * Aggregation of the fields paths based on parenthesis. Only parent/enclosing {@link YangInstanceIdentifier}
     * are kept. For example, paths '/x/y/z', '/x/y', and '/x' are aggregated into single field path: '/x'
     *
     * @param fields paths of fields
     * @return filtered {@link List} of paths
     */
    private static List<YangInstanceIdentifier> aggregateFields(final List<YangInstanceIdentifier> fields) {
        return fields.stream()
            .filter(field -> fields.stream()
                .filter(fieldYiid -> !field.equals(fieldYiid))
                .noneMatch(fieldYiid -> fieldYiid.contains(field)))
            .collect(Collectors.toList());
    }

    /**
     * Construct a tree based on the parent {@link YangInstanceIdentifier} and provided list of fields. The goal of this
     * procedure is the elimination of the redundancy that is introduced by potentially overlapping parts of the fields
     * paths.
     *
     * @param query  path to parent element
     * @param fields subpaths relative to parent path that identify specific fields
     * @return created {@link PathNode} structure
     */
    private static PathNode constructPathArgumentTree(final YangInstanceIdentifier query,
            final List<YangInstanceIdentifier> fields) {
        final var queryIterator = query.getPathArguments().iterator();
        final var rootTreeNode = new PathNode(queryIterator.next());

        var queryTreeNode = rootTreeNode;
        while (queryIterator.hasNext()) {
            queryTreeNode = queryTreeNode.ensureChild(queryIterator.next());
        }

        for (var field : fields) {
            var actualFieldTreeNode = queryTreeNode;
            for (var fieldPathArg : field.getPathArguments()) {
                actualFieldTreeNode = actualFieldTreeNode.ensureChild(fieldPathArg);
            }
        }
        return rootTreeNode;
    }

    public static NormalizationResultHolder transformDOMSourceToNormalizedNode(final MountPointContext mount,
            final DOMSource value) throws XMLStreamException, URISyntaxException, IOException, SAXException {
        final var resultHolder = new NormalizationResultHolder();
        final var writer = ImmutableNormalizedNodeStreamWriter.from(resultHolder);
        try (var parser = XmlParserStream.create(writer, new ProxyMountPointContext(mount))) {
            parser.traverse(value);
        }
        return resultHolder;
    }

    // FIXME: document this interface contract. Does it support RFC8528/RFC8542? How?
    public static NormalizationResultHolder transformDOMSourceToNormalizedNode(
            final EffectiveModelContext schemaContext, final DOMSource value)
                throws XMLStreamException, URISyntaxException, IOException, SAXException {
        return transformDOMSourceToNormalizedNode(MountPointContext.of(schemaContext), value);
    }
}
