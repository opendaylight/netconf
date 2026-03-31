/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.model;

import static java.util.Objects.requireNonNull;
import static org.opendaylight.restconf.openapi.model.RestDocgenUtil.widthList;

import com.fasterxml.jackson.core.JsonGenerator;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.yangtools.yang.model.api.ActionNodeContainer;
import org.opendaylight.yangtools.yang.model.api.ChoiceSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.util.SchemaInferenceStack;

public final class SchemasEntity extends OpenApiEntity {
    private static final String OBJECT_TYPE = "object";
    private static final String INPUT_SUFFIX = "_input";
    private static final String OUTPUT_SUFFIX = "_output";

    private final @NonNull EffectiveModelContext modelContext;
    private final @NonNull Collection<? extends Module> modules;
    private final boolean isForSingleModule;
    private final int width;
    private final int depth;

    public SchemasEntity(final EffectiveModelContext modelContext, final Collection<? extends Module> modules,
            final boolean isForSingleModule, final int width, final int depth) {
        this.modelContext = requireNonNull(modelContext);
        this.modules = requireNonNull(modules);
        this.isForSingleModule = isForSingleModule;
        this.width = width;
        this.depth = depth;
    }

    @Override
    public void generate(final @NonNull JsonGenerator generator) throws IOException {
        generator.writeObjectFieldStart("schemas");

        for (var module : modules) {
            for (var schema : toComponents(module)) {
                schema.generate(generator);
            }
        }

        generator.writeEndObject();
    }

    private ArrayDeque<SchemaEntity> toComponents(final Module module) throws IOException {
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
                        processDataAndActionNodes(child, moduleName, stack, definitionNames, result, moduleName, false,
                            0);
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
                        processDataAndActionNodes(child, moduleName, stack, definitionNames, result, moduleName, false,
                            0);
                    }
                }
                stack.exit();
            }
            stack.exit();
        }

        final var childNodes = widthList(module, width);
        for (final var childNode : childNodes) {
            processDataAndActionNodes(childNode, moduleName, stack, definitionNames, result, moduleName, true, 0);
        }
        return result;
    }

    private void processDataAndActionNodes(final DataSchemaNode node, final String title,
            final SchemaInferenceStack stack, final DefinitionNames definitionNames,
            final ArrayDeque<SchemaEntity> result, final String parentName, final boolean isParentConfig,
            final int nodeDepth) {
        // Check if processed node isn't deeper than set cut off depth
        if (depth > 0 && nodeDepth > depth) {
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
                isParentConfig, definitionNames, width, depth, nodeDepth);
            final var isConfig = node.isConfiguration() && isParentConfig;
            result.add(child);
            stack.enterSchemaTree(node.getQName());
            processActions(node, newTitle, stack, definitionNames, result, parentName, 0);
            final var childNodes = widthList((DataNodeContainer) node, width);
            for (final var childNode : childNodes) {
                processDataAndActionNodes(childNode, newTitle, stack, definitionNames, result, newTitle, isConfig,
                    nodeDepth + 1);
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
                processDataAndActionNodes(childNode, title, stack, definitionNames, result, parentName, isParentConfig,
                    nodeDepth + 1);
            }
            stack.exit(); // Exit the CaseSchemaNode context
            stack.exit(); // Exit the ChoiceSchemaNode context
        }
    }

    private void processActions(final DataSchemaNode node, final String title, final SchemaInferenceStack stack,
            final DefinitionNames definitionNames, final ArrayDeque<SchemaEntity> result, final String parentName,
            final int nodeDepth) {
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
