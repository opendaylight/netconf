/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 * Copyright (c) 2021 PANTHEON.tech, s.r.o.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.restconf.wadl.generator;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.xtend2.lib.StringConcatenation;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.XMLNamespace;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.ModuleImport;

final class WadlTemplate {
    private final List<DataSchemaNode> operationalData = new ArrayList<>();
    private final List<DataSchemaNode> configData = new ArrayList<>();
    private final EffectiveModelContext context;
    private final Module module;

    private List<LeafSchemaNode> pathListParams;

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

    public CharSequence body() {
        if (!module.getRpcs().isEmpty() || !configData.isEmpty() || !operationalData.isEmpty()) {
            return application();
        }
        return null;
    }

    private CharSequence application() {
        StringConcatenation builder = new StringConcatenation();
        builder.append("<?xml version=\"1.0\"?>");
        builder.newLine();
        builder.append("<application xmlns=\"http://wadl.dev.java.net/2009/02\" ");
        builder.append(importsAsNamespaces());
        builder.append(" xmlns:");
        builder.append(module.getPrefix());
        builder.append("=\"");
        builder.append(module.getNamespace());
        builder.append("\">");
        builder.newLineIfNotEmpty();
        builder.newLine();
        builder.append("    ");
        builder.append("<grammars>");
        builder.newLine();
        builder.append("        ");
        builder.append("<include href=\"");
        builder.append(module.getName(), "        ");
        builder.append(".yang\"/>");
        builder.newLineIfNotEmpty();

        for (final ModuleImport imprt : module.getImports()) {
            builder.append("        ");
            builder.append("<include href=\"");
            builder.append(imprt.getModuleName(), "        ");
            builder.append(".yang\"/>");
            builder.newLineIfNotEmpty();
        }

        builder.append("    ");
        builder.append("</grammars>");
        builder.newLine();
        builder.newLine();
        builder.append("    ");
        builder.append("<resources base=\"http://localhost:9998/restconf\">");
        builder.newLine();

        if (!operationalData.isEmpty()) {
            builder.append("        ");
            builder.append("<resource path=\"operational\">");
            builder.newLine();
            for (final DataSchemaNode schemaNode : operationalData) {
                builder.append("        ");
                builder.append("    ");
                builder.append(firstResource(schemaNode, false), "            ");
                builder.newLineIfNotEmpty();
            }
            builder.append("        ");
            builder.append("</resource>");
            builder.newLine();
        }

        if (!configData.isEmpty()) {
            builder.append("        ");
            builder.append("<resource path=\"config\">");
            builder.newLine();

            for (final DataSchemaNode schemaNode : configData) {
                builder.append("        ");
                builder.append("    ");
                builder.append(mehodPost(schemaNode), "            ");
                builder.newLineIfNotEmpty();
            }
            for (final DataSchemaNode schemaNode : configData) {
                builder.append("        ");
                builder.append("    ");
                builder.append(firstResource(schemaNode, true), "            ");
                builder.newLineIfNotEmpty();
            }
            builder.append("        ");
            builder.append("</resource>");
            builder.newLine();
        }

        final var rpcs = module.getRpcs();
        if (!rpcs.isEmpty()) {
            builder.append("        ");
            builder.append("<resource path=\"operations\">");
            builder.newLine();
            for (var rpc : rpcs) {
                builder.append("        ");
                builder.append("    ");
                builder.append("<resource path=\"");
                builder.append(module.getName(), "            ");
                builder.append(":");
                builder.append(rpc.getQName().getLocalName(), "            ");
                builder.append("\">");
                builder.newLineIfNotEmpty();
                builder.append("        ");
                builder.append("    ");
                builder.append("    ");
                builder.append(methodPostRpc(), "                ");
                builder.newLineIfNotEmpty();
                builder.append("        ");
                builder.append("    ");
                builder.append("</resource>");
                builder.newLine();
            }
            builder.append("        ");
            builder.append("</resource>");
            builder.newLine();
        }

        builder.append("    ");
        builder.append("</resources>");
        builder.newLine();
        builder.append("</application>");
        builder.newLine();
        return builder;
    }

