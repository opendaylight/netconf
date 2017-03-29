/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.communicator.impl.xml.draft04;

import com.google.common.base.Preconditions;
import java.io.IOException;
import java.io.StringWriter;
import javax.xml.XMLConstants;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.restconfsb.communicator.api.http.Request;
import org.opendaylight.restconfsb.communicator.api.renderer.Renderer;
import org.opendaylight.restconfsb.communicator.impl.common.YangInstanceIdentifierToUrlCodec;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.api.schema.stream.NormalizedNodeWriter;
import org.opendaylight.yangtools.yang.data.impl.codec.xml.XMLStreamNormalizedNodeStreamWriter;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextNode;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextTree;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;

/**
 * Renders data in binding independent form to xml.
 */
public class XmlRenderer implements Renderer {

    public static final XMLOutputFactory XML_FACTORY;

    static {
        XML_FACTORY = XMLOutputFactory.newFactory();
        XML_FACTORY.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES, true);
    }

    private final SchemaContext schemaContext;
    private final DataSchemaContextTree dataSchemaContextTree;
    private final YangInstanceIdentifierToUrlCodec toUrlCodec;

    public XmlRenderer(final SchemaContext schemaContext) {
        this.schemaContext = schemaContext;
        this.dataSchemaContextTree = DataSchemaContextTree.from(schemaContext);
        toUrlCodec = new YangInstanceIdentifierToUrlCodec(schemaContext);
    }

    @Override
    public Request renderEditConfig(final YangInstanceIdentifier path, final NormalizedNode<?, ?> body) {
        final SchemaPath schemaPath = getParentSchemaPath(path);
        try {
            final String finalPath = createConfigPath(renderDataPath(path));

            final String stringBody = writeNormalizedNode(body, schemaPath);
            return Request.createRequestWithBody(finalPath, stringBody, Request.RestconfMediaType.XML_DATA);
        } catch (XMLStreamException | IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public Request renderOperation(final SchemaPath type, final ContainerNode input) {
        final Module module = findNodeModule(type.getLastComponent());
        final String urlPath = "/operations/" + module.getName() + ":" + type.getLastComponent().getLocalName();
        final RpcDefinition rpcDefinition = findRpcDefinition(module, type);
        try {
            final String body;
            final ContainerSchemaNode inputDefinition = rpcDefinition.getInput();
            if (inputDefinition != null) {
                Preconditions.checkNotNull(input);
                body = writeNormalizedNodeRpc(input, inputDefinition.getPath());
            } else {
                body = "";
            }
            return Request.createRequestWithBody(urlPath, body, Request.RestconfMediaType.XML_OPERATION);
        } catch (XMLStreamException | IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public Request renderGetData(final YangInstanceIdentifier path, final LogicalDatastoreType type) {
        final String configPath;
        if (type == LogicalDatastoreType.OPERATIONAL) {
            configPath = createOperationalPath(renderDataPath(path));
        } else {
            configPath = createConfigPath(renderDataPath(path));
        }

        return Request.createRequestWithoutBody(configPath, Request.RestconfMediaType.XML_DATA);
    }

    @Override
    public Request renderDeleteConfig(final YangInstanceIdentifier path) {
        final String configPath = createConfigPath(renderDataPath(path));

        return Request.createRequestWithoutBody(configPath, Request.RestconfMediaType.XML_DATA);
    }

    private String renderDataPath(final YangInstanceIdentifier path) {
        if (path.isEmpty()) {
            return "";
        }
        return toUrlCodec.serialize(path);
    }

    private String createConfigPath(final String resource) {
        return "/data" + resource + "?content=config";
    }

    private String createOperationalPath(final String resource) {
        return "/data" + resource + "?content=nonconfig";
    }

    private RpcDefinition findRpcDefinition(final Module module, final SchemaPath type) {
        final QName rpcQName = type.getLastComponent();
        for (final RpcDefinition rpcDefinition : module.getRpcs()) {
            if (rpcQName.equals(rpcDefinition.getQName())) {
                return rpcDefinition;
            }
        }
        throw new IllegalStateException("Rpc " + type + " definition not found");
    }

    private String writeNormalizedNodeRpc(final ContainerNode body, final SchemaPath schemaPath) throws XMLStreamException, IOException {
        return writeViaNormalizedNodeWriter(schemaPath, new NormalizedNodeWriterUser() {
            @Override
            public void use(final XMLStreamWriter xmlWriter, final NormalizedNodeWriter normalizedNodeWriter) {
                try {
                    xmlWriter.writeStartElement(XMLConstants.DEFAULT_NS_PREFIX, "input", schemaPath.getLastComponent().getNamespace().toString());
                    for (final DataContainerChild child : body.getValue()) {
                        normalizedNodeWriter.write(child);
                    }
                    xmlWriter.writeEndElement();
                } catch (XMLStreamException | IOException e) {
                    throw new IllegalStateException(e);
                }
            }
        });
    }

    private String writeNormalizedNode(final NormalizedNode<?, ?> body, final SchemaPath schemaPath) throws XMLStreamException, IOException {
        return writeViaNormalizedNodeWriter(schemaPath, new NormalizedNodeWriterUser() {
            @Override
            public void use(final XMLStreamWriter xmlWriter, final NormalizedNodeWriter normalizedNodeWriter) {
                try {
                    normalizedNodeWriter.write(body);
                } catch (final IOException e) {
                    throw new IllegalStateException(e);
                }
            }
        });
    }

    private String writeViaNormalizedNodeWriter(final SchemaPath schemaPath, final NormalizedNodeWriterUser user) throws IOException, XMLStreamException {
        final StringWriter result = new StringWriter();
        final XMLStreamWriter xmlWriter = XML_FACTORY.createXMLStreamWriter(result);
        try (final NormalizedNodeStreamWriter normalizedNodeStreamWriter = XMLStreamNormalizedNodeStreamWriter.create(xmlWriter, schemaContext, schemaPath);
             final NormalizedNodeWriter normalizedNodeWriter = NormalizedNodeWriter.forStreamWriter(normalizedNodeStreamWriter)) {
            user.use(xmlWriter, normalizedNodeWriter);
            normalizedNodeWriter.flush();
        } finally {
            if (xmlWriter != null) {
                xmlWriter.close();
            }
        }
        return result.toString();
    }

    private SchemaPath getParentSchemaPath(final YangInstanceIdentifier path) {
        if (path.isEmpty()) {
            return SchemaPath.ROOT;
        }
        final DataSchemaContextNode<?> child = dataSchemaContextTree.getChild(path);
        final DataSchemaNode dataSchemaNode = child.getDataSchemaNode();
        if (dataSchemaNode == null) {
            throw new IllegalStateException("Data schema node for " + path + " not found.");
        }
        if (child.isKeyedEntry()) {
            return child.getDataSchemaNode().getPath();
        }
        return dataSchemaNode.getPath().getParent();
    }

    private Module findNodeModule(final QName nodeType) {
        final Module module = schemaContext.findModuleByNamespaceAndRevision(nodeType.getNamespace(), nodeType.getRevision());
        if (module == null) {
            throw new IllegalStateException("Module with namespace " + nodeType.getNamespace() + " and revision " + nodeType.getFormattedRevision() + " not found");
        }
        return module;
    }

    private interface NormalizedNodeWriterUser {
        void use(XMLStreamWriter xmlWriter, NormalizedNodeWriter normalizedNodeWriter);
    }

}