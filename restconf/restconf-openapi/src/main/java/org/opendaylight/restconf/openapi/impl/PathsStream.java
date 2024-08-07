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
import java.util.stream.Collectors;
import org.opendaylight.restconf.openapi.jaxrs.OpenApiBodyWriter;
import org.opendaylight.restconf.openapi.model.DeleteEntity;
import org.opendaylight.restconf.openapi.model.GetEntity;
import org.opendaylight.restconf.openapi.model.GetRootEntity;
import org.opendaylight.restconf.openapi.model.ParameterEntity;
import org.opendaylight.restconf.openapi.model.ParameterSchemaEntity;
import org.opendaylight.restconf.openapi.model.PatchEntity;
import org.opendaylight.restconf.openapi.model.PathEntity;
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
    private static final String OPERATIONS = "operations";
    private static final String DATA = "data";

    private final Iterator<? extends Module> iterator;
    private final OpenApiBodyWriter writer;
    private final EffectiveModelContext modelContext;
    private final String deviceName;
    private final String urlPrefix;
    private final String basePath;
    private final boolean isForSingleModule;
    private final boolean includeDataStore;
    private final ByteArrayOutputStream stream;
    private final JsonGenerator generator;
    private final int width;
    private final int depth;

    private boolean hasRootPostLink;
    private boolean hasAddedDataStore;
    private Reader reader;
    private ReadableByteChannel channel;
    private boolean eof;

    public PathsStream(final EffectiveModelContext modelContext, final OpenApiBodyWriter writer,
            final String deviceName, final String urlPrefix, final boolean isForSingleModule,
            final boolean includeDataStore, final Iterator<? extends Module> iterator, final String basePath,
            final ByteArrayOutputStream stream, final JsonGenerator generator, final int width,
            final int depth) {
        this.iterator = iterator;
        this.writer = writer;
        this.modelContext = modelContext;
        this.isForSingleModule = isForSingleModule;
        this.deviceName = deviceName;
        this.urlPrefix = urlPrefix;
        this.includeDataStore = includeDataStore;
        this.basePath = basePath;
        this.stream = stream;
        this.generator = generator;
        this.width = width;
        this.depth = depth;
        hasRootPostLink = false;
        hasAddedDataStore = false;
    }

    @Override
    public int read() throws IOException {
        if (eof) {
            return -1;
        }
        if (reader == null) {
            generator.writeObjectFieldStart("paths");
            generator.flush();
            reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(stream.toByteArray()), StandardCharsets.UTF_8));
            stream.reset();
        }
        var read = reader.read();
        while (read == -1) {
            if (iterator.hasNext()) {
                reader = new BufferedReader(
                    new InputStreamReader(new PathStream(toPaths(iterator.next()), writer), StandardCharsets.UTF_8));
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
            generator.writeObjectFieldStart("paths");
            generator.flush();
            channel = Channels.newChannel(new ByteArrayInputStream(stream.toByteArray()));
            stream.reset();
        }
        var read = channel.read(ByteBuffer.wrap(array, off, len));
        while (read == -1) {
            if (iterator.hasNext()) {
                channel = Channels.newChannel(new PathStream(toPaths(iterator.next()), writer));
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

    private Deque<PathEntity> toPaths(final Module module) {
        final var result = new ArrayDeque<PathEntity>();
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
                processChildNode(node, pathParams, moduleName, result, path, nodeLocalName, isConfig, modelContext,
                    deviceName, basePath, null, List.of(), width, depth, 0);
            }
        }
        return result;
    }

    private static void processChildNode(final DataSchemaNode node, final List<ParameterEntity> pathParams,
            final String moduleName, final Deque<PathEntity> result, final String path, final String refPath,
            final boolean isConfig, final EffectiveModelContext modelContext, final String deviceName,
            final String basePath, final SchemaNode parentNode, final List<SchemaNode> parentNodes, final int width,
            final int depth, final int nodeDepth) {
        if (depth > 0 && nodeDepth + 1 > depth) {
            return;
        }
        final var resourcePath = basePath + DATA + path;
        final var fullName = resolveFullNameFromNode(node.getQName(), modelContext);
        final var firstChild = getListOrContainerChildNode((DataNodeContainer) node, width, depth, nodeDepth);
        if (firstChild != null && node instanceof ContainerSchemaNode) {
            result.add(processTopPathEntity(node, resourcePath, pathParams, moduleName, refPath, isConfig,
                fullName, firstChild, deviceName));
        } else {
            result.add(processDataPathEntity(node, resourcePath, pathParams, moduleName, refPath,
                isConfig, fullName, deviceName));
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
                final var childPath = basePath + OPERATIONS + resourceActionPath;
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
                    modelContext, deviceName, basePath, node, listOfParents, width, depth, nodeDepth + 1);
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

    private static PathEntity processDataPathEntity(final SchemaNode node, final String resourcePath,
            final List<ParameterEntity> pathParams, final String moduleName, final String refPath,
            final boolean isConfig, final String fullName, final String deviceName) {
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

    private static PathEntity processTopPathEntity(final SchemaNode node, final String resourcePath,
            final List<ParameterEntity> pathParams, final String moduleName, final String refPath,
            final boolean isConfig, final String fullName, final SchemaNode childNode, final String deviceName) {
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
