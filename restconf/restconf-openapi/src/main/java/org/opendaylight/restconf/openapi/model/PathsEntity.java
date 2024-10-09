/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.model;

import static java.util.Objects.requireNonNull;
import static org.opendaylight.restconf.openapi.util.RestDocgenUtil.resolveFullNameFromNode;
import static org.opendaylight.restconf.openapi.util.RestDocgenUtil.resolvePathArgumentsName;
import static org.opendaylight.restconf.openapi.util.RestDocgenUtil.widthList;

import com.fasterxml.jackson.core.JsonGenerator;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.stream.Collectors;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.model.api.ActionNodeContainer;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.model.api.type.BooleanTypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.DecimalTypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.Int16TypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.Int32TypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.Int64TypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.Int8TypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.Uint16TypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.Uint32TypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.Uint64TypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.Uint8TypeDefinition;

public final class PathsEntity extends OpenApiEntity {
    private static final String OPERATIONS = "operations";
    private static final String DATA = "data";

    private final @NonNull EffectiveModelContext modelContext;
    private final @NonNull String deviceName;
    private final @NonNull String urlPrefix;
    private final boolean isForSingleModule;
    private final boolean includeDataStore;
    private final @NonNull Collection<? extends Module> modules;
    private final @NonNull String basePath;
    private final int width;
    private final int depth;

    public PathsEntity(final EffectiveModelContext modelContext, final String deviceName, final String urlPrefix,
            final boolean isForSingleModule, final boolean includeDataStore, final Collection<? extends Module> modules,
            final String basePath, final int width, final int depth) {
        this.modelContext = requireNonNull(modelContext);
        this.deviceName = requireNonNull(deviceName);
        this.urlPrefix = requireNonNull(urlPrefix);
        this.isForSingleModule = isForSingleModule;
        this.includeDataStore = includeDataStore;
        this.modules = requireNonNull(modules);
        this.basePath = requireNonNull(basePath);
        this.width = width;
        this.depth = depth;
    }

    @Override
    public void generate(final JsonGenerator generator) throws IOException {
        generator.writeObjectFieldStart("paths");

        boolean hasRootPostLink = false;
        boolean hasAddedDataStore = false;

        final var result = new ArrayDeque<PathEntity>();
        for (var module : modules) {
            if (includeDataStore && !hasAddedDataStore) {
                final var childNode = module.getChildNodes().stream()
                    .filter(node -> node.isConfiguration()
                        && (node instanceof ListSchemaNode || node instanceof ContainerSchemaNode))
                    .findFirst();
                if (childNode.isPresent()) {
                    final var dataPath = basePath + DATA + urlPrefix;
                    final var post = new PostEntity(childNode.orElseThrow(), deviceName, module.getName(),
                        List.of(), childNode.orElseThrow().getQName().getLocalName(), module, List.of(), true);
                    result.add(new PathEntity(dataPath, post, new GetRootEntity(deviceName, "data")));
                    final var operationsPath = basePath + OPERATIONS + urlPrefix;
                    result.add(new PathEntity(operationsPath, new GetRootEntity(deviceName, "operations")));
                    hasAddedDataStore = true;
                }
            }

            // RPC operations (via post) - RPCs have their own path
            for (final var rpc : module.getRpcs()) {
                final var localName = rpc.getQName().getLocalName();
                final var post = new PostEntity(rpc, deviceName, module.getName(), List.of(), localName, null,
                    List.of(), false);
                final var resolvedPath = basePath + OPERATIONS + urlPrefix + "/" + module.getName() + ":" + localName;
                final var entity = new PathEntity(resolvedPath, post);
                result.add(entity);
            }

            final var childNodes = widthList(module, width);
            for (final var node : childNodes) {
                final var moduleName = module.getName();
                final boolean isConfig = node.isConfiguration();
                final var nodeLocalName = node.getQName().getLocalName();

                if (node instanceof ListSchemaNode || node instanceof ContainerSchemaNode) {
                    if (isConfig && !hasRootPostLink && isForSingleModule) {
                        final var resolvedPath = basePath + DATA + urlPrefix;
                        result.add(new PathEntity(resolvedPath,
                            new PostEntity(node, deviceName, moduleName, List.of(), nodeLocalName, module, List.of(),
                                false)));
                        hasRootPostLink = true;
                    }
                    //process first node
                    final var pathParams = new ArrayList<ParameterEntity>();
                    final var localName = moduleName + ":" + nodeLocalName;
                    final var path = urlPrefix + "/" + processPath(node, pathParams, localName);
                    processChildNode(node, pathParams, moduleName, result, path, nodeLocalName, isConfig, null,
                        List.of(), 0);
                }
            }

            for (var path = result.poll(); path != null; path = result.poll()) {
                path.generate(generator);
            }
        }

        generator.writeEndObject();
    }

