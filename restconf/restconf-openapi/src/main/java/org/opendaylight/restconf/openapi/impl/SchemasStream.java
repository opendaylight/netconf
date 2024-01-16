/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.impl;

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
import org.opendaylight.restconf.openapi.model.SchemaEntity;
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
    private final EffectiveModelContext context;
    private final JsonGenerator generator;
    private final ByteArrayOutputStream stream;

    private Reader reader;
    private ReadableByteChannel channel;
    private boolean schemesWritten;

    public SchemasStream(final EffectiveModelContext context, final OpenApiBodyWriter writer,
            final JsonGenerator generator, final ByteArrayOutputStream stream,
            final Iterator<? extends Module> iterator) {
        this.iterator = iterator;
        this.context = context;
        this.writer = writer;
        this.generator = generator;
        this.stream = stream;
    }

    @Override
    public int read() throws IOException {
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
                reader = new BufferedReader(
                    new InputStreamReader(new SchemaStream(toComponents(iterator.next(), context), writer),
                        StandardCharsets.UTF_8));
                read = reader.read();
                continue;
            }
            if (!schemesWritten) {
                generator.writeEndObject();
                schemesWritten = true;
                continue;
            }
            generator.flush();
            reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(stream.toByteArray()), StandardCharsets.UTF_8));
            stream.reset();
            return reader.read();
        }

        return read;
    }

    @Override
    public int read(final byte[] array, final int off, final int len) throws IOException {
        if (channel == null) {
            generator.writeObjectFieldStart("schemas");
            generator.flush();
            channel = Channels.newChannel(new ByteArrayInputStream(stream.toByteArray()));
            stream.reset();
        }

        var read = channel.read(ByteBuffer.wrap(array));
        while (read == -1) {
            if (iterator.hasNext()) {
                channel = Channels.newChannel(new SchemaStream(toComponents(iterator.next(), context), writer));
                read = channel.read(ByteBuffer.wrap(array));
                continue;
            }
            if (!schemesWritten) {
                generator.writeEndObject();
                schemesWritten = true;
                continue;
            }
            generator.flush();
            channel = Channels.newChannel(new ByteArrayInputStream(stream.toByteArray()));
            stream.reset();
            return channel.read(ByteBuffer.wrap(array));
        }

        return read;
    }

    private static Deque<SchemaEntity> toComponents(final Module module, final EffectiveModelContext context) {
        final var result = new ArrayDeque<SchemaEntity>();
        final var definitionNames = new DefinitionNames();
        final var stack = SchemaInferenceStack.of(context);
        final var moduleName = module.getName();
        final var children = new ArrayList<DataSchemaNode>();
        for (final var rpc : module.getRpcs()) {
            stack.enterSchemaTree(rpc.getQName());
            final var rpcName = rpc.getQName().getLocalName();
            final var rpcInput = rpc.getInput();
            if (!rpcInput.getChildNodes().isEmpty()) {
                final var input = new SchemaEntity(rpcInput, moduleName + "_" + rpcName + INPUT_SUFFIX, null,
                    OBJECT_TYPE, stack, moduleName, false, definitionNames, EntityType.RPC);
                result.add(input);
                stack.enterSchemaTree(rpcInput.getQName());
                for (final var child : rpcInput.getChildNodes()) {
                    if (!children.contains(child)) {
                        children.add(child);
                        processDataAndActionNodes(child, moduleName, stack, definitionNames, result, moduleName, false);
                    }
                }
                stack.exit();
            }
            final var rpcOutput = rpc.getOutput();
            if (!rpcOutput.getChildNodes().isEmpty()) {
                final var output = new SchemaEntity(rpcOutput, moduleName + "_" + rpcName + OUTPUT_SUFFIX, null,
                    OBJECT_TYPE, stack, moduleName, false, definitionNames, EntityType.RPC);
                result.add(output);
                stack.enterSchemaTree(rpcOutput.getQName());
                for (final var child : rpcOutput.getChildNodes()) {
                    if (!children.contains(child)) {
                        children.add(child);
                        processDataAndActionNodes(child, moduleName, stack, definitionNames, result, moduleName, false);
                    }
                }
                stack.exit();
            }
            stack.exit();
        }

        for (final var childNode : module.getChildNodes()) {
            processDataAndActionNodes(childNode, moduleName, stack, definitionNames, result, moduleName,
                true);
        }
        return result;
    }

    private static void processDataAndActionNodes(final DataSchemaNode node, final String title,
            final SchemaInferenceStack stack, final DefinitionNames definitionNames,
            final ArrayDeque<SchemaEntity> result, final String parentName, final boolean isParentConfig) {
        if (node instanceof ContainerSchemaNode || node instanceof ListSchemaNode) {
            final var newTitle = title + "_" + node.getQName().getLocalName();
            final String discriminator;
            if (!definitionNames.isListedNode(node)) {
                final var parentNameConfigLocalName = parentName + "_" + node.getQName().getLocalName();
                final var names = List.of(parentNameConfigLocalName);
                discriminator = definitionNames.pickDiscriminator(node, names);
            } else {
                discriminator = definitionNames.getDiscriminator(node);
            }
            final var child = new SchemaEntity(node, newTitle, discriminator, OBJECT_TYPE, stack, parentName,
                isParentConfig, definitionNames, EntityType.NODE);
            final var isConfig = node.isConfiguration() && isParentConfig;
            result.add(child);
            stack.enterSchemaTree(node.getQName());
            processActions(node, title, stack, definitionNames, result, parentName);
            for (final var childNode : ((DataNodeContainer) node).getChildNodes()) {
                processDataAndActionNodes(childNode, newTitle, stack, definitionNames, result, newTitle, isConfig);
            }
            stack.exit();
        } else if (node instanceof ChoiceSchemaNode choiceNode && !choiceNode.getCases().isEmpty()) {
            // Process default case or first case
            final var caseNode = choiceNode.getDefaultCase()
                .orElseGet(() -> choiceNode.getCases().stream().findFirst()
                    .orElseThrow(() -> new IllegalStateException("No cases found in ChoiceSchemaNode")));
            stack.enterSchemaTree(choiceNode.getQName());
            stack.enterSchemaTree(caseNode.getQName());
            for (final var childNode : caseNode.getChildNodes()) {
                processDataAndActionNodes(childNode, title, stack, definitionNames, result, parentName, isParentConfig);
            }
            stack.exit(); // Exit the CaseSchemaNode context
            stack.exit(); // Exit the ChoiceSchemaNode context
        }
    }

    private static void processActions(final DataSchemaNode node, final String title, final SchemaInferenceStack stack,
            final DefinitionNames definitionNames, final ArrayDeque<SchemaEntity> result, final String parentName) {
        for (final var actionDef : ((ActionNodeContainer) node).getActions()) {
            stack.enterSchemaTree(actionDef.getQName());
            final var actionName = actionDef.getQName().getLocalName();
            final var actionInput = actionDef.getInput();
            if (!actionInput.getChildNodes().isEmpty()) {
                final var input = new SchemaEntity(actionInput, title + "_" + actionName + INPUT_SUFFIX, null,
                    OBJECT_TYPE, stack, parentName, false, definitionNames, EntityType.RPC);
                result.add(input);
            }
            final var actionOutput = actionDef.getOutput();
            if (!actionOutput.getChildNodes().isEmpty()) {
                final var output = new SchemaEntity(actionOutput, title + "_" + actionName + OUTPUT_SUFFIX, null,
                    OBJECT_TYPE, stack, parentName, false, definitionNames, EntityType.RPC);
                result.add(output);
            }
            stack.exit();
        }
    }

    public enum EntityType {
        NODE,
        RPC
    }
}
