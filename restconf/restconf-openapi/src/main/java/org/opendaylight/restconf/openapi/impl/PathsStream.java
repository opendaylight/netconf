/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.impl;

import static org.opendaylight.restconf.openapi.util.RestDocgenUtil.resolveFullNameFromNode;
import static org.opendaylight.restconf.openapi.util.RestDocgenUtil.resolvePathArgumentsName;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.stream.Collectors;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.restconf.openapi.jaxrs.OpenApiBodyWriter;
import org.opendaylight.restconf.openapi.model.DeleteEntity;
import org.opendaylight.restconf.openapi.model.GetEntity;
import org.opendaylight.restconf.openapi.model.OpenApiEntity;
import org.opendaylight.restconf.openapi.model.ParameterEntity;
import org.opendaylight.restconf.openapi.model.ParameterSchemaEntity;
import org.opendaylight.restconf.openapi.model.PatchEntity;
import org.opendaylight.restconf.openapi.model.PathEntity;
import org.opendaylight.restconf.openapi.model.PathsEntity;
import org.opendaylight.restconf.openapi.model.PostEntity;
import org.opendaylight.restconf.openapi.model.PutEntity;
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

public final class PathsStream extends InputStream {
    private final Collection<? extends Module> modules;
    private final OpenApiBodyWriter writer;
    private final EffectiveModelContext schemaContext;
    private final String deviceName;
    private final String urlPrefix;
    private final String basePath;
    private final boolean isForSingleModule;
    private final boolean includeDataStore;

    private static final String OPERATIONS = "operations";
    private static final String DATA = "data";
    private boolean hasRootPostLink;
    private boolean hasAddedDataStore;
    private Reader reader;

    public PathsStream(final EffectiveModelContext schemaContext, final OpenApiBodyWriter writer,
            final String deviceName, final String urlPrefix, final boolean isForSingleModule,
            final boolean includeDataStore, final Collection<? extends Module> modules, final String basePath) {
        this.modules = modules;
        this.writer = writer;
        this.schemaContext = schemaContext;
        this.isForSingleModule = isForSingleModule;
        this.deviceName = deviceName;
        this.urlPrefix = urlPrefix;
        this.includeDataStore = includeDataStore;
        this.basePath = basePath;
        hasRootPostLink = false;
        hasAddedDataStore = false;
    }