    private void processChildNode(final DataSchemaNode node, final List<ParameterEntity> pathParams,
            final String moduleName, final Deque<PathEntity> result, final String path, final String refPath,
            final boolean isConfig, final SchemaNode parentNode, final List<SchemaNode> parentNodes,
            final int nodeDepth) {
        if (depth > 0 && nodeDepth + 1 > depth) {
            return;
        }
        final var resourcePath = basePath + DATA + path;
        final var fullName = resolveFullNameFromNode(node.getQName(), modelContext);
        final var firstChild = getListOrContainerChildNode((DataNodeContainer) node, width, depth, nodeDepth);
        if (firstChild != null && node instanceof ContainerSchemaNode) {
            result.add(processTopPathEntity(node, resourcePath, pathParams, moduleName, refPath, isConfig, fullName,
                firstChild));
        } else {
            result.add(processDataPathEntity(node, resourcePath, pathParams, moduleName, refPath, isConfig, fullName));
        }
        final var listOfParents = new ArrayList<>(parentNodes);
        if (parentNode != null) {
            listOfParents.add(parentNode);
        }
        if (node instanceof ActionNodeContainer actionContainer) {
            final var listOfParentsForActions = new ArrayList<>(listOfParents);
            listOfParentsForActions.add(node);
            final var actionParams = new ArrayList<>(pathParams);
            actionContainer.getActions().forEach(actionDef -> {
                final var resourceActionPath = path + "/" + resolvePathArgumentsName(actionDef.getQName(),
                    node.getQName(), modelContext);
                final var childPath = basePath + DATA + resourceActionPath;
                result.add(processActionPathEntity(actionDef, childPath, actionParams, moduleName,
                    refPath, deviceName, parentNode, listOfParentsForActions));
            });
        }
        final var childNodes = widthList((DataNodeContainer) node, width);
        for (final var childNode : childNodes) {
            if (childNode instanceof ListSchemaNode || childNode instanceof ContainerSchemaNode) {
                final var childParams = new ArrayList<>(pathParams);
                final var newRefPath = refPath + "_" + childNode.getQName().getLocalName();
                final var localName = resolvePathArgumentsName(childNode.getQName(), node.getQName(), modelContext);
                final var resourceDataPath = path + "/" + processPath(childNode, childParams, localName);
                final var newConfig = isConfig && childNode.isConfiguration();
                processChildNode(childNode, childParams, moduleName, result, resourceDataPath, newRefPath, newConfig,
                    node, listOfParents, nodeDepth + 1);
            }
        }
    }

    private static <T extends DataNodeContainer> DataSchemaNode getListOrContainerChildNode(final T node,
            final int width, final int depth, final int nodeDepth) {
        if (depth > 0 && nodeDepth + 2 > depth) {
            return null;
        }
        // Note: Since post using first container/list among children to prevent missing schema for ref error it
        // should be also limited by width here even if it means not generating POST at all
        final var childNodes = widthList(node, width);
        return childNodes.stream()
            .filter(n -> n instanceof ListSchemaNode || n instanceof ContainerSchemaNode)
            .findFirst().orElse(null);
    }

    private PathEntity processDataPathEntity(final SchemaNode node, final String resourcePath,
            final List<ParameterEntity> pathParams, final String moduleName, final String refPath,
            final boolean isConfig, final String fullName) {
        if (isConfig) {
            return new PathEntity(resourcePath,
                new PatchEntity(node, deviceName, moduleName, pathParams, refPath, fullName),
                new PutEntity(node, deviceName, moduleName, pathParams, refPath, fullName),
                new GetEntity(node, deviceName, moduleName, pathParams, refPath, true),
                new DeleteEntity(node, deviceName, moduleName, pathParams, refPath));
        } else {
            return new PathEntity(resourcePath,
                new GetEntity(node, deviceName, moduleName, pathParams, refPath, false));
        }
    }

