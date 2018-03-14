/*
 * Copyright (c) 2018 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.mdsal.connector.ops;

import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.Map;
import javax.xml.transform.dom.DOMSource;
import org.opendaylight.controller.config.util.xml.DocumentedException;
import org.opendaylight.controller.config.util.xml.DocumentedException.ErrorSeverity;
import org.opendaylight.controller.config.util.xml.DocumentedException.ErrorTag;
import org.opendaylight.controller.config.util.xml.DocumentedException.ErrorType;
import org.opendaylight.controller.config.util.xml.XmlElement;
import org.opendaylight.controller.config.util.xml.XmlUtil;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadWriteTransaction;
import org.opendaylight.netconf.api.NetconfDocumentedException;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.mdsal.connector.CurrentSchemaContext;
import org.opendaylight.netconf.mdsal.connector.TransactionProvider;
import org.opendaylight.netconf.util.mapping.AbstractSingletonNetconfOperation;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.codec.xml.XmlParserStream;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.NormalizedNodeResult;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class CopyConfig extends AbstractSingletonNetconfOperation {
    private static final Logger LOG = LoggerFactory.getLogger(CopyConfig.class);

    private static final String OPERATION_NAME = "copy-config";
    private static final String CONFIG_KEY = "config";
    private static final String TARGET_KEY = "target";
    private static final String SOURCE_KEY = "source";

    // Top-level "data" node without child nodes
    private static final ContainerNode EMPTY_ROOT_NODE = Builders.containerBuilder()
        .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(SchemaContext.NAME)).build();

    private final CurrentSchemaContext schemaContext;
    private final TransactionProvider transactionProvider;

    public CopyConfig(final String netconfSessionIdForReporting, final CurrentSchemaContext schemaContext,
                      final TransactionProvider transactionProvider) {
        super(netconfSessionIdForReporting);
        this.schemaContext = schemaContext;
        this.transactionProvider = transactionProvider;
    }

    @Override
    protected Element handleWithNoSubsequentOperations(final Document document, final XmlElement operationElement)
            throws DocumentedException {
        final Datastore targetDatastore = extractTargetParameter(operationElement);
        if (targetDatastore == Datastore.running) {
            throw new DocumentedException("edit-config on running datastore is not supported",
                    ErrorType.PROTOCOL,
                    ErrorTag.OPERATION_NOT_SUPPORTED,
                    ErrorSeverity.ERROR);
        }
        final XmlElement configElement = extractConfigParameter(operationElement);

        // <copy-config>, unlike <edit-config>, always replaces entire configuration,
        // so remove old configuration first:
        final DOMDataReadWriteTransaction rwTx = transactionProvider.getOrCreateTransaction();
        rwTx.put(LogicalDatastoreType.CONFIGURATION, YangInstanceIdentifier.EMPTY, EMPTY_ROOT_NODE);

        // Then create nodes present in the <config> element:
        for (final XmlElement element : configElement.getChildElements()) {
            final String ns = element.getNamespace();
            final DataSchemaNode schemaNode = getSchemaNodeFromNamespace(ns, element);
            final NormalizedNode<?, ?> data = parseIntoNormalizedNode(schemaNode, element);
            final YangInstanceIdentifier path = YangInstanceIdentifier.create(data.getIdentifier());
            // Doing merge instead of put to support top-level list:
            rwTx.merge(LogicalDatastoreType.CONFIGURATION, path, data);
        }
        return XmlUtil.createElement(document, XmlNetconfConstants.OK, Optional.absent());
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    private NormalizedNode<?, ?> parseIntoNormalizedNode(final DataSchemaNode schemaNode, final XmlElement element)
        throws DocumentedException {
        if (!(schemaNode instanceof ContainerSchemaNode) && !(schemaNode instanceof ListSchemaNode)) {
            // this should never happen since copy-config on any other node type should not be possible nor makes sense
            LOG.debug("DataNode from module is not ContainerSchemaNode nor ListSchemaNode, aborting..");
            throw new UnsupportedOperationException("implement exception if parse fails");
        }

        final NormalizedNodeResult resultHolder = new NormalizedNodeResult();
        final NormalizedNodeStreamWriter writer = ImmutableNormalizedNodeStreamWriter.from(resultHolder);
        final XmlParserStream xmlParser = XmlParserStream.create(writer, schemaContext.getCurrentContext(), schemaNode);
        try {
            xmlParser.traverse(new DOMSource(element.getDomElement()));
        } catch (final Exception ex) {
            throw new NetconfDocumentedException("Error parsing input: " + ex.getMessage(), ex, ErrorType.PROTOCOL,
                    ErrorTag.MALFORMED_MESSAGE, ErrorSeverity.ERROR);
        }

        return resultHolder.getResult();
    }

    private DataSchemaNode getSchemaNodeFromNamespace(final String namespace,
                                                      final XmlElement element) throws DocumentedException {
        final Iterator<Module> it;
        try {
            // returns module with newest revision since findModuleByNamespace returns a set of modules and we only
            // need the newest one
            it = schemaContext.getCurrentContext().findModules(new URI(namespace)).iterator();
        } catch (final URISyntaxException e) {
            throw new NetconfDocumentedException("Unable to create URI for namespace : " + namespace, e,
                ErrorType.APPLICATION, ErrorTag.INVALID_VALUE, ErrorSeverity.ERROR);
        }

        if (!it.hasNext()) {
            // no module is present with this namespace
            throw new NetconfDocumentedException("Unable to find module by namespace: " + namespace,
                ErrorType.APPLICATION, ErrorTag.UNKNOWN_NAMESPACE, ErrorSeverity.ERROR);
        }

        final Module module = it.next();
        final java.util.Optional<DataSchemaNode> schemaNode =
            module.findDataChildByName(QName.create(module.getQNameModule(), element.getName()));
        if (!schemaNode.isPresent()) {
            throw new DocumentedException(
                "Unable to find node with namespace: " + namespace + "in module: " + module.toString(),
                ErrorType.APPLICATION,
                ErrorTag.UNKNOWN_NAMESPACE,
                ErrorSeverity.ERROR);
        }

        return schemaNode.get();
    }

    private static Datastore extractTargetParameter(final XmlElement operationElement) throws DocumentedException {
        final NodeList elementsByTagName = getElementsByTagName(operationElement, TARGET_KEY);
        // Direct lookup instead of using XmlElement class due to performance
        if (elementsByTagName.getLength() == 0) {
            final Map<String, String> errorInfo = ImmutableMap.of("bad-attribute", TARGET_KEY, "bad-element",
                OPERATION_NAME);
            throw new DocumentedException("Missing target element", ErrorType.PROTOCOL, ErrorTag.MISSING_ATTRIBUTE,
                ErrorSeverity.ERROR, errorInfo);
        } else if (elementsByTagName.getLength() > 1) {
            throw new DocumentedException("Multiple target elements", ErrorType.PROTOCOL, ErrorTag.UNKNOWN_ELEMENT,
                ErrorSeverity.ERROR);
        } else {
            final XmlElement targetChildNode =
                    XmlElement.fromDomElement((Element) elementsByTagName.item(0)).getOnlyChildElement();
            return Datastore.valueOf(targetChildNode.getName());
        }
    }

    private static XmlElement extractConfigParameter(final XmlElement operationElement) throws DocumentedException {
        final Optional<XmlElement> sourceNode = operationElement.getOnlyChildElementOptionally(SOURCE_KEY);
        if (!sourceNode.isPresent()) {
            throw new DocumentedException(SOURCE_KEY + " element is missing",
                ErrorType.PROTOCOL,
                ErrorTag.MISSING_ELEMENT,
                ErrorSeverity.ERROR);
        }
        final Optional<XmlElement> configNode =
            sourceNode.get().getOnlyChildElementOptionally(CONFIG_KEY);
        if (!configNode.isPresent()) {
            throw new DocumentedException(CONFIG_KEY + " element is missing",
                ErrorType.PROTOCOL,
                ErrorTag.MISSING_ELEMENT,
                ErrorSeverity.ERROR);
        }
        return configNode.get();
    }

    private static NodeList getElementsByTagName(final XmlElement operationElement, final String key) throws
        DocumentedException {
        final Element element = operationElement.getDomElement();
        final NodeList elementsByTagName;

        if (Strings.isNullOrEmpty(element.getPrefix())) {
            elementsByTagName = element.getElementsByTagName(key);
        } else {
            elementsByTagName = element.getElementsByTagNameNS(operationElement.getNamespace(), key);
        }

        return elementsByTagName;
    }

    @Override
    protected String getOperationName() {
        return OPERATION_NAME;
    }

}