    @Override
    public int read() throws IOException {
        if (reader == null) {
            reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(writeNextEntity(new PathsEntity(toPaths()))),
                    StandardCharsets.UTF_8));
        }
        return reader.read();
    }

    @Override
    public int read(final byte @NonNull [] array, final int off, final int len) throws IOException {
        return super.read(array, off, len);
    }

    private byte[] writeNextEntity(final OpenApiEntity next) throws IOException {
        writer.writeTo(next, null, null, null, null, null, null);
        return writer.readFrom();
    }

    private Deque<PathEntity> toPaths() {
        final var result = new ArrayDeque<PathEntity>();
        for (final var module : modules) {
            if (includeDataStore && !hasAddedDataStore) {
                final var dataPath = basePath + DATA + urlPrefix;
                result.add(new PathEntity(dataPath, null, null, null,
                    new GetEntity(null, deviceName, "data", null, null, false),
                    null));
                final var operationsPath = basePath + OPERATIONS + urlPrefix;
                result.add(new PathEntity(operationsPath, null, null, null,
                    new GetEntity(null, deviceName, "operations", null, null, false),
                    null));
                hasAddedDataStore = true;
            }
            // RPC operations (via post) - RPCs have their own path
            for (final var rpc : module.getRpcs()) {
                final var localName = rpc.getQName().getLocalName();
                final var post = new PostEntity(rpc, deviceName, module.getName(), new ArrayList<>(), localName, null);
                final var resolvedPath = basePath + OPERATIONS + urlPrefix + "/" + module.getName() + ":" + localName;
                final var entity = new PathEntity(resolvedPath, post, null, null, null, null);
                result.add(entity);
            }
            for (final var node : module.getChildNodes()) {
                final var moduleName = module.getName();
                final boolean isConfig = node.isConfiguration();
                final var nodeLocalName = node.getQName().getLocalName();

                if (node instanceof ListSchemaNode || node instanceof ContainerSchemaNode) {
                    if (isConfig && !hasRootPostLink && isForSingleModule) {
                        final var resolvedPath = basePath + DATA + urlPrefix;
                        result.add(new PathEntity(resolvedPath, new PostEntity(node, deviceName, moduleName,
                            new ArrayList<>(), nodeLocalName, module), null, null, null, null));
                        hasRootPostLink = true;
                    }
                    //process first node
                    final var pathParams = new ArrayList<ParameterEntity>();
                    final var localName = moduleName + ":" + nodeLocalName;
                    final var path = urlPrefix + "/" + processPath(node, pathParams, localName);
                    processChildNode(node, pathParams, moduleName, result, path, nodeLocalName, isConfig, schemaContext,
                        deviceName, basePath, node);
                }
            }
        }
        return result;
    }

    private static void processChildNode(final DataSchemaNode node, final List<ParameterEntity> pathParams,
            final String moduleName, final Deque<PathEntity> result, final String path, final String refPath,
            final boolean isConfig, final EffectiveModelContext schemaContext, final String deviceName,
            final String basePath, final SchemaNode parentNode) {
        final var resourcePath = basePath + DATA + path;
        final var fullName = resolveFullNameFromNode(node.getQName(), schemaContext);
        final var firstChild = getListOrContainerChildNode((DataNodeContainer) node);
        if (firstChild != null && node instanceof ContainerSchemaNode) {
            result.add(processTopPathEntity(node, resourcePath, pathParams, moduleName, refPath, isConfig,
                fullName, firstChild, deviceName));
        } else {
            result.add(processDataPathEntity(node, resourcePath, pathParams, moduleName, refPath,
                isConfig, fullName, deviceName));
        }
        final var childNodes = ((DataNodeContainer) node).getChildNodes();
        if (node instanceof ActionNodeContainer actionContainer) {
            final var actionParams = new ArrayList<>(pathParams);
            actionContainer.getActions().forEach(actionDef -> {
                final var resourceActionPath = path + "/" + resolvePathArgumentsName(actionDef.getQName(),
                    node.getQName(), schemaContext);
                final var childPath = basePath + OPERATIONS + resourceActionPath;
                result.add(processRootAndActionPathEntity(actionDef, childPath, actionParams, moduleName,
                    refPath, deviceName, parentNode));
            });
        }
        for (final var childNode : childNodes) {
            final var childParams = new ArrayList<>(pathParams);
            final var newRefPath = refPath + "_" + childNode.getQName().getLocalName();
            if (childNode instanceof ListSchemaNode || childNode instanceof ContainerSchemaNode) {
                final var localName = resolvePathArgumentsName(childNode.getQName(), node.getQName(), schemaContext);
                final var resourceDataPath = path + "/" + processPath(childNode, childParams, localName);
                final var newConfig = isConfig && childNode.isConfiguration();
                processChildNode(childNode, childParams, moduleName, result, resourceDataPath, newRefPath, newConfig,
                    schemaContext, deviceName, basePath, node);
            }
        }
    }

    private static <T extends DataNodeContainer> DataSchemaNode getListOrContainerChildNode(final T node) {
        return node.getChildNodes().stream()
            .filter(n -> n instanceof ListSchemaNode || n instanceof ContainerSchemaNode)
            .findFirst().orElse(null);
    }

    private static PathEntity processDataPathEntity(final SchemaNode node, final String resourcePath,
            final List<ParameterEntity> pathParams, final String moduleName, final String refPath,
            final boolean isConfig, final String fullName, final String deviceName) {
        if (isConfig) {
            return new PathEntity(resourcePath, null,
                new PatchEntity(node, deviceName, moduleName, pathParams, refPath, fullName),
                new PutEntity(node, deviceName, moduleName, pathParams, refPath, fullName),
                new GetEntity(node, deviceName, moduleName, pathParams, refPath, true),
                new DeleteEntity(node, deviceName, moduleName, pathParams, refPath));
        } else {
            return new PathEntity(resourcePath, null, null, null,
                new GetEntity(node, deviceName, moduleName, pathParams, refPath, false), null);
        }
    }

    private static PathEntity processTopPathEntity(final SchemaNode node, final String resourcePath,
            final List<ParameterEntity> pathParams, final String moduleName, final String refPath,
            final boolean isConfig, final String fullName, final SchemaNode childNode, final String deviceName) {
        if (isConfig) {
            final var childNodeRefPath = refPath + "_" + childNode.getQName().getLocalName();
            var post = new PostEntity(childNode, deviceName, moduleName, pathParams, childNodeRefPath, node);
            if (!((DataSchemaNode) childNode).isConfiguration()) {
                post = new PostEntity(node, deviceName, moduleName, pathParams, refPath, null);
            }
            return new PathEntity(resourcePath, post,
                new PatchEntity(node, deviceName, moduleName, pathParams, refPath, fullName),
                new PutEntity(node, deviceName, moduleName, pathParams, refPath, fullName),
                new GetEntity(node, deviceName, moduleName, pathParams, refPath, true),
                new DeleteEntity(node, deviceName, moduleName, pathParams, refPath));
        } else {
            return new PathEntity(resourcePath, null, null, null,
                new GetEntity(node, deviceName, moduleName, pathParams, refPath, false), null);
        }
    }

    private static PathEntity processRootAndActionPathEntity(final SchemaNode node, final String resourcePath,
            final List<ParameterEntity> pathParams, final String moduleName, final String refPath,
            final String deviceName, final SchemaNode parentNode) {
        return new PathEntity(resourcePath,
            new PostEntity(node, deviceName, moduleName, pathParams, refPath, parentNode),
            null, null, null, null);
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