    private CharSequence importsAsNamespaces() {
        StringConcatenation builder = new StringConcatenation();
        for (final ModuleImport imprt : module.getImports()) {
            builder.append("xmlns:");
            builder.append(imprt.getPrefix());
            builder.append("=\"");
            builder.append(context.findModule(imprt.getModuleName().getLocalName(),
                imprt.getRevision()).orElseThrow().getNamespace());
            builder.append("\"");
            builder.newLineIfNotEmpty();
        }
        return builder;
    }

    private String firstResource(final DataSchemaNode schemaNode, final boolean config) {
        StringConcatenation builder = new StringConcatenation();
        builder.append("<resource path=\"");
        builder.append(module.getName());
        builder.append(":");
        builder.append(createPath(schemaNode));
        builder.append("\">");
        builder.newLineIfNotEmpty();
        builder.append("    ");
        builder.append(resourceBody(schemaNode, config), "    ");
        builder.newLineIfNotEmpty();
        builder.append("</resource>");
        builder.newLine();
        return builder.toString();
    }

    private String resource(final DataSchemaNode schemaNode, final boolean config) {
        StringConcatenation builder = new StringConcatenation();
        builder.append("<resource path=\"");
        builder.append(createPath(schemaNode));
        builder.append("\">");
        builder.newLineIfNotEmpty();
        builder.append("    ");
        builder.append(resourceBody(schemaNode, config), "    ");
        builder.newLineIfNotEmpty();
        builder.append("</resource>");
        builder.newLine();
        return builder.toString();
    }

    private String createPath(final DataSchemaNode schemaNode) {
        pathListParams = new ArrayList<>();
        StringBuilder path = new StringBuilder().append(schemaNode.getQName().getLocalName());
        if (schemaNode instanceof ListSchemaNode listNode) {
            for (final QName listKey : listNode.getKeyDefinition()) {
                pathListParams.add((LeafSchemaNode) listNode.getDataChildByName(listKey));
                path.append("'/{").append(listKey.getLocalName()).append('}');
            }
        }
        return path.toString();
    }

    private String resourceBody(final DataSchemaNode schemaNode, final boolean config) {
        StringConcatenation builder = new StringConcatenation();
        if (pathListParams != null && !pathListParams.isEmpty()) {
            builder.append(resourceParams());
            builder.newLineIfNotEmpty();
        }
        builder.append(methodGet(schemaNode));
        builder.newLineIfNotEmpty();
        builder.newLineIfNotEmpty();
        final var children = Iterables.filter(((DataNodeContainer) schemaNode).getChildNodes(),
            WadlTemplate::isListOrContainer);
        if (config) {
            builder.append(methodDelete(schemaNode));
            builder.newLineIfNotEmpty();
            builder.append(methodPut(schemaNode));
            builder.newLineIfNotEmpty();
            for (final DataSchemaNode child : children) {
                builder.append(mehodPost(child));
                builder.newLineIfNotEmpty();
            }
        }
        for (final DataSchemaNode child : children) {
            builder.append(resource(child, config));
            builder.newLineIfNotEmpty();
        }
        return builder.toString();
    }

    private CharSequence resourceParams() {
        StringConcatenation builder = new StringConcatenation();
        for (final LeafSchemaNode pathParam : pathListParams) {
            if (pathParam != null) {
                builder.newLineIfNotEmpty();
                builder.append("<param required=\"true\" style=\"template\" name=\"");
                builder.append(pathParam.getQName().getLocalName());
                builder.append("\" type=\"");
                builder.append(pathParam.getType().getQName().getLocalName());
                builder.append("\"/>");
                builder.newLineIfNotEmpty();
            }
        }
        return builder;
    }

