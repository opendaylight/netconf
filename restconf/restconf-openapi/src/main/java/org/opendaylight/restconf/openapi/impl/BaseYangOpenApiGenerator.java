/*
 * Copyright (c) 2014 Brocade Communications Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.openapi.impl;

import static java.util.Objects.requireNonNull;
import static org.opendaylight.restconf.openapi.model.builder.OperationBuilder.TOP;
import static org.opendaylight.restconf.openapi.model.builder.OperationBuilder.buildDelete;
import static org.opendaylight.restconf.openapi.model.builder.OperationBuilder.buildGet;
import static org.opendaylight.restconf.openapi.model.builder.OperationBuilder.buildPatch;
import static org.opendaylight.restconf.openapi.model.builder.OperationBuilder.buildPost;
import static org.opendaylight.restconf.openapi.model.builder.OperationBuilder.buildPostOperation;
import static org.opendaylight.restconf.openapi.model.builder.OperationBuilder.buildPut;
import static org.opendaylight.restconf.openapi.util.RestDocgenUtil.resolvePathArgumentsName;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Preconditions;
import com.google.common.collect.Range;
import java.io.IOException;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import javax.ws.rs.core.UriInfo;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.openapi.model.Components;
import org.opendaylight.restconf.openapi.model.Info;
import org.opendaylight.restconf.openapi.model.OpenApiObject;
import org.opendaylight.restconf.openapi.model.Operation;
import org.opendaylight.restconf.openapi.model.Parameter;
import org.opendaylight.restconf.openapi.model.Path;
import org.opendaylight.restconf.openapi.model.Schema;
import org.opendaylight.restconf.openapi.model.SecuritySchemes;
import org.opendaylight.restconf.openapi.model.Server;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.model.api.ActionNodeContainer;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.OperationDefinition;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BaseYangOpenApiGenerator {

    private static final Logger LOG = LoggerFactory.getLogger(BaseYangOpenApiGenerator.class);

    public static final String API_VERSION = "1.0.0";
    public static final String OPEN_API_VERSION = "3.0.3";
    public static final String BASE_PATH = "/";
    public static final String MODULE_NAME_SUFFIX = "_module";
    public static final ObjectNode OPEN_API_BASIC_AUTH = JsonNodeFactory.instance.objectNode()
        .put("type", "http")
        .put("scheme", "basic");
    public static final ArrayNode SECURITY = JsonNodeFactory.instance.arrayNode()
        .add(JsonNodeFactory.instance.objectNode().set("basicAuth", JsonNodeFactory.instance.arrayNode()));

    private final DefinitionGenerator jsonConverter = new DefinitionGenerator();
    private final DOMSchemaService schemaService;

    protected BaseYangOpenApiGenerator(final @NonNull DOMSchemaService schemaService) {
        this.schemaService = requireNonNull(schemaService);
    }

    public OpenApiObject getControllerModulesDoc(final UriInfo uriInfo, final DefinitionNames definitionNames) {
        final var context = requireNonNull(schemaService.getGlobalContext());
        final var schema = createSchemaFromUriInfo(uriInfo);
        final var host = createHostFromUriInfo(uriInfo);
        final var title = "Controller modules of RESTCONF";
        final var info = new Info(API_VERSION, title);
        final var servers = List.of(new Server(schema + "://" + host + BASE_PATH));

        final var paths = new HashMap<String, Path>();
        final var schemas = new HashMap<String, Schema>();
        for (final var module : getSortedModules(context)) {
            LOG.debug("Working on [{},{}]...", module.getName(), module.getQNameModule().getRevision().orElse(null));
            schemas.putAll(getSchemas(module, context, definitionNames, false));
            paths.putAll(getPaths(module, "", "Controller", context, definitionNames, false));
        }

        final var components = new Components(schemas, new SecuritySchemes(OPEN_API_BASIC_AUTH));
        return new OpenApiObject(OPEN_API_VERSION, info, servers, paths, components, SECURITY);
    }

    public static Set<Module> filterByRange(final SortedSet<Module> modules, final Range<Integer> range) {
        if (range.equals(Range.all())) {
            return modules;
        }
        final int begin = range.lowerEndpoint();
        final int end = range.upperEndpoint();

        Module firstModule = null;

        final Iterator<Module> iterator = modules.iterator();
        int counter = 0;
        while (iterator.hasNext() && counter < end) {
            final Module module = iterator.next();
            if (containsListOrContainer(module.getChildNodes()) || !module.getRpcs().isEmpty()) {
                if (counter == begin) {
                    firstModule = module;
                }
                counter++;
            }
        }

        if (iterator.hasNext()) {
            return modules.subSet(firstModule, iterator.next());
        } else {
            return modules.tailSet(firstModule);
        }
    }

    public OpenApiObject getApiDeclaration(final String module, final String revision, final UriInfo uriInfo) {
        final EffectiveModelContext schemaContext = schemaService.getGlobalContext();
        Preconditions.checkState(schemaContext != null);
        return getApiDeclaration(module, revision, uriInfo, schemaContext, "");
    }

    public OpenApiObject getApiDeclaration(final String moduleName, final String revision, final UriInfo uriInfo,
            final EffectiveModelContext schemaContext, final String context) {
        final Optional<Revision> rev;

        try {
            rev = Revision.ofNullable(revision);
        } catch (final DateTimeParseException e) {
            throw new IllegalArgumentException(e);
        }

        final Module module = schemaContext.findModule(moduleName, rev).orElse(null);
        Preconditions.checkArgument(module != null,
                "Could not find module by name,revision: " + moduleName + "," + revision);

        return getApiDeclaration(module, uriInfo, context, schemaContext);
    }

    public OpenApiObject getApiDeclaration(final Module module, final UriInfo uriInfo, final String context,
            final EffectiveModelContext schemaContext) {
        final String schema = createSchemaFromUriInfo(uriInfo);
        final String host = createHostFromUriInfo(uriInfo);

        return getOpenApiSpec(module, schema, host, BASE_PATH, context, schemaContext);
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

    public OpenApiObject getOpenApiSpec(final Module module, final String schema, final String host,
            final String basePath, final String context, final EffectiveModelContext schemaContext) {
        final var info = new Info(API_VERSION, module.getName());
        final var servers = List.of(new Server(schema + "://" + host + basePath));
        final var definitionNames = new DefinitionNames();
        final var schemas = getSchemas(module, schemaContext, definitionNames, true);
        final var components = new Components(schemas, new SecuritySchemes(OPEN_API_BASIC_AUTH));
        final var paths = getPaths(module, context, null, schemaContext, definitionNames, true);
        return new OpenApiObject(OPEN_API_VERSION, info, servers, paths, components, SECURITY);
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
            if (node.isConfiguration() && (node instanceof ListSchemaNode || node instanceof ContainerSchemaNode)) {
                LOG.debug("Is Configuration node [{}]",  node.getQName().getLocalName());

                final String localName = moduleName + ":" + node.getQName().getLocalName();
                final String resourcePath  = getResourcePath("data", context);

                final List<Parameter> pathParams = new ArrayList<>();
                /*
                 * When there are two or more top container or list nodes
                 * whose config statement is true in module, make sure that
                 * only one root post link is added for this module.
                 */
                if (isForSingleModule && !hasAddRootPostLink) {
                    LOG.debug("Has added root post link for module {}", moduleName);
                    addRootPostLink(module, deviceName, pathParams, resourcePath, paths);

                    hasAddRootPostLink = true;
                }
                final String resourcePathPart = createPath(node, pathParams, localName);
                addPaths(node, deviceName, moduleName, paths, pathParams, schemaContext,
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
            schemas = jsonConverter.convertToSchemas(module, schemaContext, definitionNames, isForSingleModule);
        } catch (final IOException e) {
            LOG.error("Exception occurred in DefinitionGenerator", e); // FIXME propagate exception
        }

        return schemas;
    }

    private static void addRootPostLink(final Module module, final String deviceName,
            final List<Parameter> pathParams, final String resourcePath, final Map<String, Path> paths) {
        if (containsListOrContainer(module.getChildNodes())) {
            final String moduleName = module.getName();
            final String name = moduleName + MODULE_NAME_SUFFIX;
            paths.put(resourcePath, new Path.Builder()
                .post(buildPost("", name, "", moduleName, deviceName, module.getDescription().orElse(""), pathParams))
                .build());
        }
    }

    public abstract String getResourcePath(String resourceType, String context);

    private void addPaths(final DataSchemaNode node, final String deviceName, final String moduleName,
            final Map<String, Path> paths, final List<Parameter> parentPathParams,
            final EffectiveModelContext schemaContext, final String parentName, final DefinitionNames definitionNames,
            final String resourcePathPart, final String context) {
        final String dataPath = getResourcePath("data", context) + "/" + resourcePathPart;
        LOG.debug("Adding path: [{}]", dataPath);
        final List<Parameter> pathParams = new ArrayList<>(parentPathParams);
        Iterable<? extends DataSchemaNode> childSchemaNodes = Collections.emptySet();
        if (node instanceof ListSchemaNode || node instanceof ContainerSchemaNode) {
            childSchemaNodes = ((DataNodeContainer) node).getChildNodes();
        }
        paths.put(dataPath, operations(node, moduleName, deviceName, pathParams, parentName, definitionNames));

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
            if (childNode.isConfiguration() && (childNode instanceof ListSchemaNode
                    || childNode instanceof ContainerSchemaNode)) {
                final String newParent = parentName + "_" + node.getQName().getLocalName();
                final String localName = resolvePathArgumentsName(childNode.getQName(), node.getQName(), schemaContext);
                final String newPathPart = resourcePathPart + "/" + createPath(childNode, pathParams, localName);
                addPaths(childNode, deviceName, moduleName, paths, pathParams, schemaContext,
                    newParent, definitionNames, newPathPart, context);
                pathParams.clear();
                pathParams.addAll(parentPathParams);
            }
        }
    }

    private static boolean containsListOrContainer(final Iterable<? extends DataSchemaNode> nodes) {
        for (final DataSchemaNode child : nodes) {
            if (child instanceof ListSchemaNode || child instanceof ContainerSchemaNode) {
                return true;
            }
        }
        return false;
    }

    private static Path operations(final DataSchemaNode node, final String moduleName,
            final String deviceName, final List<Parameter> pathParams, final String parentName,
            final DefinitionNames definitionNames) {
        final Path.Builder operationsBuilder = new Path.Builder();

        final String discriminator = definitionNames.getDiscriminator(node);
        final String nodeName = node.getQName().getLocalName();

        final String defName = parentName + "_" + nodeName + discriminator;
        final String defNameTop = parentName + "_" + nodeName + TOP + discriminator;
        final Operation get = buildGet(node, moduleName, deviceName, pathParams, defName, defNameTop);
        operationsBuilder.get(get);

        final Operation put = buildPut(parentName, nodeName, discriminator, moduleName, deviceName,
                node.getDescription().orElse(""), pathParams);
        operationsBuilder.put(put);

        final Operation patch = buildPatch(parentName, nodeName, moduleName, deviceName,
                node.getDescription().orElse(""), pathParams);
        operationsBuilder.patch(patch);

        final Operation delete = buildDelete(node, moduleName, deviceName, pathParams);
        operationsBuilder.delete(delete);

        final Operation post = buildPost(parentName, nodeName, discriminator, moduleName, deviceName,
                node.getDescription().orElse(""), pathParams);
        operationsBuilder.post(post);

        return operationsBuilder.build();
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
                    .schema(new Schema.Builder().type("string").build())
                    .in("path")
                    .required(true)
                    .description(description)
                    .build());
            }
        }
        return path.toString();
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

    public String generateUrlPrefixFromInstanceID(final YangInstanceIdentifier key, final String moduleName) {
        final StringBuilder builder = new StringBuilder();
        builder.append("/");
        if (moduleName != null) {
            builder.append(moduleName).append(':');
        }
        for (final PathArgument arg : key.getPathArguments()) {
            final String name = arg.getNodeType().getLocalName();
            if (arg instanceof NodeIdentifierWithPredicates nodeId) {
                for (final Entry<QName, Object> entry : nodeId.entrySet()) {
                    builder.deleteCharAt(builder.length() - 1).append("=").append(entry.getValue()).append('/');
                }
            } else {
                builder.append(name).append('/');
            }
        }
        return builder.toString();
    }
}
