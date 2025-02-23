/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.mdsal.operations;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.dom.api.DOMRpcResult;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.api.NamespaceURN;
import org.opendaylight.netconf.api.NetconfDocumentedException;
import org.opendaylight.netconf.api.messages.RpcReplyMessage;
import org.opendaylight.netconf.api.xml.XmlElement;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.databind.DatabindContext;
import org.opendaylight.netconf.databind.DatabindProvider;
import org.opendaylight.netconf.server.api.operations.AbstractSingletonNetconfOperation;
import org.opendaylight.netconf.server.api.operations.HandlingPriority;
import org.opendaylight.netconf.server.api.operations.NetconfOperationChainedExecution;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;
import org.opendaylight.yangtools.yang.common.ErrorSeverity;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.XMLNamespace;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.codec.xml.XMLStreamNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.codec.xml.XmlParserStream;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.NormalizationResultHolder;
import org.opendaylight.yangtools.yang.data.impl.schema.SchemaOrderedNormalizedNodeWriter;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.api.stmt.SchemaNodeIdentifier.Absolute;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public final class RuntimeRpc extends AbstractSingletonNetconfOperation {
    private static final Logger LOG = LoggerFactory.getLogger(RuntimeRpc.class);
    private static final XMLOutputFactory XML_OUTPUT_FACTORY;

    static {
        XML_OUTPUT_FACTORY = XMLOutputFactory.newFactory();
        XML_OUTPUT_FACTORY.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES, Boolean.TRUE);
    }

    private final DatabindProvider databindProvider;
    private final DOMRpcService rpcService;

    public RuntimeRpc(final SessionIdType sessionId, final DatabindProvider databindProvider,
            final DOMRpcService rpcService) {
        super(sessionId);
        this.databindProvider = requireNonNull(databindProvider);
        this.rpcService = rpcService;
    }

    @Override
    protected HandlingPriority canHandle(final String netconfOperationName, final String namespace) {
        final var xmlNamespace = XMLNamespace.of(namespace);
        final var rpcDef = getModule(databindProvider.currentDatabind(), xmlNamespace)
            .flatMap(module -> getRpcDefinitionFromModule(module, xmlNamespace, netconfOperationName));
        if (rpcDef.isEmpty()) {
            LOG.debug("Cannot handle rpc: {}, {}", netconfOperationName, namespace);
            return null;
        }
        return HandlingPriority.HANDLE_WITH_DEFAULT_PRIORITY;
    }

    @Override
    protected String getOperationName() {
        throw new UnsupportedOperationException("Runtime rpc does not have a stable name");
    }

    // this returns module with the newest revision if more then 1 module with same namespace is found
    private static Optional<? extends Module> getModule(final DatabindContext databind, final XMLNamespace namespace) {
        return databind.modelContext().findModules(namespace).stream().findFirst();
    }

    private static Optional<RpcDefinition> getRpcDefinitionFromModule(final Module module, final XMLNamespace namespace,
            final String name) {
        for (var rpcDef : module.getRpcs()) {
            final var qname = rpcDef.getQName();
            if (qname.getNamespace().equals(namespace) && qname.getLocalName().equals(name)) {
                return Optional.of(rpcDef);
            }
        }
        return Optional.empty();
    }

    @Override
    protected Element handleWithNoSubsequentOperations(final Document document, final XmlElement operationElement)
            throws DocumentedException {
        final var netconfOperationName = operationElement.getName();
        final String netconfOperationNamespace;
        try {
            netconfOperationNamespace = operationElement.getNamespace();
        } catch (final DocumentedException e) {
            LOG.debug("Cannot retrieve netconf operation namespace from message due to ", e);
            throw new DocumentedException("Cannot retrieve netconf operation namespace from message", e,
                    ErrorType.PROTOCOL, ErrorTag.UNKNOWN_NAMESPACE, ErrorSeverity.ERROR);
        }

        final var namespaceURI = XMLNamespace.of(netconfOperationNamespace);
        final var databind = databindProvider.currentDatabind();
        final var moduleOptional = getModule(databind, namespaceURI);

        if (moduleOptional.isEmpty()) {
            throw new DocumentedException("Unable to find module in Schema Context with namespace and name : "
                        + namespaceURI + " " + netconfOperationName,
                    ErrorType.APPLICATION, ErrorTag.BAD_ELEMENT, ErrorSeverity.ERROR);
        }

        final var rpcDefinitionOptional = getRpcDefinitionFromModule(moduleOptional.orElseThrow(), namespaceURI,
            netconfOperationName);

        if (rpcDefinitionOptional.isEmpty()) {
            throw new DocumentedException(
                    "Unable to find RpcDefinition with namespace and name : "
                        + namespaceURI + " " + netconfOperationName,
                    ErrorType.APPLICATION, ErrorTag.BAD_ELEMENT, ErrorSeverity.ERROR);
        }

        final var rpcDefinition = rpcDefinitionOptional.orElseThrow();
        final var inputNode = rpcToNNode(databind, operationElement, rpcDefinition);

        final DOMRpcResult result;
        try {
            result = rpcService.invokeRpc(rpcDefinition.getQName(), inputNode).get();
        } catch (final InterruptedException | ExecutionException e) {
            throw DocumentedException.wrap(e);
        }
        if (result.value() == null) {
            return document.createElementNS(NamespaceURN.BASE, XmlNetconfConstants.OK);
        }
        return transformNormalizedNode(databind, document, result.value(),
                Absolute.of(rpcDefinition.getQName(), rpcDefinition.getOutput().getQName()));
    }

    @Override
    public Document handle(final Document requestMessage, final NetconfOperationChainedExecution subsequentOperation)
            throws DocumentedException {
        final XmlElement requestElement = getRequestElementWithCheck(requestMessage);
        final Document document = XmlUtil.newDocument();
        final XmlElement operationElement = requestElement.getOnlyChildElement();
        final Map<String, Attr> attributes = requestElement.getAttributes();

        final Element response = handle(document, operationElement, subsequentOperation);
        final Element rpcReply = document.createElementNS(NamespaceURN.BASE, RpcReplyMessage.ELEMENT_NAME);

        if (XmlUtil.hasNamespace(response)) {
            rpcReply.appendChild(response);
        } else {
            final NodeList list = response.getChildNodes();
            if (list.getLength() == 0) {
                rpcReply.appendChild(response);
            } else {
                while (list.getLength() != 0) {
                    rpcReply.appendChild(list.item(0));
                }
            }
        }

        for (final Attr attribute : attributes.values()) {
            rpcReply.setAttributeNode((Attr) document.importNode(attribute, true));
        }
        document.appendChild(rpcReply);
        return document;
    }

    private static Element transformNormalizedNode(final DatabindContext databind, final Document document,
            final ContainerNode data, final Absolute rpcOutputPath) {
        final var result = new DOMResult(document.createElement(RpcReplyMessage.ELEMENT_NAME));
        final var xmlWriter = getXmlStreamWriter(result);
        final var modelContext = databind.modelContext();
        final var nnStreamWriter = XMLStreamNormalizedNodeStreamWriter.create(xmlWriter, modelContext, rpcOutputPath);

        final var nnWriter = new SchemaOrderedNormalizedNodeWriter(nnStreamWriter, modelContext, rpcOutputPath);

        writeRootElement(xmlWriter, nnWriter, data);
        try {
            nnStreamWriter.close();
            xmlWriter.close();
        } catch (IOException | XMLStreamException e) {
            // FIXME: throw DocumentedException
            LOG.warn("Error while closing streams", e);
        }

        return (Element) result.getNode();
    }

    private static XMLStreamWriter getXmlStreamWriter(final DOMResult result) {
        try {
            return XML_OUTPUT_FACTORY.createXMLStreamWriter(result);
        } catch (final XMLStreamException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void writeRootElement(final XMLStreamWriter xmlWriter,
            final SchemaOrderedNormalizedNodeWriter nnWriter, final ContainerNode data) {
        try {
            nnWriter.write(data.body());
            nnWriter.flush();
            xmlWriter.flush();
        } catch (XMLStreamException | IOException e) {
            // FIXME: throw DocumentedException
            throw new IllegalStateException(e);
        }
    }

    /**
     * Parses xml element rpc input into normalized node or null if rpc does not take any input.
     *
     * @param element rpc xml element
     * @param rpcDefinition   input container schema node, or null if rpc does not take any input
     * @return parsed rpc into normalized node, or null if input schema is null
     */
    private static @Nullable ContainerNode rpcToNNode(final DatabindContext databind, final XmlElement element,
            final RpcDefinition rpcDefinition) throws DocumentedException {
        final var resultHolder = new NormalizationResultHolder();
        final var writer = ImmutableNormalizedNodeStreamWriter.from(resultHolder);
        final var xmlParser = XmlParserStream.create(writer, SchemaInferenceStack.of(databind.modelContext(),
            // FIXME: of data tree path/
            Absolute.of(rpcDefinition.getQName(), rpcDefinition.getInput().getQName())).toInference());

        try {
            xmlParser.traverse(new DOMSource(element.getDomElement()));
        } catch (final XMLStreamException | IOException ex) {
            throw new NetconfDocumentedException("Error parsing input: " + ex.getMessage(), ex,
                ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE, ErrorSeverity.ERROR);
        }

        return (ContainerNode) resultHolder.getResult().data();
    }
}
