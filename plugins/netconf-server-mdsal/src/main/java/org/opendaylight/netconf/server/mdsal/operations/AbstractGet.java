/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.mdsal.operations;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.util.Optional;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.dom.DOMResult;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.api.NamespaceURN;
import org.opendaylight.netconf.api.xml.XmlElement;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.server.api.operations.AbstractSingletonNetconfOperation;
import org.opendaylight.netconf.server.mdsal.CurrentSchemaContext;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;
import org.opendaylight.yangtools.yang.common.ErrorSeverity;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeWriter;
import org.opendaylight.yangtools.yang.data.api.schema.stream.YangInstanceIdentifierWriter;
import org.opendaylight.yangtools.yang.data.codec.xml.XMLStreamNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

// FIXME: seal when we have JDK17+
abstract class AbstractGet extends AbstractSingletonNetconfOperation {
    private static final XMLOutputFactory XML_OUTPUT_FACTORY;
    private static final String FILTER = "filter";

    static {
        XML_OUTPUT_FACTORY = XMLOutputFactory.newFactory();
        XML_OUTPUT_FACTORY.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES, true);
    }

    private final CurrentSchemaContext schemaContext;
    private final FilterContentValidator validator;

    AbstractGet(final SessionIdType sessionId, final CurrentSchemaContext schemaContext) {
        super(sessionId);
        this.schemaContext = schemaContext;
        validator = new FilterContentValidator(schemaContext);
    }

    // FIXME: throw a DocumentedException
    private Node transformNormalizedNode(final Document document, final NormalizedNode data,
                                         final YangInstanceIdentifier dataRoot) {
        final DOMResult result = new DOMResult(document.createElement(XmlNetconfConstants.DATA_KEY));
        final XMLStreamWriter xmlWriter = getXmlStreamWriter(result);
        final EffectiveModelContext currentContext = schemaContext.getCurrentContext();

        final NormalizedNodeStreamWriter nnStreamWriter = XMLStreamNormalizedNodeStreamWriter.create(xmlWriter,
            currentContext);

        try {
            if (dataRoot.isEmpty()) {
                writeRoot(nnStreamWriter, data);
            } else {
                write(nnStreamWriter, currentContext, dataRoot.coerceParent(), data);
            }
        } catch (IOException e) {
            // FIXME: throw DocumentedException
            throw new IllegalStateException(e);
        }

        return result.getNode();
    }

    private static void write(final NormalizedNodeStreamWriter nnStreamWriter,
            final EffectiveModelContext currentContext, final YangInstanceIdentifier parent, final NormalizedNode data)
                throws IOException {
        try (var yiidWriter = YangInstanceIdentifierWriter.open(nnStreamWriter, currentContext, parent)) {
            try (var nnWriter = NormalizedNodeWriter.forStreamWriter(nnStreamWriter, true)) {
                nnWriter.write(data);
            }
        }
    }

    private static void writeRoot(final NormalizedNodeStreamWriter nnStreamWriter, final NormalizedNode data)
            throws IOException {
        checkArgument(data instanceof ContainerNode, "Unexpected root data %s", data);

        try (var nnWriter = NormalizedNodeWriter.forStreamWriter(nnStreamWriter, true)) {
            for (var child : ((ContainerNode) data).body()) {
                nnWriter.write(child);
            }
        }
    }

    private static XMLStreamWriter getXmlStreamWriter(final DOMResult result) {
        try {
            return XML_OUTPUT_FACTORY.createXMLStreamWriter(result);
        } catch (final XMLStreamException e) {
            // FIXME: throw DocumentedException
            throw new IllegalStateException(e);
        }
    }

    final Element serializeNodeWithParentStructure(final Document document, final YangInstanceIdentifier dataRoot,
                                                   final NormalizedNode node) {
        return (Element) transformNormalizedNode(document, node, dataRoot);
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
    final Optional<YangInstanceIdentifier> getDataRootFromFilter(final XmlElement operationElement)
            throws DocumentedException {
        final var optFilterElement = operationElement.getOnlyChildElementOptionally(FILTER);
        if (optFilterElement.isEmpty()) {
            return Optional.of(YangInstanceIdentifier.of());
        }

        final var filterElement = optFilterElement.orElseThrow();
        if (filterElement.getChildElements().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(getInstanceIdentifierFromFilter(filterElement));
    }

    @VisibleForTesting
    protected final YangInstanceIdentifier getInstanceIdentifierFromFilter(final XmlElement filterElement)
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
                NamespaceURN.BASE);
            return sourceElement.isEmpty() ? Optional.empty()
                : Optional.of(Datastore.valueOf(sourceElement.orElseThrow().getOnlyChildElement().getName()));
        }

        private static void validateInputRpc(final XmlElement xml, final String operationName)
                throws DocumentedException {
            xml.checkName(operationName);
            xml.checkNamespace(NamespaceURN.BASE);
        }

        public Optional<Datastore> getDatastore() {
            return datastore;
        }
    }
}
