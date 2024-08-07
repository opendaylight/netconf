/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.impl;

import static org.opendaylight.restconf.openapi.util.RestDocgenUtil.widthList;

import com.fasterxml.jackson.core.JsonGenerator;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import org.opendaylight.restconf.openapi.jaxrs.OpenApiBodyWriter;
import org.opendaylight.restconf.openapi.model.NodeSchemaEntity;
import org.opendaylight.restconf.openapi.model.OpenApiEntity;
import org.opendaylight.restconf.openapi.model.RpcSchemaEntity;
import org.opendaylight.restconf.openapi.model.SchemaEntity;
import org.opendaylight.restconf.openapi.model.SchemasEntity;
import org.opendaylight.yangtools.yang.model.api.ActionNodeContainer;
import org.opendaylight.yangtools.yang.model.api.ChoiceSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack;

public final class SchemasStream extends InputStream {
    private static final String OBJECT_TYPE = "object";
    private static final String INPUT_SUFFIX = "_input";
    private static final String OUTPUT_SUFFIX = "_output";

    private final Iterator<? extends Module> iterator;
    private final OpenApiBodyWriter writer;
    private final EffectiveModelContext modelContext;
    private final boolean isForSingleModule;
    private final ByteArrayOutputStream stream;
    private final JsonGenerator generator;
    private final int width;
    private final int depth;

    private Reader reader;
    private ReadableByteChannel channel;
    private boolean eof;

    public SchemasStream(final EffectiveModelContext modelContext, final OpenApiBodyWriter writer,
            final Iterator<? extends Module> iterator, final boolean isForSingleModule,
            final ByteArrayOutputStream stream, final JsonGenerator generator, final int width,
            final int depth) {
        this.iterator = iterator;
        this.modelContext = modelContext;
        this.writer = writer;
        this.isForSingleModule = isForSingleModule;
        this.stream = stream;
        this.generator = generator;
        this.width = width;
        this.depth = depth;
    }

