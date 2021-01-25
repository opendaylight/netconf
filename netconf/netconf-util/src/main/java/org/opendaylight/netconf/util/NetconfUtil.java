/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.util;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.api.xml.XmlElement;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.yangtools.rcf8528.data.util.EmptyMountPointContext;
import org.opendaylight.yangtools.rfc7952.data.api.NormalizedMetadata;
import org.opendaylight.yangtools.rfc7952.data.util.NormalizedMetadataWriter;
import org.opendaylight.yangtools.rfc8528.data.api.MountPointContext;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeWriter;
import org.opendaylight.yangtools.yang.data.codec.xml.XMLStreamNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.codec.xml.XmlCodecFactory;
import org.opendaylight.yangtools.yang.data.codec.xml.XmlParserStream;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.NormalizedNodeResult;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

public final class NetconfUtil {
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
     * <p>
     * Due to this we perform a quick test for behavior and decide the appropriate strategy.
     */
    @FunctionalInterface
    private interface NamespaceSetter {
        void initializeNamespace(XMLStreamWriter writer) throws XMLStreamException;

        static NamespaceSetter forFactory(final XMLOutputFactory xmlFactory) {
            final String netconfNamespace = NETCONF_QNAME.getNamespace().toString();
            final AnyXmlNamespaceContext namespaceContext = new AnyXmlNamespaceContext(ImmutableMap.of(
                "op", netconfNamespace));

            try {
                final XMLStreamWriter testWriter = xmlFactory.createXMLStreamWriter(new DOMResult(
                    XmlUtil.newDocument()));
                testWriter.setNamespaceContext(namespaceContext);
            } catch (final UnsupportedOperationException e) {
                // This happens with JDK's DOM writer, which we may be using
                LOG.warn("Unable to set namespace context, falling back to setPrefix()", e);
                return writer -> writer.setPrefix("op", netconfNamespace);
            } catch (XMLStreamException e) {
                throw new ExceptionInInitializerError(e);
            }

            // Success, we can use setNamespaceContext()
            return writer -> writer.setNamespaceContext(namespaceContext);
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(NetconfUtil.class);

    // FIXME: document what exactly this QName means, as it is not referring to a tangible node nor the ietf-module.
    // FIXME: what is this contract saying?
    //        - is it saying all data is going to be interpreted with this root?
    //        - is this saying we are following a specific interface contract (i.e. do we have schema mounts?)
    //        - is it also inferring some abilities w.r.t. RFC8342?
    public static final QName NETCONF_QNAME = QName.create(QNameModule.create(SchemaContext.NAME.getNamespace(),
        Revision.of("2011-06-01")), "netconf").intern();
    // FIXME: is this the device-bound revision?
    public static final QName NETCONF_DATA_QNAME = QName.create(NETCONF_QNAME, "data").intern();

    public static final XMLOutputFactory XML_FACTORY;

    static {
        final XMLOutputFactory f = XMLOutputFactory.newFactory();
        // FIXME: not repairing namespaces is probably common, this should be availabe as common XML constant.
        f.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES, false);
        XML_FACTORY = f;
    }

    private static final NamespaceSetter XML_NAMESPACE_SETTER = NamespaceSetter.forFactory(XML_FACTORY);

    private NetconfUtil() {
        // No-op
    }

