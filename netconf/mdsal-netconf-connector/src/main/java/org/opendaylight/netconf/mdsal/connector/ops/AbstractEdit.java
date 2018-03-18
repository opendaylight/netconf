/*
 * Copyright (c) 2018 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.mdsal.connector.ops;

import com.google.common.annotations.VisibleForTesting;
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
import org.opendaylight.netconf.api.NetconfDocumentedException;
import org.opendaylight.netconf.mdsal.connector.CurrentSchemaContext;
import org.opendaylight.netconf.util.mapping.AbstractSingletonNetconfOperation;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.codec.xml.XmlParserStream;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

abstract class AbstractEdit extends AbstractSingletonNetconfOperation {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractEdit.class);
    private static final String TARGET_KEY = "target";

    protected final CurrentSchemaContext schemaContext;

    protected AbstractEdit(final String netconfSessionIdForReporting, final CurrentSchemaContext schemaContext) {
        super(netconfSessionIdForReporting);
        this.schemaContext = schemaContext;
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    protected void parseIntoNormalizedNode(final DataSchemaNode schemaNode, final XmlElement element,
                                         final NormalizedNodeStreamWriter writer) throws DocumentedException {
        if (!(schemaNode instanceof ContainerSchemaNode) && !(schemaNode instanceof ListSchemaNode)) {
            //this should never happen since edit-config/copy-config on any other node type should not be possible nor
            // makes sense
            LOG.debug("DataNode from module is not ContainerSchemaNode nor ListSchemaNode, aborting..");
            throw new UnsupportedOperationException("implement exception if parse fails");
        }

        final XmlParserStream xmlParser = XmlParserStream.create(writer, schemaContext.getCurrentContext(), schemaNode);
        try {
            xmlParser.traverse(new DOMSource(element.getDomElement()));
        } catch (final Exception ex) {
            throw new NetconfDocumentedException("Error parsing input: " + ex.getMessage(), ex, ErrorType.PROTOCOL,
                ErrorTag.MALFORMED_MESSAGE, ErrorSeverity.ERROR);
        }
    }

    protected Optional<DataSchemaNode> getSchemaNodeFromNamespace(final String namespace, final XmlElement element)
        throws DocumentedException {
        final Iterator<Module> it;
        try {
            // returns module with newest revision since findModuleByNamespace returns a set of modules and we only
            // need the newest one
            it = schemaContext.getCurrentContext().findModules(new URI(namespace)).iterator();
        } catch (final URISyntaxException e) {
            LOG.debug("Unable to create URI for namespace : {}", namespace);
            return Optional.absent();
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
            if (schemaNode != null) {
                throw new DocumentedException(
                    "Unable to find node with namespace: " + namespace + "in module: " + module.toString(),
                    ErrorType.APPLICATION,
                    ErrorTag.UNKNOWN_NAMESPACE,
                    ErrorSeverity.ERROR);
            }
        }

        return Optional.fromJavaUtil(schemaNode);
    }

    protected static Datastore extractTargetParameter(final XmlElement operationElement, final String operationName)
        throws DocumentedException {
        final NodeList elementsByTagName = getElementsByTagName(operationElement, TARGET_KEY);
        // Direct lookup instead of using XmlElement class due to performance
        if (elementsByTagName.getLength() == 0) {
            final Map<String, String> errorInfo = ImmutableMap.of("bad-attribute", TARGET_KEY, "bad-element",
                operationName);
            throw new DocumentedException("Missing target element", ErrorType.PROTOCOL, ErrorTag.MISSING_ATTRIBUTE,
                ErrorSeverity.ERROR, errorInfo);
        } else if (elementsByTagName.getLength() > 1) {
            throw new DocumentedException("Multiple target elements", ErrorType.RPC, ErrorTag.UNKNOWN_ATTRIBUTE,
                ErrorSeverity.ERROR);
        } else {
            final XmlElement targetChildNode =
                XmlElement.fromDomElement((Element) elementsByTagName.item(0)).getOnlyChildElement();
            return Datastore.valueOf(targetChildNode.getName());
        }
    }

    @VisibleForTesting
    static NodeList getElementsByTagName(final XmlElement operationElement, final String key) throws
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

    protected static XmlElement getElement(final XmlElement parent, final String elementName)
        throws DocumentedException {
        final Optional<XmlElement> childNode = parent.getOnlyChildElementOptionally(elementName);
        if (!childNode.isPresent()) {
            throw new DocumentedException(elementName + " element is missing",
                ErrorType.PROTOCOL,
                ErrorTag.MISSING_ELEMENT,
                ErrorSeverity.ERROR);
        }

        return childNode.get();
    }
}