    private static CharSequence methodGet(final DataSchemaNode schemaNode) {
        StringConcatenation builder = new StringConcatenation();
        builder.append("<method name=\"GET\">");
        builder.newLine();
        builder.append("    ");
        builder.append("<response>");
        builder.newLine();
        builder.append("        ");
        builder.append(representation(schemaNode.getQName()), "        ");
        builder.newLineIfNotEmpty();
        builder.append("    ");
        builder.append("</response>");
        builder.newLine();
        builder.append("</method>");
        builder.newLine();
        return builder;
    }

    private static CharSequence methodPut(final DataSchemaNode schemaNode) {
        StringConcatenation builder = new StringConcatenation();
        builder.append("<method name=\"PUT\">");
        builder.newLine();
        builder.append("    ");
        builder.append("<request>");
        builder.newLine();
        builder.append("        ");
        builder.append(representation(schemaNode.getQName()), "        ");
        builder.newLineIfNotEmpty();
        builder.append("    ");
        builder.append("</request>");
        builder.newLine();
        builder.append("</method>");
        builder.newLine();
        return builder;
    }

    private static CharSequence mehodPost(final DataSchemaNode schemaNode) {
        StringConcatenation builder = new StringConcatenation();
        builder.append("<method name=\"POST\">");
        builder.newLine();
        builder.append("    ");
        builder.append("<request>");
        builder.newLine();
        builder.append("        ");
        builder.append(representation(schemaNode.getQName()), "        ");
        builder.newLineIfNotEmpty();
        builder.append("    ");
        builder.append("</request>");
        builder.newLine();
        builder.append("</method>");
        builder.newLine();
        return builder;
    }

    private static CharSequence methodPostRpc() {
        StringConcatenation builder = new StringConcatenation();
        builder.append("<method name=\"POST\">");
        builder.newLine();
        builder.append("    ");
        builder.append("<request>");
        builder.newLine();
        builder.append("    ");
        builder.append("    ");
        builder.append(representation(null, "input"), "        ");
        builder.newLineIfNotEmpty();
        builder.append("    ");
        builder.append("</request>");
        builder.newLine();
        builder.append("    ");
        builder.append("<response>");
        builder.newLine();
        builder.append("    ");
        builder.append("    ");
        builder.append(representation(null, "output"), "        ");
        builder.newLineIfNotEmpty();
        builder.append("    ");
        builder.append("</response>");
        builder.newLine();
        builder.append("</method>");
        builder.newLine();
        return builder;
    }

    private static CharSequence methodDelete(final DataSchemaNode schemaNode) {
        StringConcatenation builder = new StringConcatenation();
        builder.append("<method name=\"DELETE\" />");
        builder.newLine();
        return builder;
    }

    private static CharSequence representation(final QName qname) {
        return representation(qname.getNamespace(), qname.getLocalName());
    }

    private static CharSequence representation(final XMLNamespace prefix, final String name) {
        StringConcatenation builder = new StringConcatenation();
        builder.newLineIfNotEmpty();
        builder.append("<representation mediaType=\"application/xml\" element=\"");
        builder.append(name);
        builder.append("\"/>");
        builder.newLineIfNotEmpty();
        builder.append("<representation mediaType=\"text/xml\" element=\"");
        builder.append(name);
        builder.append("\"/>");
        builder.newLineIfNotEmpty();
        builder.append("<representation mediaType=\"application/json\" element=\"");
        builder.append(name);
        builder.append("\"/>");
        builder.newLineIfNotEmpty();
        builder.append("<representation mediaType=\"application/yang.data+xml\" element=\"");
        builder.append(name);
        builder.append("\"/>");
        builder.newLineIfNotEmpty();
        builder.append("<representation mediaType=\"application/yang.data+json\" element=\"");
        builder.append(name);
        builder.append("\"/>");
        builder.newLineIfNotEmpty();
        return builder;
    }

    private static boolean isListOrContainer(final DataSchemaNode schemaNode) {
        return schemaNode instanceof ListSchemaNode || schemaNode instanceof ContainerSchemaNode;
    }
}
