/*
 * Copyright (c) 2018 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.mdsal.connector.ops;

import com.google.common.base.Optional;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.List;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
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
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.NormalizedNodeResult;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public final class CopyConfig extends AbstractEdit {
    private static final String OPERATION_NAME = "copy-config";
    private static final String CONFIG_KEY = "config";
    private static final String SOURCE_KEY = "source";
    private static final String URL_KEY = "url";

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
        final Datastore targetDatastore = extractTargetParameter(operationElement, OPERATION_NAME);
        if (targetDatastore == Datastore.running) {
            throw new DocumentedException("edit-config on running datastore is not supported",
                    ErrorType.PROTOCOL,
                    ErrorTag.OPERATION_NOT_SUPPORTED,
                    ErrorSeverity.ERROR);
        }

        final List<XmlElement> configElements = getConfigElements(operationElement);

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
        return XmlUtil.createElement(document, XmlNetconfConstants.OK, Optional.absent());
    }


    // TODO move to some utility
    private List<XmlElement> getConfigElements(final XmlElement operationElement) throws DocumentedException {
        final XmlElement source = getElement(operationElement, SOURCE_KEY);
        final Optional<XmlElement> configElement = source.getOnlyChildElementOptionally(CONFIG_KEY);
        List<XmlElement> configElements = null;
        if (configElement.isPresent()) {
            configElements = configElement.get().getChildElements();
        } else {
            final XmlElement urlElement = getElement(source, URL_KEY);
            final String urlString = urlElement.getTextContent();
            if (urlString.startsWith("file:")) {
                try {
                    URI uri = new URI(urlString);
                    File file = new File(uri);
                    String content = new String (Files.readAllBytes(file.toPath()), Charset.forName("UTF-8"));
                    content.toString();
                    // TODO parse content to XML
                } catch (URISyntaxException e) {
                    throw new DocumentedException(urlString + " is not valid URI",
                        ErrorType.APPLICATION,
                        ErrorTag.INVALID_VALUE,
                        ErrorSeverity.ERROR);
                } catch (Exception e) {
                    throw new DocumentedException("Could not open URI:" + urlString,
                        ErrorType.APPLICATION,
                        ErrorTag.INVALID_VALUE,
                        ErrorSeverity.ERROR);
                }
            } else {
                throw new IllegalArgumentException("URL unsupported");
            }

        }
        return configElements;
    }

    private static XmlElement extractURLParameter(final XmlElement operationElement) throws DocumentedException {
        final XmlElement source = getElement(operationElement, SOURCE_KEY);
        return getElement(source, URL_KEY);
    }

    @Override
    protected String getOperationName() {
        return OPERATION_NAME;
    }
}
