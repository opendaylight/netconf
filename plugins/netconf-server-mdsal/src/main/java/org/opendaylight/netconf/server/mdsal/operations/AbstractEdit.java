/*
 * Copyright (c) 2018 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.mdsal.operations;

import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Iterator;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.dom.DOMSource;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.api.NetconfDocumentedException;
import org.opendaylight.netconf.api.xml.XmlElement;
import org.opendaylight.netconf.server.mdsal.CurrentSchemaContext;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;
import org.opendaylight.yangtools.yang.common.ErrorSeverity;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.XMLNamespace;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.codec.xml.XmlParserStream;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaTreeInference;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

abstract class AbstractEdit extends AbstractConfigOperation {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractEdit.class);
    private static final String TARGET_KEY = "target";

    final CurrentSchemaContext schemaContext;

    AbstractEdit(final SessionIdType sessionId, final CurrentSchemaContext schemaContext) {
        super(sessionId);
        this.schemaContext = schemaContext;
    }

    static final void parseIntoNormalizedNode(final SchemaTreeInference inference, final XmlElement element,
                                              final NormalizedNodeStreamWriter writer) throws DocumentedException {
        final var path = inference.statementPath();
        final var schemaNode = path.get(path.size() - 1);
        if (!(schemaNode instanceof ContainerSchemaNode) && !(schemaNode instanceof ListSchemaNode)) {
            // This should never happen since any edit operation on any other node type
            // should not be possible nor makes sense
            LOG.debug("DataNode from module is not ContainerSchemaNode nor ListSchemaNode, aborting..");
            throw new UnsupportedOperationException("implement exception if parse fails");
        }

        final XmlParserStream xmlParser = XmlParserStream.create(writer, inference);
        try {
            xmlParser.traverse(new DOMSource(element.getDomElement()));
        } catch (final XMLStreamException | URISyntaxException | IOException | SAXException ex) {
            throw new NetconfDocumentedException("Error parsing input: " + ex.getMessage(), ex,
                ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE, ErrorSeverity.ERROR);
        }
    }

    final SchemaTreeInference getSchemaNodeFromNamespace(final String namespace, final XmlElement element)
            throws DocumentedException {
        final XMLNamespace ns;
        try {
            ns = XMLNamespace.of(namespace);
        } catch (final IllegalArgumentException e) {
            throw new NetconfDocumentedException("Unable to create URI for namespace : " + namespace, e,
                ErrorType.APPLICATION, ErrorTag.INVALID_VALUE, ErrorSeverity.ERROR);
        }

        // Returns module with newest revision since findModuleByNamespace returns a set of modules and we only
        // need the newest one
        final EffectiveModelContext ctx = schemaContext.getCurrentContext();
        final Iterator<? extends @NonNull Module> it = ctx.findModules(ns).iterator();
        if (!it.hasNext()) {
            // No module is present with this namespace
            throw new NetconfDocumentedException("Unable to find module by namespace: " + namespace,
                ErrorType.APPLICATION, ErrorTag.UNKNOWN_NAMESPACE, ErrorSeverity.ERROR);
        }

        final Module module = it.next();
        final SchemaInferenceStack stack = SchemaInferenceStack.of(ctx);
        final String elementName = element.getName();
        try {
            // FIXME: This is a bit suspect. The element is formed using XML encoding, hence it corresponds to
            //        enterDataTree(). But then we use the result of this method to create a NormalizedNode tree,
            //        which contains ChoiceNode. This needs to be tested with something like to following:
            //
            //        module mod {
            //          choice foo {
            //            case bar {
            //              leaf baz {
            //                type string;
            //              }
            //            }
            //          }
            //        }
            stack.enterSchemaTree(QName.create(module.getQNameModule(), elementName));
        } catch (IllegalArgumentException e) {
            throw new DocumentedException(
                "Unable to find node " + elementName + " with namespace: " + namespace + " in module: " + module, e,
                ErrorType.APPLICATION, ErrorTag.UNKNOWN_NAMESPACE, ErrorSeverity.ERROR);
        }

        return stack.toSchemaTreeInference();
    }

    static final XmlElement extractTargetElement(final XmlElement operationElement, final String operationName)
        throws DocumentedException {
        final NodeList elementsByTagName = getElementsByTagName(operationElement, TARGET_KEY);
        // Direct lookup instead of using XmlElement class due to performance
        if (elementsByTagName.getLength() == 0) {
            throw new DocumentedException("Missing target element", ErrorType.PROTOCOL, ErrorTag.MISSING_ATTRIBUTE,
                ErrorSeverity.ERROR, ImmutableMap.of("bad-attribute", TARGET_KEY, "bad-element", operationName));
        } else if (elementsByTagName.getLength() > 1) {
            throw new DocumentedException("Multiple target elements", ErrorType.RPC, ErrorTag.UNKNOWN_ATTRIBUTE,
                ErrorSeverity.ERROR);
        } else {
            return XmlElement.fromDomElement((Element) elementsByTagName.item(0)).getOnlyChildElement();
        }
    }
}
