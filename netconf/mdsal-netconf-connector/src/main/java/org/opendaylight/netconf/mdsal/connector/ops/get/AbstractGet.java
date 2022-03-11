/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.mdsal.connector.ops.get;

import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.dom.DOMResult;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.api.xml.XmlElement;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.mdsal.connector.CurrentSchemaContext;
import org.opendaylight.netconf.mdsal.connector.ops.Datastore;
import org.opendaylight.netconf.util.mapping.AbstractSingletonNetconfOperation;
import org.opendaylight.yangtools.yang.common.ErrorSeverity;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.AugmentationIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeWriter;
import org.opendaylight.yangtools.yang.data.api.schema.stream.YangInstanceIdentifierWriter;
import org.opendaylight.yangtools.yang.data.codec.xml.XMLStreamNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.model.api.DataNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public abstract class AbstractGet extends AbstractSingletonNetconfOperation {
    private static final XMLOutputFactory XML_OUTPUT_FACTORY;
    private static final YangInstanceIdentifier ROOT = YangInstanceIdentifier.empty();
    private static final String FILTER = "filter";

    static {
        XML_OUTPUT_FACTORY = XMLOutputFactory.newFactory();
        XML_OUTPUT_FACTORY.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES, true);
    }

    protected final CurrentSchemaContext schemaContext;
    private final FilterContentValidator validator;

    public AbstractGet(final String netconfSessionIdForReporting, final CurrentSchemaContext schemaContext) {
        super(netconfSessionIdForReporting);
        this.schemaContext = schemaContext;
        this.validator = new FilterContentValidator(schemaContext);
    }

    protected Node transformNormalizedNode(final Document document, final NormalizedNode data,
                                           final YangInstanceIdentifier dataRoot) {

        final DOMResult result = new DOMResult(document.createElement(XmlNetconfConstants.DATA_KEY));

        final XMLStreamWriter xmlWriter = getXmlStreamWriter(result);

        final SchemaPath schemaPath = getSchemaPath(dataRoot);
        final NormalizedNodeStreamWriter nnStreamWriter = XMLStreamNormalizedNodeStreamWriter.create(xmlWriter,
                schemaContext.getCurrentContext(), schemaPath);

        final DataSchemaNode dataSchemaNode;
        if (dataRoot.isEmpty()) {
            dataSchemaNode = schemaContext.getCurrentContext();
        } else {
            final Optional<DataSchemaNode> dataTreeChild =
                    schemaContext.getCurrentContext().findDataTreeChild(schemaPath.getPathFromRoot());
            dataSchemaNode = dataTreeChild.orElseThrow(
                    () -> new IllegalArgumentException("Unable to find schema node for " + dataRoot));
        }

        try (var yiidWriter = YangInstanceIdentifierWriter.open(nnStreamWriter,
                (DataNodeContainer) dataSchemaNode, dataRoot)) {
            try (var nnWriter = NormalizedNodeWriter.forStreamWriter(nnStreamWriter, true)) {
                if (data instanceof ContainerNode) {
                    writeRootElement(xmlWriter, nnWriter, (ContainerNode) data);
                } else if (data instanceof MapNode) {
                    writeRootElement(xmlWriter, nnWriter, (MapNode) data);
                } else {
                    throw new IllegalArgumentException("Unable to transform node of type: "
                            + data.getClass().toString() + " offending node: " + data);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return result.getNode();
    }

    private static XMLStreamWriter getXmlStreamWriter(final DOMResult result) {
        try {
            return XML_OUTPUT_FACTORY.createXMLStreamWriter(result);
        } catch (final XMLStreamException e) {
            throw new RuntimeException(e);
        }
    }

    private static SchemaPath getSchemaPath(final YangInstanceIdentifier dataRoot) {

        return SchemaPath.create(dataRoot.getPathArguments().stream()
                .filter(p -> !(p instanceof NodeIdentifierWithPredicates))
                .filter(p -> !(p instanceof AugmentationIdentifier))
                .map(PathArgument::getNodeType)
                .collect(Collectors.toList()), true);
    }

    private static void writeRootElement(final XMLStreamWriter xmlWriter, final NormalizedNodeWriter nnWriter,
                                         final ContainerNode data) throws IOException {
        if (data.getIdentifier().getNodeType().equals(SchemaContext.NAME)) {
            for (final DataContainerChild child : data.body()) {
                nnWriter.write(child);
            }
        } else {
            nnWriter.write(data);
        }
    }

    private static void writeRootElement(final XMLStreamWriter xmlWriter, final NormalizedNodeWriter nnWriter,
                                         final MapNode data) throws IOException {
        if (data.getIdentifier().getNodeType().equals(SchemaContext.NAME)) {
            for (final MapEntryNode child : data.body()) {
                nnWriter.write(child);
            }
        } else {
            nnWriter.write(data);
        }
    }

    protected Element serializeNodeWithParentStructure(final Document document, final YangInstanceIdentifier dataRoot,
                                                       final NormalizedNode node) {
        if (!dataRoot.equals(ROOT)) {
            return (Element) transformNormalizedNode(document, node, dataRoot);
        }
        return (Element) transformNormalizedNode(document, node, ROOT);
    }

    /**
     * Obtain data root according to filter from operation element.
     *
     * @param operationElement operation element
     * @return if filter is present and not empty returns Optional of the InstanceIdentifier to the read location
     *      in datastore. Empty filter returns Optional.absent() which should equal an empty &lt;data/&gt;
     *      container in the response. If filter is not present we want to read the entire datastore - return ROOT.
     * @throws DocumentedException if not possible to get identifier from filter
     */
    protected Optional<YangInstanceIdentifier> getDataRootFromFilter(final XmlElement operationElement)
            throws DocumentedException {
        final Optional<XmlElement> filterElement = operationElement.getOnlyChildElementOptionally(FILTER);
        if (filterElement.isPresent()) {
            if (filterElement.get().getChildElements().size() == 0) {
                return Optional.empty();
            }
            return Optional.of(getInstanceIdentifierFromFilter(filterElement.get()));
        }

        return Optional.of(ROOT);
    }

    @VisibleForTesting
    protected YangInstanceIdentifier getInstanceIdentifierFromFilter(final XmlElement filterElement)
            throws DocumentedException {

        if (filterElement.getChildElements().size() != 1) {
            throw new DocumentedException("Multiple filter roots not supported yet",
                    ErrorType.APPLICATION, ErrorTag.OPERATION_NOT_SUPPORTED, ErrorSeverity.ERROR);
        }

        final XmlElement element = filterElement.getOnlyChildElement();
        return validator.validate(element);
    }

    protected static final class GetConfigExecution {
        private final Optional<Datastore> datastore;

        GetConfigExecution(final Optional<Datastore> datastore) {
            this.datastore = datastore;
        }

        static GetConfigExecution fromXml(final XmlElement xml, final String operationName) throws DocumentedException {
            try {
                validateInputRpc(xml, operationName);
            } catch (final DocumentedException e) {
                throw new DocumentedException("Incorrect RPC: " + e.getMessage(), e, e.getErrorType(), e.getErrorTag(),
                        e.getErrorSeverity(), e.getErrorInfo());
            }

            final Optional<Datastore> sourceDatastore;
            try {
                sourceDatastore = parseSource(xml);
            } catch (final DocumentedException e) {
                throw new DocumentedException("Get-config source attribute error: " + e.getMessage(), e,
                        e.getErrorType(), e.getErrorTag(), e.getErrorSeverity(), e.getErrorInfo());
            }

            return new GetConfigExecution(sourceDatastore);
        }

        private static Optional<Datastore> parseSource(final XmlElement xml) throws DocumentedException {
            final Optional<XmlElement> sourceElement = xml.getOnlyChildElementOptionally(XmlNetconfConstants.SOURCE_KEY,
                    XmlNetconfConstants.URN_IETF_PARAMS_XML_NS_NETCONF_BASE_1_0);
            return sourceElement.isPresent()
                    ? Optional.of(Datastore.valueOf(sourceElement.get().getOnlyChildElement().getName()))
                    : Optional.empty();
        }

        private static void validateInputRpc(final XmlElement xml, final String operationName) throws
                DocumentedException {
            xml.checkName(operationName);
            xml.checkNamespace(XmlNetconfConstants.URN_IETF_PARAMS_XML_NS_NETCONF_BASE_1_0);
        }

        public Optional<Datastore> getDatastore() {
            return datastore;
        }
    }
}
