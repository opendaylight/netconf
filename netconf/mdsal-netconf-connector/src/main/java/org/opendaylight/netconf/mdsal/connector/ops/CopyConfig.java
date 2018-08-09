/*
 * Copyright (c) 2018 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.mdsal.connector.ops;

import static org.opendaylight.netconf.api.xml.XmlNetconfConstants.URN_IETF_PARAMS_XML_NS_NETCONF_BASE_1_0;

import com.google.common.base.Optional;
import com.google.common.collect.Iterables;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.dom.DOMResult;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadWriteTransaction;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public final class CopyConfig extends AbstractEdit {
    private static final String OPERATION_NAME = "copy-config";
    private static final String SOURCE_KEY = "source";
    private static final Logger LOG = LoggerFactory.getLogger(CopyConfig.class);
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
            copyToUrl(operationElement, targetElement);
        } else {
            throw new DocumentedException("Unsupported target: " + target,
                ErrorType.PROTOCOL,
                ErrorTag.BAD_ELEMENT,
                ErrorSeverity.ERROR);
        }
        return XmlUtil.createElement(document, XmlNetconfConstants.OK, Optional.absent());
    }

    private void copyToCandidate(final XmlElement operationElement)
        throws DocumentedException {
        final XmlElement source = getSourceElement(operationElement);
        final List<XmlElement> configElements = getConfigElement(source).getChildElements();

        // <copy-config>, unlike <edit-config>, always replaces entire configuration,
        // so remove old configuration first:
        final DOMDataReadWriteTransaction rwTx = transactionProvider.getOrCreateTransaction();
        rwTx.put(LogicalDatastoreType.CONFIGURATION, YangInstanceIdentifier.EMPTY, EMPTY_ROOT_NODE);

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

    private void copyToUrl(final XmlElement operationElement, final XmlElement urlElement) throws DocumentedException {
        final XmlElement sourceType = getSourceElement(operationElement).getOnlyChildElement();
        try{
            final Datastore sourceDatastore = Datastore.valueOf(sourceType.getName());

            // Read data from datastore
            final DOMDataReadWriteTransaction rwTx = getTransaction(sourceDatastore);
            final YangInstanceIdentifier dataRoot = YangInstanceIdentifier.EMPTY;
            try {
                final Optional<NormalizedNode<?, ?>> normalizedNodeOptional = rwTx.read(
                    LogicalDatastoreType.CONFIGURATION, dataRoot).checkedGet();
                if (sourceDatastore == Datastore.running) {
                    transactionProvider.abortRunningTransaction(rwTx);
                }

                // Save to URL specified in targetElement
                final Node node = transformNormalizedNode(operationElement.getDomElement().getOwnerDocument(),
                    normalizedNodeOptional.get(), dataRoot);
                final String xml = XmlUtil.toString((Element) node);
                // TODO: require file:
                try {
                    final Path file = Paths.get(new URI(urlElement.getTextContent()));
                    Files.write(file, xml.getBytes(StandardCharsets.UTF_8));
                    //Files.write(a.getBytes(StandardCharsets.UTF_8), file);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (URISyntaxException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }


            } catch (final ReadFailedException e) {
                LOG.warn("Unable to read data: {}", dataRoot, e);
                throw new IllegalStateException("Unable to read data " + dataRoot, e);
            }

        } catch (IllegalArgumentException e) {
            throw new DocumentedException("Unsupported source for <url> target",
                ErrorType.PROTOCOL,
                ErrorTag.OPERATION_NOT_SUPPORTED,
                ErrorSeverity.ERROR);
        }



    }

    private DOMDataReadWriteTransaction getTransaction(final Datastore datastore) throws DocumentedException {
        if (datastore == Datastore.candidate) {
            return transactionProvider.getOrCreateTransaction();
        } else if (datastore == Datastore.running) {
            return transactionProvider.createRunningTransaction();
        }
        throw new DocumentedException("Incorrect Datastore: ", ErrorType.PROTOCOL, ErrorTag.BAD_ELEMENT,
            ErrorSeverity.ERROR);
    }

    private Node transformNormalizedNode(final Document document, final NormalizedNode<?, ?> data,
                                         final YangInstanceIdentifier dataRoot) {
        final DOMResult result = new DOMResult(document.createElementNS(URN_IETF_PARAMS_XML_NS_NETCONF_BASE_1_0, CONFIG_KEY));
        final XMLStreamWriter xmlWriter = getXmlStreamWriter(result);

        final NormalizedNodeStreamWriter nnStreamWriter = XMLStreamNormalizedNodeStreamWriter.create(xmlWriter,
            schemaContext.getCurrentContext(), getSchemaPath(dataRoot));

        final NormalizedNodeWriter nnWriter = NormalizedNodeWriter.forStreamWriter(nnStreamWriter, true);

        writeRootElement(xmlWriter, nnWriter, (ContainerNode) data);
        return result.getNode();
    }

    private static void writeRootElement(final XMLStreamWriter xmlWriter, final NormalizedNodeWriter nnWriter,
                                         final ContainerNode data) {
        try {
            if (data.getNodeType().equals(SchemaContext.NAME)) {
                for (final DataContainerChild<? extends YangInstanceIdentifier.PathArgument, ?> child : data.getValue()) {
                    nnWriter.write(child);
                }
            } else {
                nnWriter.write(data);
            }
            nnWriter.flush();
            xmlWriter.flush();
        } catch (XMLStreamException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static XMLStreamWriter getXmlStreamWriter(final DOMResult result) {
        try {
            return XML_OUTPUT_FACTORY.createXMLStreamWriter(result);
        } catch (final XMLStreamException e) {
            throw new RuntimeException(e);
        }
    }

    private static SchemaPath getSchemaPath(final YangInstanceIdentifier dataRoot) {
        return SchemaPath.create(
            Iterables.transform(dataRoot.getPathArguments(), YangInstanceIdentifier.PathArgument::getNodeType),dataRoot.equals(YangInstanceIdentifier.EMPTY));
    }

    @Override
    protected String getOperationName() {
        return OPERATION_NAME;
    }
}
