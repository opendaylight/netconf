/*
 * Copyright (c) 2018 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.mdsal.connector.ops;

import static org.opendaylight.netconf.api.xml.XmlNetconfConstants.URN_IETF_PARAMS_XML_NS_NETCONF_BASE_1_0;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.dom.DOMResult;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.api.DocumentedException.ErrorSeverity;
import org.opendaylight.netconf.api.DocumentedException.ErrorTag;
import org.opendaylight.netconf.api.DocumentedException.ErrorType;
import org.opendaylight.netconf.api.xml.XmlElement;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.mdsal.connector.CurrentSchemaContext;
import org.opendaylight.netconf.mdsal.connector.TransactionProvider;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeWriter;
import org.opendaylight.yangtools.yang.data.codec.xml.XMLStreamNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.NormalizedNodeResult;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public final class CopyConfig extends AbstractEdit {
    private static final String OPERATION_NAME = "copy-config";
    private static final String SOURCE_KEY = "source";
    private static final XMLOutputFactory XML_OUTPUT_FACTORY;

    static {
        XML_OUTPUT_FACTORY = XMLOutputFactory.newFactory();
        XML_OUTPUT_FACTORY.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES, true);
    }

    // Top-level "data" node without child nodes
    private static final ContainerNode EMPTY_ROOT_NODE = Builders.containerBuilder()
        .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(SchemaContext.NAME)).build();

    private final TransactionProvider transactionProvider;

    public CopyConfig(final String netconfSessionIdForReporting, final CurrentSchemaContext schemaContext,
                      final TransactionProvider transactionProvider) {
        super(netconfSessionIdForReporting, schemaContext);
        this.transactionProvider = transactionProvider;
    }

    @Override
    protected Element handleWithNoSubsequentOperations(final Document document, final XmlElement operationElement)
        throws DocumentedException {
        final XmlElement targetElement = extractTargetElement(operationElement, OPERATION_NAME);
        final String target = targetElement.getName();
        if (Datastore.running.toString().equals(target)) {
            throw new DocumentedException("edit-config on running datastore is not supported",
                ErrorType.PROTOCOL,
                ErrorTag.OPERATION_NOT_SUPPORTED,
                ErrorSeverity.ERROR);
        } else if (Datastore.candidate.toString().equals(target)) {
            copyToCandidate(operationElement);
        } else if (URL_KEY.equals(target)) {
            copyToUrl(targetElement, operationElement);
        } else {
            throw new DocumentedException("Unsupported target: " + target,
                ErrorType.PROTOCOL,
                ErrorTag.BAD_ELEMENT,
                ErrorSeverity.ERROR);
        }
        return document.createElement(XmlNetconfConstants.OK);
    }

    private void copyToCandidate(final XmlElement operationElement)
        throws DocumentedException {
        final XmlElement source = getSourceElement(operationElement);
        final List<XmlElement> configElements = getConfigElement(source).getChildElements();

        // <copy-config>, unlike <edit-config>, always replaces entire configuration,
        // so remove old configuration first:
        final DOMDataTreeReadWriteTransaction rwTx = transactionProvider.getOrCreateTransaction();
        rwTx.put(LogicalDatastoreType.CONFIGURATION, YangInstanceIdentifier.empty(), EMPTY_ROOT_NODE);

        // Then create nodes present in the <config> element:
        for (final XmlElement element : configElements) {
            final String ns = element.getNamespace();
            final DataSchemaNode schemaNode = getSchemaNodeFromNamespace(ns, element);
            final NormalizedNodeResult resultHolder = new NormalizedNodeResult();
            parseIntoNormalizedNode(schemaNode, element, ImmutableNormalizedNodeStreamWriter.from(resultHolder));
            final NormalizedNode<?, ?> data = resultHolder.getResult();
            final YangInstanceIdentifier path = YangInstanceIdentifier.create(data.getIdentifier());
            // Doing merge instead of put to support top-level list:
            rwTx.merge(LogicalDatastoreType.CONFIGURATION, path, data);
        }
    }

    private static XmlElement getSourceElement(final XmlElement parent) throws DocumentedException {
        final Optional<XmlElement> sourceElement = parent.getOnlyChildElementOptionally(SOURCE_KEY);
        if (!sourceElement.isPresent()) {
            throw new DocumentedException("<source> element is missing",
                DocumentedException.ErrorType.PROTOCOL,
                DocumentedException.ErrorTag.MISSING_ELEMENT,
                DocumentedException.ErrorSeverity.ERROR);
        }

        return sourceElement.get();
    }

    private void copyToUrl(final XmlElement urlElement, final XmlElement operationElement) throws DocumentedException {
        final String url = urlElement.getTextContent();
        if (!url.startsWith("file:")) {
            throw new DocumentedException("Unsupported <url> protocol: " + url,
                ErrorType.PROTOCOL,
                ErrorTag.OPERATION_NOT_SUPPORTED,
                ErrorSeverity.ERROR);
        }

        // Read data from datastore:
        final XmlElement source = getSourceElement(operationElement).getOnlyChildElement();
        final ContainerNode data = readData(source);

        // Transform NN to XML:
        final Document document = operationElement.getDomElement().getOwnerDocument();
        final Node node = transformNormalizedNode(document, data);

        // Save XML to file:
        final String xml = XmlUtil.toString((Element) node);
        try {
            final Path file = Paths.get(new URI(url));
            Files.write(file, xml.getBytes(StandardCharsets.UTF_8));
        } catch (URISyntaxException | IllegalArgumentException e) {
            throw new DocumentedException("Invalid URI: " + url, e,
                ErrorType.RPC,
                ErrorTag.INVALID_VALUE,
                ErrorSeverity.ERROR);
        } catch (IOException e) {
            throw new DocumentedException("Failed to write : " + url, e,
                ErrorType.APPLICATION,
                ErrorTag.OPERATION_FAILED,
                ErrorSeverity.ERROR);
        }
    }

    private ContainerNode readData(final XmlElement source) throws DocumentedException {
        final Datastore sourceDatastore = getDatastore(source);
        final DOMDataTreeReadWriteTransaction rwTx = getTransaction(sourceDatastore);
        final YangInstanceIdentifier dataRoot = YangInstanceIdentifier.empty();
        try {
            final Optional<NormalizedNode<?, ?>> normalizedNodeOptional = rwTx.read(
                LogicalDatastoreType.CONFIGURATION, dataRoot).get();
            if (sourceDatastore == Datastore.running) {
                transactionProvider.abortRunningTransaction(rwTx);
            }
            return (ContainerNode) normalizedNodeOptional.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new IllegalStateException("Unable to read data " + dataRoot, e);
        }
    }

    private static Datastore getDatastore(final XmlElement source) throws DocumentedException {
        try {
            return Datastore.valueOf(source.getName());
        } catch (IllegalArgumentException e) {
            throw new DocumentedException("Unsupported source for <url> target", e,
                ErrorType.PROTOCOL,
                ErrorTag.OPERATION_NOT_SUPPORTED,
                ErrorSeverity.ERROR);
        }
    }

    private DOMDataTreeReadWriteTransaction getTransaction(final Datastore datastore) throws DocumentedException {
        if (datastore == Datastore.candidate) {
            return transactionProvider.getOrCreateTransaction();
        } else if (datastore == Datastore.running) {
            return transactionProvider.createRunningTransaction();
        }
        throw new DocumentedException("Incorrect Datastore: ", ErrorType.PROTOCOL, ErrorTag.BAD_ELEMENT,
            ErrorSeverity.ERROR);
    }

    private Node transformNormalizedNode(final Document document, final ContainerNode data) {
        final Element configElement = document.createElementNS(URN_IETF_PARAMS_XML_NS_NETCONF_BASE_1_0, CONFIG_KEY);
        final DOMResult result = new DOMResult(configElement);
        try {
            final XMLStreamWriter xmlWriter = XML_OUTPUT_FACTORY.createXMLStreamWriter(result);
            final NormalizedNodeStreamWriter nnStreamWriter = XMLStreamNormalizedNodeStreamWriter.create(xmlWriter,
                schemaContext.getCurrentContext(), SchemaPath.ROOT);

            final NormalizedNodeWriter nnWriter = NormalizedNodeWriter.forStreamWriter(nnStreamWriter, true);
            for (DataContainerChild<? extends YangInstanceIdentifier.PathArgument, ?> child : data.getValue()) {
                nnWriter.write(child);
            }
            nnWriter.flush();
            xmlWriter.flush();
        } catch (XMLStreamException | IOException e) {
            throw new RuntimeException(e);
        }
        return result.getNode();
    }

    @Override
    protected String getOperationName() {
        return OPERATION_NAME;
    }
}