    public static Document checkIsMessageOk(final Document response) throws DocumentedException {
        final XmlElement docElement = XmlElement.fromDomDocument(response);
        // FIXME: we should throw DocumentedException here
        checkState(XmlNetconfConstants.RPC_REPLY_KEY.equals(docElement.getName()));
        final XmlElement element = docElement.getOnlyChildElement();
        if (XmlNetconfConstants.OK.equals(element.getName())) {
            return response;
        }

        LOG.warn("Can not load last configuration. Operation failed.");
        // FIXME: we should be throwing a DocumentedException here
        throw new IllegalStateException("Can not load last configuration. Operation failed: "
                + XmlUtil.toString(response));
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    public static void writeNormalizedNode(final NormalizedNode<?, ?> normalized, final DOMResult result,
                                           final SchemaPath schemaPath, final SchemaContext context)
            throws IOException, XMLStreamException {
        final XMLStreamWriter writer = XML_FACTORY.createXMLStreamWriter(result);
        try (
             NormalizedNodeStreamWriter normalizedNodeStreamWriter =
                     XMLStreamNormalizedNodeStreamWriter.create(writer, context, schemaPath);
             NormalizedNodeWriter normalizedNodeWriter =
                     NormalizedNodeWriter.forStreamWriter(normalizedNodeStreamWriter)
        ) {
            normalizedNodeWriter.write(normalized);
            normalizedNodeWriter.flush();
        } finally {
            try {
                if (writer != null) {
                    writer.close();
                }
            } catch (final Exception e) {
                LOG.warn("Unable to close resource properly", e);
            }
        }
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    public static void writeNormalizedNode(final NormalizedNode<?, ?> normalized,
                                           final @Nullable NormalizedMetadata metadata,
                                           final DOMResult result, final SchemaPath schemaPath,
                                           final SchemaContext context) throws IOException, XMLStreamException {
        if (metadata == null) {
            writeNormalizedNode(normalized, result, schemaPath, context);
            return;
        }

        final XMLStreamWriter writer = XML_FACTORY.createXMLStreamWriter(result);
        XML_NAMESPACE_SETTER.initializeNamespace(writer);
        try (
             NormalizedNodeStreamWriter normalizedNodeStreamWriter =
                     XMLStreamNormalizedNodeStreamWriter.create(writer, context, schemaPath);
                NormalizedMetadataWriter normalizedNodeWriter =
                     NormalizedMetadataWriter.forStreamWriter(normalizedNodeStreamWriter)
        ) {
            normalizedNodeWriter.write(normalized, metadata);
            normalizedNodeWriter.flush();
        } finally {
            try {
                if (writer != null) {
                    writer.close();
                }
            } catch (final Exception e) {
                LOG.warn("Unable to close resource properly", e);
            }
        }
    }

    /**
     * Writing subtree filter specified by {@link YangInstanceIdentifier} into {@link DOMResult}.
     *
     * @param query      path to the root node
     * @param result     DOM result holder
     * @param schemaPath schema path of the parent node
     * @param context    mountpoint schema context
     * @throws IOException        failed to write filter into {@link NormalizedNodeStreamWriter}
     * @throws XMLStreamException failed to serialize filter into XML document
     */
    public static void writeFilter(final YangInstanceIdentifier query, final DOMResult result,
            final SchemaPath schemaPath, final SchemaContext context) throws IOException, XMLStreamException {
        if (query.isEmpty()) {
            // No query at all
            return;
        }

        final XMLStreamWriter xmlWriter = XML_FACTORY.createXMLStreamWriter(result);
        try {
            try (NormalizedNodeStreamWriter streamWriter =
                    XMLStreamNormalizedNodeStreamWriter.create(xmlWriter, context, schemaPath);
                 EmptyListXmlWriter writer = new EmptyListXmlWriter(streamWriter, xmlWriter)) {
                final Iterator<PathArgument> it = query.getPathArguments().iterator();
                final PathArgument first = it.next();
                StreamingContext.fromSchemaAndQNameChecked(context, first.getNodeType()).streamToWriter(writer, first,
                    it);
            }
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
     * @param schemaPath schema path of the parent node
     * @param context    mountpoint schema context
     * @param fields     list of specific fields for which the filter should be created
     * @throws IOException        failed to write filter into {@link NormalizedNodeStreamWriter}
     * @throws XMLStreamException failed to serialize filter into XML document
     * @throws NullPointerException if any argument is null
     */
    public static void writeFilter(final YangInstanceIdentifier query, final DOMResult result,
                                   final SchemaPath schemaPath, final SchemaContext context,
                                   final List<YangInstanceIdentifier> fields) throws IOException, XMLStreamException {
        if (query.isEmpty() || fields.isEmpty()) {
            // No query at all
            return;
        }
        final List<YangInstanceIdentifier> aggregatedFields = aggregateFields(fields);
        final PathNode rootNode = constructPathArgumentTree(query, aggregatedFields);

        final XMLStreamWriter xmlWriter = XML_FACTORY.createXMLStreamWriter(result);
        try {
            try (NormalizedNodeStreamWriter streamWriter =
                    XMLStreamNormalizedNodeStreamWriter.create(xmlWriter, context, schemaPath);
                 EmptyListXmlWriter writer = new EmptyListXmlWriter(streamWriter, xmlWriter)) {
                final PathArgument first = rootNode.element();
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
        final PathArgument pathArg = pathArgumentTree.element();

        final QName nodeType = pathArg.getNodeType();
        final String elementNamespace = nodeType.getNamespace().toString();

        if (data.getElementsByTagNameNS(elementNamespace, nodeType.getLocalName()).getLength() != 0) {
            // element has already been written as list key
            return;
        }

        final Element childElement = data.getOwnerDocument().createElementNS(elementNamespace, nodeType.getLocalName());
        data.appendChild(childElement);
        if (pathArg instanceof NodeIdentifierWithPredicates) {
            appendListKeyNodes(childElement, (NodeIdentifierWithPredicates) pathArg);
        }
        for (final PathNode childrenNode : pathArgumentTree.children()) {
            pathArgumentTreeToXmlStructure(childrenNode, childElement);
        }
    }

    /**
     * Appending list key elements to parent element.
     *
     * @param parentElement parent XML element to which children elements are appended
     * @param listEntryId   list entry identifier
     */
    public static void appendListKeyNodes(final Element parentElement, final NodeIdentifierWithPredicates listEntryId) {
        for (Entry<QName, Object> key : listEntryId.entrySet()) {
            final Element keyElement = parentElement.getOwnerDocument().createElementNS(
                    key.getKey().getNamespace().toString(), key.getKey().getLocalName());
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
     * @return created {@link TreeNode} structure
     */
    private static PathNode constructPathArgumentTree(final YangInstanceIdentifier query,
            final List<YangInstanceIdentifier> fields) {
        final Iterator<PathArgument> queryIterator = query.getPathArguments().iterator();
        final PathNode rootTreeNode = new PathNode(queryIterator.next());

        PathNode queryTreeNode = rootTreeNode;
        while (queryIterator.hasNext()) {
            queryTreeNode = queryTreeNode.ensureChild(queryIterator.next());
        }

        for (final YangInstanceIdentifier field : fields) {
            PathNode actualFieldTreeNode = queryTreeNode;
            for (final PathArgument fieldPathArg : field.getPathArguments()) {
                actualFieldTreeNode = actualFieldTreeNode.ensureChild(fieldPathArg);
            }
        }
        return rootTreeNode;
    }

    public static NormalizedNodeResult transformDOMSourceToNormalizedNode(final MountPointContext mountContext,
            final DOMSource value) throws XMLStreamException, URISyntaxException, IOException, SAXException {
        final NormalizedNodeResult resultHolder = new NormalizedNodeResult();
        final NormalizedNodeStreamWriter writer = ImmutableNormalizedNodeStreamWriter.from(resultHolder);
        final XmlCodecFactory codecs = XmlCodecFactory.create(mountContext);

        // FIXME: we probably need to propagate MountPointContext here and not just the child nodes
        final ContainerSchemaNode dataRead = new NodeContainerProxy(NETCONF_DATA_QNAME,
            mountContext.getSchemaContext().getChildNodes());
        try (XmlParserStream xmlParserStream = XmlParserStream.create(writer, codecs, dataRead)) {
            xmlParserStream.traverse(value);
        }
        return resultHolder;
    }


    // FIXME: document this interface contract. Does it support RFC8528/RFC8542? How?
    public static NormalizedNodeResult transformDOMSourceToNormalizedNode(final EffectiveModelContext schemaContext,
            final DOMSource value) throws XMLStreamException, URISyntaxException, IOException, SAXException {
        return transformDOMSourceToNormalizedNode(new EmptyMountPointContext(schemaContext), value);
    }
}
