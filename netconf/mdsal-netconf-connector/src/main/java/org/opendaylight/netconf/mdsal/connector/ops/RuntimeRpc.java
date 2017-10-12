/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.mdsal.connector.ops;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import javax.annotation.Nullable;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import org.opendaylight.controller.config.util.xml.DocumentedException;
import org.opendaylight.controller.config.util.xml.DocumentedException.ErrorSeverity;
import org.opendaylight.controller.config.util.xml.DocumentedException.ErrorTag;
import org.opendaylight.controller.config.util.xml.DocumentedException.ErrorType;
import org.opendaylight.controller.config.util.xml.XmlElement;
import org.opendaylight.controller.config.util.xml.XmlMappingConstants;
import org.opendaylight.controller.config.util.xml.XmlUtil;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcException;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcResult;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcService;
import org.opendaylight.netconf.api.NetconfDocumentedException;
import org.opendaylight.netconf.api.xml.XmlNetconfConstants;
import org.opendaylight.netconf.mapping.api.HandlingPriority;
import org.opendaylight.netconf.mapping.api.NetconfOperationChainedExecution;
import org.opendaylight.netconf.mdsal.connector.CurrentSchemaContext;
import org.opendaylight.netconf.util.mapping.AbstractSingletonNetconfOperation;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.codec.xml.XMLStreamNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.codec.xml.XmlParserStream;
import org.opendaylight.yangtools.yang.data.impl.schema.ImmutableNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.impl.schema.NormalizedNodeResult;
import org.opendaylight.yangtools.yang.data.impl.schema.SchemaOrderedNormalizedNodeWriter;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class RuntimeRpc extends AbstractSingletonNetconfOperation {

    private static final Logger LOG = LoggerFactory.getLogger(RuntimeRpc.class);

    private static final XMLOutputFactory XML_OUTPUT_FACTORY;

    static {
        XML_OUTPUT_FACTORY = XMLOutputFactory.newFactory();
        XML_OUTPUT_FACTORY.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES, Boolean.TRUE);
    }

    private final CurrentSchemaContext schemaContext;
    private final DOMRpcService rpcService;

    public RuntimeRpc(final String netconfSessionIdForReporting, final CurrentSchemaContext schemaContext,
                      final DOMRpcService rpcService) {
        super(netconfSessionIdForReporting);
        this.schemaContext = schemaContext;
        this.rpcService = rpcService;
    }

    @Override
    protected HandlingPriority canHandle(final String netconfOperationName, final String namespace) {
        final URI namespaceURI = createNsUri(namespace);
        final Optional<Module> module = getModule(namespaceURI);

        if (!module.isPresent()) {
            LOG.debug("Cannot handle rpc: {}, {}", netconfOperationName, namespace);
            return HandlingPriority.CANNOT_HANDLE;
        }

        getRpcDefinitionFromModule(module.get(), namespaceURI, netconfOperationName);
        return HandlingPriority.HANDLE_WITH_DEFAULT_PRIORITY;

    }

    @Override
    protected String getOperationName() {
        throw new UnsupportedOperationException("Runtime rpc does not have a stable name");
    }

    private static URI createNsUri(final String namespace) {
        // May throw IllegalArgumentException, but that should never happen, as the namespace comes from parsed XML
        return URI.create(namespace);
    }

    //this returns module with the newest revision if more then 1 module with same namespace is found
    private Optional<Module> getModule(final URI namespaceURI) {
        return Optional.fromJavaUtil(schemaContext.getCurrentContext().findModule(namespaceURI));
    }

    private static Optional<RpcDefinition> getRpcDefinitionFromModule(final Module module, final URI namespaceURI,
            final String name) {
        for (final RpcDefinition rpcDef : module.getRpcs()) {
            if (rpcDef.getQName().getNamespace().equals(namespaceURI)
                    && rpcDef.getQName().getLocalName().equals(name)) {
                return Optional.of(rpcDef);
            }
        }
        return Optional.absent();
    }

    @Override
    protected Element handleWithNoSubsequentOperations(final Document document, final XmlElement operationElement)
            throws DocumentedException {

        final String netconfOperationName = operationElement.getName();
        final String netconfOperationNamespace;
        try {
            netconfOperationNamespace = operationElement.getNamespace();
        } catch (final DocumentedException e) {
            LOG.debug("Cannot retrieve netconf operation namespace from message due to ", e);
            throw new DocumentedException("Cannot retrieve netconf operation namespace from message",
                    ErrorType.PROTOCOL, ErrorTag.UNKNOWN_NAMESPACE, ErrorSeverity.ERROR);
        }

        final URI namespaceURI = createNsUri(netconfOperationNamespace);
        final Optional<Module> moduleOptional = getModule(namespaceURI);

        if (!moduleOptional.isPresent()) {
            throw new DocumentedException("Unable to find module in Schema Context with namespace and name : "
                        + namespaceURI + " " + netconfOperationName + schemaContext.getCurrentContext(),
                    ErrorType.APPLICATION, ErrorTag.BAD_ELEMENT, ErrorSeverity.ERROR);
        }

        final Optional<RpcDefinition> rpcDefinitionOptional = getRpcDefinitionFromModule(moduleOptional.get(),
                namespaceURI, netconfOperationName);

        if (!rpcDefinitionOptional.isPresent()) {
            throw new DocumentedException(
                    "Unable to find RpcDefinition with namespace and name : "
                        + namespaceURI + " " + netconfOperationName,
                    ErrorType.APPLICATION, ErrorTag.BAD_ELEMENT, ErrorSeverity.ERROR);
        }

        final RpcDefinition rpcDefinition = rpcDefinitionOptional.get();
        final SchemaPath schemaPath = SchemaPath.create(Collections.singletonList(rpcDefinition.getQName()), true);
        final NormalizedNode<?, ?> inputNode = rpcToNNode(operationElement, rpcDefinition.getInput());

        final CheckedFuture<DOMRpcResult, DOMRpcException> rpcFuture = rpcService.invokeRpc(schemaPath, inputNode);
        try {
            final DOMRpcResult result = rpcFuture.checkedGet();
            if (result.getResult() == null) {
                return XmlUtil.createElement(document, XmlNetconfConstants.OK,
                        Optional.of(XmlNetconfConstants.URN_IETF_PARAMS_XML_NS_NETCONF_BASE_1_0));
            }
            return (Element) transformNormalizedNode(document, result.getResult(), rpcDefinition.getOutput().getPath());
        } catch (final DOMRpcException e) {
            throw DocumentedException.wrap(e);
        }
    }

    @Override
    public Document handle(final Document requestMessage,
                           final NetconfOperationChainedExecution subsequentOperation) throws DocumentedException {

        final XmlElement requestElement = getRequestElementWithCheck(requestMessage);

        final Document document = XmlUtil.newDocument();

        final XmlElement operationElement = requestElement.getOnlyChildElement();
        final Map<String, Attr> attributes = requestElement.getAttributes();

        final Element response = handle(document, operationElement, subsequentOperation);
        final Element rpcReply = XmlUtil.createElement(document, XmlMappingConstants.RPC_REPLY_KEY,
                Optional.of(XmlNetconfConstants.URN_IETF_PARAMS_XML_NS_NETCONF_BASE_1_0));

        if (XmlElement.fromDomElement(response).hasNamespace()) {
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

    private Node transformNormalizedNode(final Document document, final NormalizedNode<?, ?> data,
                                         final SchemaPath rpcOutputPath) {
        final DOMResult result = new DOMResult(document.createElement(XmlMappingConstants.RPC_REPLY_KEY));

        final XMLStreamWriter xmlWriter = getXmlStreamWriter(result);

        final NormalizedNodeStreamWriter nnStreamWriter = XMLStreamNormalizedNodeStreamWriter.create(xmlWriter,
                schemaContext.getCurrentContext(), rpcOutputPath);

        final SchemaOrderedNormalizedNodeWriter nnWriter =
                new SchemaOrderedNormalizedNodeWriter(nnStreamWriter, schemaContext.getCurrentContext(), rpcOutputPath);

        writeRootElement(xmlWriter, nnWriter, (ContainerNode) data);
        try {
            nnStreamWriter.close();
            xmlWriter.close();
        } catch (IOException | XMLStreamException e) {
            LOG.warn("Error while closing streams", e);
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

    private static void writeRootElement(final XMLStreamWriter xmlWriter,
            final SchemaOrderedNormalizedNodeWriter nnWriter, final ContainerNode data) {
        try {
            final Collection<DataContainerChild<?, ?>> value = data.getValue();
            nnWriter.write(value);
            nnWriter.flush();
            xmlWriter.flush();
        } catch (XMLStreamException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Parses xml element rpc input into normalized node or null if rpc does not take any input.
     *
     * @param element rpc xml element
     * @param input   input container schema node, or null if rpc does not take any input
     * @return parsed rpc into normalized node, or null if input schema is null
     */
    @SuppressWarnings("checkstyle:IllegalCatch")
    @Nullable
    private NormalizedNode<?, ?> rpcToNNode(final XmlElement element, @Nullable final ContainerSchemaNode input)
            throws DocumentedException {
        if (input.getChildNodes().isEmpty()) {
            return null;
        }

        final NormalizedNodeResult resultHolder = new NormalizedNodeResult();
        final NormalizedNodeStreamWriter writer = ImmutableNormalizedNodeStreamWriter.from(resultHolder);
        final XmlParserStream xmlParser = XmlParserStream.create(writer, schemaContext.getCurrentContext(), input);

        try {
            xmlParser.traverse(new DOMSource(element.getDomElement()));
        } catch (final Exception ex) {
            throw new NetconfDocumentedException("Error parsing input: " + ex.getMessage(), ex, ErrorType.PROTOCOL,
                    ErrorTag.MALFORMED_MESSAGE, ErrorSeverity.ERROR);
        }

        return resultHolder.getResult();
    }

}