    @Override
    public int read() throws IOException {
        if (eof) {
            return -1;
        }
        if (reader == null) {
            generator.writeObjectFieldStart("schemas");
            generator.flush();
            reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(stream.toByteArray()), StandardCharsets.UTF_8));
            stream.reset();
        }
        var read = reader.read();
        while (read == -1) {
            if (iterator.hasNext()) {
                reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(
                    writeNextEntity(new SchemasEntity(toComponents(iterator.next(), modelContext, isForSingleModule,
                            width, depth)))),
                        StandardCharsets.UTF_8));
                read = reader.read();
                continue;
            }
            generator.writeEndObject();
            generator.flush();
            reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(stream.toByteArray()), StandardCharsets.UTF_8));
            stream.reset();
            eof = true;
            return reader.read();
        }
        return read;
    }

    @Override
    public int read(final byte[] array, final int off, final int len) throws IOException {
        if (eof) {
            return -1;
        }
        if (channel == null) {
            generator.writeObjectFieldStart("schemas");
            generator.flush();
            channel = Channels.newChannel(new ByteArrayInputStream(stream.toByteArray()));
            stream.reset();
        }
        var read = channel.read(ByteBuffer.wrap(array, off, len));
        while (read == -1) {
            if (iterator.hasNext()) {
                channel = Channels.newChannel(new ByteArrayInputStream(writeNextEntity(
                    new SchemasEntity(toComponents(iterator.next(), modelContext, isForSingleModule, width, depth)))));
                read = channel.read(ByteBuffer.wrap(array, off, len));
                continue;
            }
            generator.writeEndObject();
            generator.flush();
            channel = Channels.newChannel(new ByteArrayInputStream(stream.toByteArray()));
            stream.reset();
            eof = true;
            return channel.read(ByteBuffer.wrap(array, off, len));
        }
        return read;
    }

    private byte[] writeNextEntity(final OpenApiEntity entity) throws IOException {
        writer.writeTo(entity, null, null, null, null, null, null);
        return writer.readFrom();
    }

    private static Deque<SchemaEntity> toComponents(final Module module, final EffectiveModelContext modelContext,
            final boolean isForSingleModule, final int width, final int depth) {
        final var result = new ArrayDeque<SchemaEntity>();
        final var definitionNames = new DefinitionNames();
        final var stack = SchemaInferenceStack.of(modelContext);
        final var moduleName = module.getName();
        if (isForSingleModule) {
            definitionNames.addUnlinkedName(moduleName + "_module");
        }
        final var children = new ArrayList<DataSchemaNode>();
        for (final var rpc : module.getRpcs()) {
            stack.enterSchemaTree(rpc.getQName());
            final var rpcName = rpc.getQName().getLocalName();
            final var rpcInput = rpc.getInput();
            if (!rpcInput.getChildNodes().isEmpty()) {
                final var input = new RpcSchemaEntity(rpcInput, moduleName + "_" + rpcName + INPUT_SUFFIX, null,
                    OBJECT_TYPE, stack, moduleName, false, definitionNames, width, depth, 0);
                result.add(input);
                stack.enterSchemaTree(rpcInput.getQName());
                for (final var child : rpcInput.getChildNodes()) {
                    if (!children.contains(child)) {
                        children.add(child);
                        processDataAndActionNodes(child, moduleName, stack, definitionNames, result, moduleName,
                            false, width, depth, 0);
                    }
                }
                stack.exit();
            }
            final var rpcOutput = rpc.getOutput();
            if (!rpcOutput.getChildNodes().isEmpty()) {
                final var output = new RpcSchemaEntity(rpcOutput, moduleName + "_" + rpcName + OUTPUT_SUFFIX, null,
                    OBJECT_TYPE, stack, moduleName, false, definitionNames, width, depth, 0);
                result.add(output);
                stack.enterSchemaTree(rpcOutput.getQName());
                for (final var child : rpcOutput.getChildNodes()) {
                    if (!children.contains(child)) {
                        children.add(child);
                        processDataAndActionNodes(child, moduleName, stack, definitionNames, result, moduleName,
                            false, width, depth, 0);
                    }
                }
                stack.exit();
            }
            stack.exit();
        }

        final var childNodes = widthList(module, width);
        for (final var childNode : childNodes) {
            processDataAndActionNodes(childNode, moduleName, stack, definitionNames, result, moduleName,
                true, width, depth, 0);
        }
        return result;
    }

    private static void processDataAndActionNodes(final DataSchemaNode node, final String title,
            final SchemaInferenceStack stack, final DefinitionNames definitionNames,
            final ArrayDeque<SchemaEntity> result, final String parentName, final boolean isParentConfig,
            final int width, final int depth, final int nodeDepth) {
        if (depth > 0 && nodeDepth + 1 > depth) {
            return;
        }
        if (node instanceof ContainerSchemaNode || node instanceof ListSchemaNode) {
            final var newTitle = title + "_" + node.getQName().getLocalName();
            if (definitionNames.isListedNode(node, newTitle)) {
                // This means schema for this node is already processed
                return;
            }
            final var discriminator = definitionNames.pickDiscriminator(node, List.of(newTitle));
            final var child = new NodeSchemaEntity(node, newTitle, discriminator, OBJECT_TYPE, stack, parentName,
                isParentConfig, definitionNames, width, depth, nodeDepth + 1);
            final var isConfig = node.isConfiguration() && isParentConfig;
            result.add(child);
            stack.enterSchemaTree(node.getQName());
            processActions(node, newTitle, stack, definitionNames, result, parentName, width, depth, 0);
            final var childNodes = widthList((DataNodeContainer) node, width);
            for (final var childNode : childNodes) {
                processDataAndActionNodes(childNode, newTitle, stack, definitionNames, result, newTitle, isConfig,
                    width, depth, nodeDepth + 1);
            }
            stack.exit();
        } else if (node instanceof ChoiceSchemaNode choiceNode && !choiceNode.getCases().isEmpty()) {
            // Process default case or first case
            final var caseNode = choiceNode.getDefaultCase()
                .orElseGet(() -> choiceNode.getCases().stream().findFirst()
                    .orElseThrow(() -> new IllegalStateException("No cases found in ChoiceSchemaNode")));
            stack.enterSchemaTree(choiceNode.getQName());
            stack.enterSchemaTree(caseNode.getQName());
            final var childNodes = widthList(caseNode, width);
            for (final var childNode : childNodes) {
                processDataAndActionNodes(childNode, title, stack, definitionNames, result, parentName,
                    isParentConfig, width, depth, nodeDepth + 1);
            }
            stack.exit(); // Exit the CaseSchemaNode context
            stack.exit(); // Exit the ChoiceSchemaNode context
        }
    }

    private static void processActions(final DataSchemaNode node, final String title, final SchemaInferenceStack stack,
            final DefinitionNames definitionNames, final ArrayDeque<SchemaEntity> result, final String parentName,
            final int width, final int depth, final int nodeDepth) {
        for (final var actionDef : ((ActionNodeContainer) node).getActions()) {
            stack.enterSchemaTree(actionDef.getQName());
            final var actionName = actionDef.getQName().getLocalName();
            final var actionInput = actionDef.getInput();
            if (!actionInput.getChildNodes().isEmpty()) {
                final var input = new RpcSchemaEntity(actionInput, title + "_" + actionName + INPUT_SUFFIX, null,
                    OBJECT_TYPE, stack, parentName, false, definitionNames, width, depth, nodeDepth + 1);
                result.add(input);
            }
            final var actionOutput = actionDef.getOutput();
            if (!actionOutput.getChildNodes().isEmpty()) {
                final var output = new RpcSchemaEntity(actionOutput, title + "_" + actionName + OUTPUT_SUFFIX, null,
                    OBJECT_TYPE, stack, parentName, false, definitionNames, width, depth, nodeDepth + 1);
                result.add(output);
            }
            stack.exit();
        }
    }
}
