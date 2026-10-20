/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 * Copyright (c) 2021 PANTHEON.tech, s.r.o.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.codegen.wadl;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.yangtools.plugin.generator.api.GeneratedFile;
import org.opendaylight.yangtools.plugin.generator.api.GeneratedFileLifecycle;
import org.opendaylight.yangtools.util.xml.IndentedXML;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.XMLNamespace;
import org.opendaylight.yangtools.yang.common.YangConstants;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class WadlGeneratedFile implements GeneratedFile {
    private static final Logger LOG = LoggerFactory.getLogger(WadlGeneratedFile.class);
    private static final IndentedXML INDENTED_XML = IndentedXML.of(4);
    private static final XMLOutputFactory XOF;

    static {
        final var factory = XMLOutputFactory.newFactory();
        factory.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES, false);
        XOF = factory;
    }

    private final List<DataSchemaNode> operationalData;
    private final List<DataSchemaNode> configData;
    private final EffectiveModelContext context;
    private final Module module;

    private List<LeafSchemaNode> pathListParams;

    @NonNullByDefault
    private WadlGeneratedFile(final EffectiveModelContext context, final Module module,
            final List<DataSchemaNode> configData, final List<DataSchemaNode> operationalData) {
        this.context = requireNonNull(context);
        this.module = requireNonNull(module);
        this.configData = List.copyOf(configData);
        this.operationalData = List.copyOf(operationalData);

    }

    static @Nullable WadlGeneratedFile of(final @NonNull EffectiveModelContext context, final @NonNull Module module) {
        final var operationalData = new ArrayList<DataSchemaNode>();
        final var configData = new ArrayList<DataSchemaNode>();
        for (var child : module.getChildNodes()) {
            if (child instanceof ContainerSchemaNode || child instanceof ListSchemaNode) {
                if (child.isConfiguration()) {
                    configData.add(child);
                } else {
                    operationalData.add(child);
                }
            }
        }

        return module.getRpcs().isEmpty() && configData.isEmpty() && operationalData.isEmpty() ? null
            : new WadlGeneratedFile(context, module, configData, operationalData);
    }

    @Override
    public GeneratedFileLifecycle getLifecycle() {
        return GeneratedFileLifecycle.TRANSIENT;
    }

    @Override
    public void writeBody(final OutputStream output) throws IOException {
        final XMLStreamWriter ww;
        try {
            ww = INDENTED_XML.wrapStreamWriter(XOF.createXMLStreamWriter(output));
        } catch (XMLStreamException e) {
            throw new IOException("Failed to create writer", e);
        }

        try {
            application(ww);
            ww.flush();
        } catch (XMLStreamException e) {
            throw new IOException("Failed to write document", e);
        } finally {
            try {
                ww.close();
            } catch (XMLStreamException e) {
                LOG.warn("Failed to close writer", e);
            }
        }
    }

    private void application(final XMLStreamWriter writer) throws XMLStreamException {
        writer.writeStartDocument();
        writer.writeStartElement("application");
        writer.writeDefaultNamespace("http://wadl.dev.java.net/2009/02");
        for (var moduleImport : module.getImports()) {
            writer.writeNamespace(moduleImport.getPrefix(),
                context.findModule(moduleImport.getModuleName().getLocalName(), moduleImport.getRevision())
                    .orElseThrow()
                    .getNamespace().toString());
        }
        writer.writeNamespace(module.getPrefix(), module.getNamespace().toString());

        writer.writeStartElement("grammars");
        writer.writeEmptyElement("include");
        writer.writeAttribute("href", module.getName() + YangConstants.RFC6020_YANG_FILE_EXTENSION);
        for (var moduleImport : module.getImports()) {
            writer.writeEmptyElement("include");
            writer.writeAttribute("href",
                moduleImport.getModuleName().getLocalName() + YangConstants.RFC6020_YANG_FILE_EXTENSION);
        }
        writer.writeEndElement();

        writer.writeStartElement("resources");
        writer.writeAttribute("base", "http://localhost:9998/restconf");

        if (!operationalData.isEmpty()) {
            writer.writeStartElement("resource");
            writer.writeAttribute("path", "operational");
            for (var data : operationalData) {
                firstResource(writer, data, false);
            }
            writer.writeEndElement();
        }

        if (!configData.isEmpty()) {
            writer.writeStartElement("resource");
            writer.writeAttribute("path", "config");
            for (var data : configData) {
                methodPost(writer, data);
            }
            for (var data : configData) {
                firstResource(writer, data, true);
            }
            writer.writeEndElement();
        }

        final var rpcs = module.getRpcs();
        if (!rpcs.isEmpty()) {
            writer.writeStartElement("resource");
            writer.writeAttribute("path", "operations");
            for (var rpc : rpcs) {
                writer.writeStartElement("resource");
                writer.writeAttribute("path", module.getName() + ':' + rpc.getQName().getLocalName());
                writer.writeStartElement("method");
                writer.writeAttribute("name", "POST");
                writer.writeStartElement("request");
                representation(writer, null, "input");
                writer.writeEndElement();
                writer.writeStartElement("response");
                representation(writer, null, "output");
                writer.writeEndElement();
                writer.writeEndElement();
                writer.writeEndElement();
            }
            writer.writeEndElement();
        }

        writer.writeEndElement();
        writer.writeEndElement();
        writer.writeEndDocument();
    }

    @NonNullByDefault
    private void firstResource(final XMLStreamWriter writer, final DataSchemaNode schemaNode, final boolean config)
            throws XMLStreamException {
        final var sb = new StringBuilder().append(module.getName()).append(':');
        createPath(sb, schemaNode);
        writer.writeStartElement("resource");
        writer.writeAttribute("path", sb.toString());
        resourceBody(writer, schemaNode, config);
        writer.writeEndElement();
    }

    @NonNullByDefault
    private void createPath(final StringBuilder sb, final DataSchemaNode schemaNode) {
        sb.append(schemaNode.getQName().getLocalName());

        if (schemaNode instanceof ListSchemaNode listNode) {
            final var keyDefinition = listNode.getKeyDefinition();
            final var size = keyDefinition.size();
            if (size != 0) {
                final var keyLeaves = new ArrayList<LeafSchemaNode>(size);
                for (var leafName : keyDefinition) {
                    keyLeaves.add((LeafSchemaNode) listNode.getDataChildByName(leafName));
                }
                pathListParams = List.copyOf(keyLeaves);
                for (var leafName : keyDefinition) {
                    sb.append("'/{").append(leafName.getLocalName()).append('}');
                }
                return;
            }
        }
        pathListParams = List.of();
    }

    @NonNullByDefault
    private void resourceBody(final XMLStreamWriter writer, final DataSchemaNode schemaNode, final boolean config)
            throws XMLStreamException {
        if (pathListParams != null && !pathListParams.isEmpty()) {
            for (var pathParam : pathListParams) {
                if (pathParam != null) {
                    writer.writeEmptyElement("param");
                    writer.writeAttribute("required", "true");
                    writer.writeAttribute("style", "template");
                    writer.writeAttribute("name", pathParam.getQName().getLocalName());
                    writer.writeAttribute("type", pathParam.typeDefinition().getQName().getLocalName());
                }
            }
        }

        writer.writeStartElement("method");
        writer.writeAttribute("name", "GET");
        writer.writeStartElement("response");
        representation(writer, schemaNode.getQName());
        writer.writeEndElement();
        writer.writeEndElement();

        final var children = ((DataNodeContainer) schemaNode).getChildNodes().stream()
            .filter(child -> child instanceof ContainerSchemaNode || child instanceof ListSchemaNode)
            .toList();

        if (config) {
            writer.writeEmptyElement("method");
            writer.writeAttribute("name", "DELETE");
            writer.writeStartElement("method");
            writer.writeAttribute("name", "PUT");
            writer.writeStartElement("request");
            representation(writer, schemaNode.getQName());
            writer.writeEndElement();
            writer.writeEndElement();
            for (var child : children) {
                methodPost(writer, child);
            }
        }

        for (var child : children) {
            writer.writeStartElement("resource");
            final var sb = new StringBuilder();
            createPath(sb, child);
            writer.writeAttribute("path", sb.toString());
            resourceBody(writer, child, config);
            writer.writeEndElement();
        }
    }

    @NonNullByDefault
    private static void methodPost(final XMLStreamWriter writer, final DataSchemaNode schemaNode)
            throws XMLStreamException {
        writer.writeStartElement("method");
        writer.writeAttribute("name", "POST");
        writer.writeStartElement("request");
        representation(writer, schemaNode.getQName());
        writer.writeEndElement();
        writer.writeEndElement();
    }

    @NonNullByDefault
    private static void representation(final XMLStreamWriter writer, final QName qname) throws XMLStreamException {
        representation(writer, qname.getNamespace(), qname.getLocalName());
    }

    @NonNullByDefault
    private static void representation(final XMLStreamWriter writer, final @Nullable XMLNamespace prefix,
            final String name) throws XMLStreamException {
        representation(writer, prefix, name, "application/xml");
        representation(writer, prefix, name, "text/xml");
        representation(writer, prefix, name, "application/json");
        representation(writer, prefix, name, "application/yang.data+xml");
        representation(writer, prefix, name, "application/yang.data+json");
    }

    @NonNullByDefault
    private static void representation(final XMLStreamWriter writer, final @Nullable XMLNamespace prefix,
            final String name, final String mediaType) throws XMLStreamException {
        writer.writeEmptyElement("representation");
        writer.writeAttribute("mediaType", mediaType);
        writer.writeAttribute("element", name);
    }
}