    private PathEntity processTopPathEntity(final SchemaNode node, final String resourcePath,
            final List<ParameterEntity> pathParams, final String moduleName, final String refPath,
            final boolean isConfig, final String fullName, final SchemaNode childNode) {
        if (isConfig) {
            final var childNodeRefPath = refPath + "_" + childNode.getQName().getLocalName();
            var post = new PostEntity(childNode, deviceName, moduleName, pathParams, childNodeRefPath, node,
                List.of(), false);
            if (!((DataSchemaNode) childNode).isConfiguration()) {
                post = new PostEntity(node, deviceName, moduleName, pathParams, refPath, null, List.of(), false);
            }
            return new PathEntity(resourcePath, post,
                new PatchEntity(node, deviceName, moduleName, pathParams, refPath, fullName),
                new PutEntity(node, deviceName, moduleName, pathParams, refPath, fullName),
                new GetEntity(node, deviceName, moduleName, pathParams, refPath, true),
                new DeleteEntity(node, deviceName, moduleName, pathParams, refPath));
        } else {
            return new PathEntity(resourcePath,
                new GetEntity(node, deviceName, moduleName, pathParams, refPath, false));
        }
    }

    private static PathEntity processActionPathEntity(final SchemaNode node, final String resourcePath,
            final List<ParameterEntity> pathParams, final String moduleName, final String refPath,
            final String deviceName, final SchemaNode parentNode, final List<SchemaNode> parentNodes) {
        return new PathEntity(resourcePath,
            new PostEntity(node, deviceName, moduleName, pathParams, refPath, parentNode, parentNodes, false));
    }

    private static String processPath(final DataSchemaNode node, final List<ParameterEntity> pathParams,
            final String localName) {
        final var path = new StringBuilder();
        path.append(localName);
        final var parameters = pathParams.stream()
            .map(ParameterEntity::name)
            .collect(Collectors.toSet());

        if (node instanceof ListSchemaNode listSchemaNode) {
            var prefix = "=";
            var discriminator = 1;
            for (final var listKey : listSchemaNode.getKeyDefinition()) {
                final var keyName = listKey.getLocalName();
                var paramName = keyName;
                while (!parameters.add(paramName)) {
                    paramName = keyName + discriminator;
                    discriminator++;
                }

                final var pathParamIdentifier = prefix + "{" + paramName + "}";
                prefix = ",";
                path.append(pathParamIdentifier);

                final var description = listSchemaNode.findDataChildByName(listKey)
                    .flatMap(DataSchemaNode::getDescription).orElse(null);

                pathParams.add(new ParameterEntity(paramName, "path", true,
                    new ParameterSchemaEntity(getAllowedType(listSchemaNode, listKey), null), description));
            }
        }
        return path.toString();
    }

    private static String getAllowedType(final ListSchemaNode list, final QName key) {
        final var keyType = ((LeafSchemaNode) list.getDataChildByName(key)).getType();

        // see: https://datatracker.ietf.org/doc/html/rfc7950#section-4.2.4
        // see: https://swagger.io/docs/specification/data-models/data-types/
        // TODO: Java 21 use pattern matching for switch
        if (keyType instanceof Int8TypeDefinition) {
            return "integer";
        }
        if (keyType instanceof Int16TypeDefinition) {
            return "integer";
        }
        if (keyType instanceof Int32TypeDefinition) {
            return "integer";
        }
        if (keyType instanceof Int64TypeDefinition) {
            return "integer";
        }
        if (keyType instanceof Uint8TypeDefinition) {
            return "integer";
        }
        if (keyType instanceof Uint16TypeDefinition) {
            return "integer";
        }
        if (keyType instanceof Uint32TypeDefinition) {
            return "integer";
        }
        if (keyType instanceof Uint64TypeDefinition) {
            return "integer";
        }
        if (keyType instanceof DecimalTypeDefinition) {
            return "number";
        }
        if (keyType instanceof BooleanTypeDefinition) {
            return "boolean";
        }

        return "string";
    }
}
