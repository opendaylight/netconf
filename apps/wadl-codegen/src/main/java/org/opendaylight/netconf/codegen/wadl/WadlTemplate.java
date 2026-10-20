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
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
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

final class WadlTemplate {
    private static final IndentedXML INDENTED_XML = IndentedXML.of(4);
    private static final XMLOutputFactory XOF;

    static {
        final var factory = XMLOutputFactory.newFactory();
        factory.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES, false);
        XOF = factory;
    }

    private final List<DataSchemaNode> operationalData = new ArrayList<>();
    private final List<DataSchemaNode> configData = new ArrayList<>();
    private final EffectiveModelContext context;
    private final Module module;

    private List<LeafSchemaNode> pathListParams;

    @NonNullByDefault
    WadlTemplate(final EffectiveModelContext context, final Module module) {
        this.context = requireNonNull(context);
        this.module = requireNonNull(module);
        for (final DataSchemaNode child : module.getChildNodes()) {
            if (child instanceof ContainerSchemaNode || child instanceof ListSchemaNode) {
                if (child.isConfiguration()) {
                    configData.add(child);
                } else {
                    operationalData.add(child);
                }
            }
        }
    }

    @Nullable String body() throws IOException {
        return module.getRpcs().isEmpty() && configData.isEmpty() && operationalData.isEmpty() ? null : application();
    }

    @NonNullByDefault
    private String application() throws IOException {
        final var sw = new StringWriter();
        final XMLStreamWriter ww;
        try {
            ww = INDENTED_XML.wrapStreamWriter(XOF.createXMLStreamWriter(sw));
        } catch (XMLStreamException e) {
            throw new IOException("Failed to create writer", e);
        }

        try {
            application(ww);
            ww.flush();
        } catch (XMLStreamException e) {
            final var thrown = new IOException("Failed to write document", e);
            try {
                ww.close();
            } catch (XMLStreamException suppressed) {
                // TODO Auto-generated catch block
                suppressed.printStackTrace();
            }
            throw thrown;
        }

        return sw.toString();
    }

    private void application(final XMLStreamWriter writer) throws XMLStreamException {
        writer.writeStartDocument();
        writer.writeStartElement("application");
        writer.writeDefaultNamespace("http://wadl.dev.java.net/2009/02");
        final var moduleImports =  module.getImports();
        for (var moduleImport : moduleImports) {
            writer.writeNamespace(moduleImport.getPrefix(),
                context.findModule(moduleImport.getModuleName().getLocalName(), moduleImport.getRevision())
                    .orElseThrow()
                    .getNamespace().toString());
        }

        writer.writeCharacters("\n");
        writer.writeStartElement("grammars");
        writer.writeStartElement("include");
        writer.writeAttribute("href", module.getName() + YangConstants.RFC6020_YANG_FILE_EXTENSION);
        writer.writeEndElement();
        for (var moduleImport : moduleImports) {
            writer.writeStartElement("include");
            writer.writeAttribute("href", moduleImport.getModuleName() + YangConstants.RFC6020_YANG_FILE_EXTENSION);
            writer.writeEndElement();
        }
        writer.writeEndElement();


        writer.writeCharacters("\n");
        writer.writeStartElement("resources");
        writer.writeAttribute("base", "http://localhost:9998/restconf");

        if (!operationalData.isEmpty()) {
            bb
                .oe("resource").attr("path", "operational").oB();
            for (var data : operationalData) {
                firstResource(bb, data, false);
            }
            bb
                .cE("resource");
        }

        if (!configData.isEmpty()) {
            bb
                .oe("resource").attr("path", "config").oB();
            for (var data : configData) {
                methodPost(bb, data);
            }
            for (var data : configData) {
                firstResource(bb, data, true);
            }
            bb
                .cE("resource");
        }

        final var rpcs = module.getRpcs();
        if (!rpcs.isEmpty()) {
            bb
                .oe("resource").attr("path", "operations").oB();
            for (var rpc : rpcs) {
                bb
                    .oe("resource").attr("path", module.getName() + ':' + rpc.getQName().getLocalName()).oB()
                        .oe("method").attr("name", "POST").oB()
                            .elem("request", representation(null, "input"))
                            .elem("response", representation(null, "output"))
                        .cE("method")
                    .cE("resource");
            }
            bb.cE("resource");
        }

        return bb
                .cE("resources")
            .cE("application")
            .toRawString();
    }

    @NonNullByDefault
    private void firstResource(final BlockBuilder bb, final DataSchemaNode schemaNode, final boolean config) {
        bb
            .oe("resource").str(" path=\"").str(module.getName()).str(":").frg(createPath(schemaNode)).str("\"").oB()
                .frg(resourceBody(schemaNode, config))
            .cE("resource");
    }

    @NonNullByDefault
    private BlockFragment createPath(final DataSchemaNode schemaNode) {
        final var localName = schemaNode.getQName().getLocalName();
        if (schemaNode instanceof ListSchemaNode listNode) {
            final var keyDefinition = listNode.getKeyDefinition();
            final var size = keyDefinition.size();
            if (size != 0) {
                final var keyLeaves = new ArrayList<LeafSchemaNode>(size);
                for (var leafName : keyDefinition) {
                    keyLeaves.add((LeafSchemaNode) listNode.getDataChildByName(leafName));
                }
                pathListParams = List.copyOf(keyLeaves);
                return bb -> {
                    bb.str(localName);
                    for (var leafName : keyDefinition) {
                        bb.str("'/{").str(leafName.getLocalName()).str("}");
                    }
                };
            }
        }

        pathListParams = List.of();
        return bb -> bb.str(localName);
    }

    @NonNullByDefault
    private BlockFragment resourceBody(final DataSchemaNode schemaNode, final boolean config) {
        return bb -> {
            if (pathListParams != null && !pathListParams.isEmpty()) {
                for (var pathParam : pathListParams) {
                    if (pathParam != null) {
                        bb
                            .oe("param")
                                .attr("required", "true")
                                .attr("style", "template")
                                .attr("name", pathParam.getQName().getLocalName())
                                .attr("type", pathParam.typeDefinition().getQName().getLocalName())
                            .cE();
                    }
                }
            }

            bb
                .oe("method").attr("name", "GET").oB()
                    .elem("response", representation(schemaNode.getQName()))
                .cE("method");

            final var children = ((DataNodeContainer) schemaNode).getChildNodes().stream()
                .filter(WadlTemplate::isListOrContainer)
                .toList();

            if (config) {
                bb
                    .eol("<method name=\"DELETE\" />")
                    .oe("method").attr("name", "PUT").oB()
                        .elem("request", representation(schemaNode.getQName()))
                    .cE("method");
                for (var child : children) {
                    methodPost(bb, child);
                }
            }

            for (var child : children) {
                bb
                    .oe("resource").attr("path", createPath(child)).oB()
                        .frg(resourceBody(child, config))
                    .cE("resource");
            }
        };
    }

    @NonNullByDefault
    private static BlockBuilder methodPost(final BlockBuilder bb, final DataSchemaNode schemaNode) {
        return bb
            .oe("method").attr("name", "POST").oB()
                .elem("request", representation(schemaNode.getQName()))
            .cE("method");
    }

    @NonNullByDefault
    private static BlockFragment representation(final QName qname) {
        return representation(qname.getNamespace(), qname.getLocalName());
    }

    @NonNullByDefault
    private static BlockFragment representation(final @Nullable XMLNamespace prefix, final String name) {
        return bb -> bb
            .oe("representation").attr("mediaType", "application/xml").attr("element", name).cE()
            .oe("representation").attr("mediaType", "text/xml").attr("element", name).cE()
            .oe("representation").attr("mediaType", "application/json").attr("element", name).cE()
            .oe("representation").attr("mediaType", "application/yang.data+xml").attr("element", name).cE()
            .oe("representation").attr("mediaType", "application/yang.data+json").attr("element", name).cE()
            .toRawString();
    }

    @NonNullByDefault
    private static boolean isListOrContainer(final DataSchemaNode schemaNode) {
        return schemaNode instanceof ListSchemaNode || schemaNode instanceof ContainerSchemaNode;
    }
}
