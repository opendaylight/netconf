/*
 * Copyright (c) 2014 Brocade Communications Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.impl;

import static java.util.Objects.requireNonNull;
import static org.opendaylight.restconf.openapi.model.builder.OperationBuilder.buildDelete;
import static org.opendaylight.restconf.openapi.model.builder.OperationBuilder.buildGet;
import static org.opendaylight.restconf.openapi.model.builder.OperationBuilder.buildPatch;
import static org.opendaylight.restconf.openapi.model.builder.OperationBuilder.buildPost;
import static org.opendaylight.restconf.openapi.model.builder.OperationBuilder.buildPostOperation;
import static org.opendaylight.restconf.openapi.model.builder.OperationBuilder.buildPut;
import static org.opendaylight.restconf.openapi.util.RestDocgenUtil.resolveFullNameFromNode;
import static org.opendaylight.restconf.openapi.util.RestDocgenUtil.resolvePathArgumentsName;

import com.google.common.base.Preconditions;
import java.io.IOException;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import javax.ws.rs.core.UriInfo;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.openapi.model.Operation;
import org.opendaylight.restconf.openapi.model.Parameter;
import org.opendaylight.restconf.openapi.model.Path;
import org.opendaylight.restconf.openapi.model.Schema;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.model.api.ActionNodeContainer;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.OperationDefinition;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BaseYangOpenApiGenerator {

    private static final Logger LOG = LoggerFactory.getLogger(BaseYangOpenApiGenerator.class);
    private static final String CONTROLLER_RESOURCE_NAME = "Controller";
    public static final String BASE_PATH = "/";
    public static final String MODULE_NAME_SUFFIX = "_module";
    public static final List<Map<String, List<String>>> SECURITY = List.of(Map.of("basicAuth", List.of()));

    private final DOMSchemaService schemaService;

    protected BaseYangOpenApiGenerator(final @NonNull DOMSchemaService schemaService) {
        this.schemaService = requireNonNull(schemaService);
    }

    public OpenApiInputStream getControllerModulesDoc(final UriInfo uriInfo) throws IOException {
        final var context = requireNonNull(schemaService.getGlobalContext());
        final var schema = createSchemaFromUriInfo(uriInfo);
        final var host = createHostFromUriInfo(uriInfo);
        final var title = "Controller modules of RESTCONF";
        final var url = schema + "://" + host + BASE_PATH;
        final var modules = context.getModules();
        return new OpenApiInputStream(context, title, url, SECURITY, CONTROLLER_RESOURCE_NAME, "",false, false,
            modules);
    }

    public OpenApiInputStream getApiDeclaration(final String module, final String revision, final UriInfo uriInfo)
            throws IOException {
        final EffectiveModelContext schemaContext = schemaService.getGlobalContext();
        Preconditions.checkState(schemaContext != null);
        return getApiDeclaration(module, revision, uriInfo, schemaContext, "", CONTROLLER_RESOURCE_NAME);
    }

    public OpenApiInputStream getApiDeclaration(final String moduleName, final String revision, final UriInfo uriInfo,
            final EffectiveModelContext schemaContext, final String urlPrefix, final @NonNull String deviceName)
            throws IOException {
        final Optional<Revision> rev;

        try {
            rev = Revision.ofNullable(revision);
        } catch (final DateTimeParseException e) {
            throw new IllegalArgumentException(e);
        }

        final var module = schemaContext.findModule(moduleName, rev).orElse(null);
        Preconditions.checkArgument(module != null,
                "Could not find module by name,revision: " + moduleName + "," + revision);

        final var schema = createSchemaFromUriInfo(uriInfo);
        final var host = createHostFromUriInfo(uriInfo);
        final var title = module.getName();
        final var url = schema + "://" + host + BASE_PATH;
        final var modules = List.of(module);
        return new OpenApiInputStream(schemaContext, title, url, SECURITY,  deviceName, urlPrefix, true, false,
            modules);
    }

    public String createHostFromUriInfo(final UriInfo uriInfo) {
        String portPart = "";
        final int port = uriInfo.getBaseUri().getPort();
        if (port != -1) {
            portPart = ":" + port;
        }
        return uriInfo.getBaseUri().getHost() + portPart;
    }

    public String createSchemaFromUriInfo(final UriInfo uriInfo) {
        return uriInfo.getBaseUri().getScheme();
    }

    public Map<String, Path> getPaths(final Module module, final String context, final String deviceName,
            final EffectiveModelContext schemaContext, final DefinitionNames definitionNames,
            final boolean isForSingleModule) {
        final Map<String, Path> paths = new HashMap<>();
        final String moduleName = module.getName();

        boolean hasAddRootPostLink = false;

        final Collection<? extends DataSchemaNode> dataSchemaNodes = module.getChildNodes();
        LOG.debug("child nodes size [{}]", dataSchemaNodes.size());
        for (final DataSchemaNode node : dataSchemaNodes) {
            if (node instanceof ListSchemaNode || node instanceof ContainerSchemaNode) {
                final boolean isConfig = node.isConfiguration();
                LOG.debug("Is Configuration node [{}] [{}]", isConfig, node.getQName().getLocalName());

                final String localName = moduleName + ":" + node.getQName().getLocalName();
                final String resourcePath  = getResourcePath("data", context);

                final List<Parameter> pathParams = new ArrayList<>();
                /*
                 * When there are two or more top container or list nodes
                 * whose config statement is true in module, make sure that
                 * only one root post link is added for this module.
                 */
                if (isConfig && isForSingleModule && !hasAddRootPostLink) {
                    LOG.debug("Has added root post link for module {}", moduleName);
                    addRootPostLink(module, deviceName, pathParams, resourcePath, paths);

                    hasAddRootPostLink = true;
                }
                final String resourcePathPart = createPath(node, pathParams, localName);
                addPaths(node, deviceName, moduleName, paths, pathParams, isConfig, schemaContext,
                    moduleName, definitionNames, resourcePathPart, context);
            }
        }

        for (final RpcDefinition rpcDefinition : module.getRpcs()) {
            final String resolvedPath = getResourcePath("operations", context) + "/" + moduleName + ":"
                    + rpcDefinition.getQName().getLocalName();
            paths.put(resolvedPath, buildPostPath(rpcDefinition, moduleName, deviceName, moduleName, definitionNames,
                List.of()));
        }

        LOG.debug("Number of Paths found [{}]", paths.size());

        return paths;
    }

    public Map<String, Schema> getSchemas(final Module module, final EffectiveModelContext schemaContext,
            final DefinitionNames definitionNames, final boolean isForSingleModule) {
        Map<String, Schema> schemas = new HashMap<>();
        try {
            schemas = DefinitionGenerator.convertToSchemas(module, schemaContext, definitionNames, isForSingleModule);
        } catch (final IOException e) {
            LOG.error("Exception occurred in DefinitionGenerator", e); // FIXME propagate exception
        }

        return schemas;
    }

    private static void addRootPostLink(final Module module, final String deviceName,
            final List<Parameter> pathParams, final String resourcePath, final Map<String, Path> paths) {
        final var childNode = getListOrContainerChildNode(module);
        if (childNode != null) {
            final String moduleName = module.getName();
            paths.put(resourcePath, new Path.Builder()
                .post(buildPost(childNode, null, moduleName, "", moduleName, deviceName,
                    module.getDescription().orElse(""), pathParams))
                .build());
        }
    }

    public abstract String getResourcePath(String resourceType, String context);

    private void addPaths(final DataSchemaNode node, final String deviceName, final String moduleName,
            final Map<String, Path> paths, final List<Parameter> parentPathParams,
            final boolean isConfig, final EffectiveModelContext schemaContext, final String parentName,
            final DefinitionNames definitionNames, final String resourcePathPart, final String context) {
        final String dataPath = getResourcePath("data", context) + "/" + resourcePathPart;
        LOG.debug("Adding path: [{}]", dataPath);
        final List<Parameter> pathParams = new ArrayList<>(parentPathParams);
        Iterable<? extends DataSchemaNode> childSchemaNodes = Collections.emptySet();
        if (node instanceof ListSchemaNode || node instanceof ContainerSchemaNode) {
            childSchemaNodes = ((DataNodeContainer) node).getChildNodes();
        }
        final String fullName = resolveFullNameFromNode(node.getQName(), schemaContext);
        paths.put(dataPath, operations(node, moduleName, deviceName, pathParams, isConfig, parentName, definitionNames,
            fullName));

        if (node instanceof ActionNodeContainer actionContainer) {
            actionContainer.getActions().forEach(actionDef -> {
                final String operationsPath = getResourcePath("operations", context)
                    + "/" + resourcePathPart
                    + "/" + resolvePathArgumentsName(actionDef.getQName(), node.getQName(), schemaContext);
                paths.put(operationsPath, buildPostPath(actionDef, moduleName, deviceName, parentName,
                    definitionNames, pathParams));
            });
        }

        for (final DataSchemaNode childNode : childSchemaNodes) {
            if (childNode instanceof ListSchemaNode || childNode instanceof ContainerSchemaNode) {
                final String newParent = parentName + "_" + node.getQName().getLocalName();
                final String localName = resolvePathArgumentsName(childNode.getQName(), node.getQName(), schemaContext);
                final String newPathPart = resourcePathPart + "/" + createPath(childNode, pathParams, localName);
                final boolean newIsConfig = isConfig && childNode.isConfiguration();
                addPaths(childNode, deviceName, moduleName, paths, pathParams, newIsConfig, schemaContext,
                    newParent, definitionNames, newPathPart, context);
                pathParams.clear();
                pathParams.addAll(parentPathParams);
            }
        }
    }

    private static Path operations(final DataSchemaNode node, final String moduleName,
            final String deviceName, final List<Parameter> pathParams, final boolean isConfig, final String parentName,
            final DefinitionNames definitionNames, final String fullName) {
        final Path.Builder operationsBuilder = new Path.Builder();

        final String discriminator = definitionNames.getDiscriminator(node);
        final String nodeName = node.getQName().getLocalName();

        final Operation get = buildGet(node, parentName, moduleName, deviceName, pathParams, isConfig);
        operationsBuilder.get(get);

        if (isConfig) {
            final Operation put = buildPut(node, parentName, moduleName, deviceName, pathParams, fullName);
            operationsBuilder.put(put);

            final Operation patch = buildPatch(node, parentName, moduleName, deviceName, pathParams, fullName);
            operationsBuilder.patch(patch);

            final Operation delete = buildDelete(node, moduleName, deviceName, pathParams);
            operationsBuilder.delete(delete);

            if (node instanceof ContainerSchemaNode container) {
                final var childNode = getListOrContainerChildNode(container);
                // we have to ensure that we are able to create POST payload containing the first container/list child
                if (childNode != null) {
                    final Operation post = buildPost(childNode, parentName, nodeName, discriminator, moduleName,
                        deviceName, node.getDescription().orElse(""), pathParams);
                    operationsBuilder.post(post);
                }
            }
        }
        return operationsBuilder.build();
    }

    private static <T extends DataNodeContainer> DataSchemaNode getListOrContainerChildNode(final T node) {
        return node.getChildNodes().stream()
            .filter(n -> n instanceof ListSchemaNode || n instanceof ContainerSchemaNode)
            .findFirst().orElse(null);
    }

    private static String createPath(final DataSchemaNode schemaNode, final List<Parameter> pathParams,
            final String localName) {
        final StringBuilder path = new StringBuilder();
        path.append(localName);
        final Set<String> parameters = pathParams.stream()
            .map(Parameter::name)
            .collect(Collectors.toSet());

        if (schemaNode instanceof ListSchemaNode listSchemaNode) {
            String prefix = "=";
            int discriminator = 1;
            for (final QName listKey : listSchemaNode.getKeyDefinition()) {
                final String keyName = listKey.getLocalName();
                String paramName = keyName;
                while (!parameters.add(paramName)) {
                    paramName = keyName + discriminator;
                    discriminator++;
                }

                final String pathParamIdentifier = prefix + "{" + paramName + "}";
                prefix = ",";
                path.append(pathParamIdentifier);

                final String description = listSchemaNode.findDataChildByName(listKey)
                    .flatMap(DataSchemaNode::getDescription).orElse(null);
                pathParams.add(new Parameter.Builder()
                    .name(paramName)
                    .schema(new Schema.Builder().type(getAllowedType(listSchemaNode, listKey)).build())
                    .in("path")
                    .required(true)
                    .description(description)
                    .build());
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

    public static SortedSet<Module> getSortedModules(final EffectiveModelContext schemaContext) {
        if (schemaContext == null) {
            return Collections.emptySortedSet();
        }

        final var sortedModules = new TreeSet<Module>((module1, module2) -> {
            int result = module1.getName().compareTo(module2.getName());
            if (result == 0) {
                result = Revision.compare(module1.getRevision(), module2.getRevision());
            }
            if (result == 0) {
                result = module1.getNamespace().compareTo(module2.getNamespace());
            }
            return result;
        });
        sortedModules.addAll(schemaContext.getModules());
        return sortedModules;
    }

    private static Path buildPostPath(final OperationDefinition operDef, final String moduleName,
            final String deviceName, final String parentName, final DefinitionNames definitionNames,
            final List<Parameter> parentPathParams) {
        return new Path.Builder()
            .post(buildPostOperation(operDef, moduleName, deviceName, parentName, definitionNames, parentPathParams))
            .build();
    }
}
