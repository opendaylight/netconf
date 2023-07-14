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
import static org.opendaylight.restconf.openapi.model.builder.OperationBuilder.getTypeParentNode;
import static org.opendaylight.restconf.openapi.util.RestDocgenUtil.resolvePathArgumentsName;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Preconditions;
import com.google.common.collect.Range;
import java.io.IOException;
import java.time.format.DateTimeParseException;
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
import java.util.stream.StreamSupport;
import javax.ws.rs.core.UriInfo;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.openapi.model.Components;
import org.opendaylight.restconf.openapi.model.Info;
import org.opendaylight.restconf.openapi.model.OpenApiObject;
import org.opendaylight.restconf.openapi.model.Operation;
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

    private static final String API_VERSION = "1.0.0";
    private static final String OPEN_API_VERSION = "3.0.3";

    private final DefinitionGenerator jsonConverter = new DefinitionGenerator();
    private final DOMSchemaService schemaService;

    public static final String BASE_PATH = "/";
    public static final String MODULE_NAME_SUFFIX = "_module";
    private static final ObjectNode OPEN_API_BASIC_AUTH = JsonNodeFactory.instance.objectNode()
            .put("type", "http")
            .put("scheme", "basic");
    private static final ArrayNode SECURITY = JsonNodeFactory.instance.arrayNode()
            .add(JsonNodeFactory.instance.objectNode().set("basicAuth", JsonNodeFactory.instance.arrayNode()));

    protected BaseYangOpenApiGenerator(final @NonNull DOMSchemaService schemaService) {
        this.schemaService = requireNonNull(schemaService);
    }

    public OpenApiObject getAllModulesDoc(final UriInfo uriInfo, final DefinitionNames definitionNames) {
        final EffectiveModelContext schemaContext = schemaService.getGlobalContext();
        Preconditions.checkState(schemaContext != null);
        return getAllModulesDoc(uriInfo, Optional.empty(), schemaContext, Optional.empty(), "", definitionNames)
            .build();
    }

    public OpenApiObject.Builder getAllModulesDoc(final UriInfo uriInfo, final Optional<Range<Integer>> range,
            final EffectiveModelContext schemaContext, final Optional<String> deviceName, final String context,
            final DefinitionNames definitionNames) {
        final String schema = createSchemaFromUriInfo(uriInfo);
        final String host = createHostFromUriInfo(uriInfo);
        String name = "Controller";
        if (deviceName.isPresent()) {
            name = deviceName.orElseThrow();
        }

        final String title = name + " modules of RESTCONF";
        final OpenApiObject.Builder docBuilder = createOpenApiObjectBuilder(schema, host, BASE_PATH, title);
        docBuilder.paths(new HashMap<>());

        fillDoc(docBuilder, range, schemaContext, context, deviceName, definitionNames);

        // FIXME rework callers logic to make possible to return OpenApiObject from here
        return docBuilder;
    }

    public void fillDoc(final OpenApiObject.Builder docBuilder, final Optional<Range<Integer>> range,
            final EffectiveModelContext schemaContext, final String context, final Optional<String> deviceName,
            final DefinitionNames definitionNames) {
        final SortedSet<Module> modules = getSortedModules(schemaContext);
        final Set<Module> filteredModules;
        if (range.isPresent()) {
            filteredModules = filterByRange(modules, range.orElseThrow());
        } else {
            filteredModules = modules;
        }

        for (final Module module : filteredModules) {
            final String revisionString = module.getQNameModule().getRevision().map(Revision::toString).orElse(null);

            LOG.debug("Working on [{},{}]...", module.getName(), revisionString);

            getOpenApiSpec(module, context, deviceName, schemaContext, definitionNames, docBuilder, false);
        }
    }

    private static Set<Module> filterByRange(final SortedSet<Module> modules, final Range<Integer> range) {
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
        final OpenApiObject.Builder docBuilder = createOpenApiObjectBuilder(schema, host, basePath, module.getName());
        final DefinitionNames definitionNames = new DefinitionNames();
        return getOpenApiSpec(module, context, Optional.empty(), schemaContext, definitionNames, docBuilder, true);
    }

    public OpenApiObject getOpenApiSpec(final Module module, final String context, final Optional<String> deviceName,
            final EffectiveModelContext schemaContext, final DefinitionNames definitionNames,
            final OpenApiObject.Builder docBuilder, final boolean isForSingleModule) {
        try {
            final Map<String, Schema> schemas = jsonConverter.convertToSchemas(module, schemaContext,
                definitionNames, isForSingleModule);
            docBuilder.getComponents().schemas().putAll(schemas);
        } catch (final IOException e) {
            LOG.error("Exception occurred in DefinitionGenerator", e);
        }
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

                final ArrayNode pathParams = JsonNodeFactory.instance.arrayNode();
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
                addPaths(node, deviceName, moduleName, paths, pathParams, schemaContext, isConfig,
                    moduleName, definitionNames, resourcePathPart, context);
            }
        }

        final ArrayNode pathParams = JsonNodeFactory.instance.arrayNode();
        for (final RpcDefinition rpcDefinition : module.getRpcs()) {
            final String resolvedPath = getResourcePath("operations", context) + "/" + moduleName + ":"
                    + rpcDefinition.getQName().getLocalName();
            addOperations(rpcDefinition, moduleName, deviceName, paths, moduleName, definitionNames,
                resolvedPath, pathParams);
        }

        LOG.debug("Number of Paths found [{}]", paths.size());

        if (isForSingleModule) {
            docBuilder.paths(paths);
        } else {
            docBuilder.getPaths().putAll(paths);
        }

        return docBuilder.build();
    }

    private static void addRootPostLink(final Module module, final Optional<String> deviceName,
            final ArrayNode pathParams, final String resourcePath, final Map<String, Path> paths) {
        if (containsListOrContainer(module.getChildNodes())) {
            final String moduleName = module.getName();
            final String name = moduleName + MODULE_NAME_SUFFIX;
            final var postBuilder = new Path.Builder();
            postBuilder.post(buildPost("", name, "", moduleName, deviceName,
                    module.getDescription().orElse(""), pathParams));
            paths.put(resourcePath, postBuilder.build());
        }
    }

    public OpenApiObject.Builder createOpenApiObjectBuilder(final String schema, final String host,
            final String basePath, final String title) {
        final OpenApiObject.Builder docBuilder = new OpenApiObject.Builder();
        docBuilder.openapi(OPEN_API_VERSION);
        docBuilder.info(new Info.Builder().title(title).version(API_VERSION).build())
            .servers(List.of(new Server(schema + "://" + host + basePath)))
            .components(new Components(new HashMap<>(), new SecuritySchemes(OPEN_API_BASIC_AUTH)))
            .security(SECURITY);
        return docBuilder;
    }

    public abstract String getResourcePath(String resourceType, String context);

    private void addPaths(final DataSchemaNode node, final Optional<String> deviceName, final String moduleName,
            final Map<String, Path> paths, final ArrayNode parentPathParams, final EffectiveModelContext schemaContext,
            final boolean isConfig, final String parentName, final DefinitionNames definitionNames,
            final String resourcePathPart, final String context) {
        final String dataPath = getResourcePath("data", context) + "/" + resourcePathPart;
        LOG.debug("Adding path: [{}]", dataPath);
        final ArrayNode pathParams = JsonNodeFactory.instance.arrayNode().addAll(parentPathParams);
        Iterable<? extends DataSchemaNode> childSchemaNodes = Collections.emptySet();
        if (node instanceof ListSchemaNode || node instanceof ContainerSchemaNode) {
            final DataNodeContainer dataNodeContainer = (DataNodeContainer) node;
            childSchemaNodes = dataNodeContainer.getChildNodes();
        }
        paths.put(dataPath, operations(node, moduleName, deviceName, pathParams, isConfig, parentName,
                definitionNames));

        if (node instanceof ActionNodeContainer) {
            ((ActionNodeContainer) node).getActions().forEach(actionDef -> {
                final String operationsPath = getResourcePath("operations", context)
                    + "/" + resourcePathPart
                    + "/" + resolvePathArgumentsName(actionDef.getQName(), node.getQName(), schemaContext);
                addOperations(actionDef, moduleName, deviceName, paths, parentName, definitionNames, operationsPath,
                    pathParams);
            });
        }

        for (final DataSchemaNode childNode : childSchemaNodes) {
            if (childNode instanceof ListSchemaNode || childNode instanceof ContainerSchemaNode) {
                final String newParent = parentName + "_" + node.getQName().getLocalName();
                final String localName = resolvePathArgumentsName(childNode.getQName(), node.getQName(), schemaContext);
                final String newPathPart = resourcePathPart + "/" + createPath(childNode, pathParams, localName);
                final boolean newIsConfig = isConfig && childNode.isConfiguration();
                addPaths(childNode, deviceName, moduleName, paths, pathParams, schemaContext,
                    newIsConfig, newParent, definitionNames, newPathPart, context);
                pathParams.removeAll();
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
            final Optional<String> deviceName, final ArrayNode pathParams, final boolean isConfig,
            final String parentName, final DefinitionNames definitionNames) {
        final Path.Builder operationsBuilder = new Path.Builder();

        final String discriminator = definitionNames.getDiscriminator(node);
        final String nodeName = node.getQName().getLocalName();

        final String defName = parentName + "_" + nodeName + discriminator;
        final String defNameTop = parentName + "_" + nodeName + TOP + discriminator;
        final Operation get = buildGet(node, moduleName, deviceName, pathParams, defName, defNameTop, isConfig);
        operationsBuilder.get(get);

        if (isConfig) {
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
        }
        return operationsBuilder.build();
    }

    private String createPath(final DataSchemaNode schemaNode, final ArrayNode pathParams, final String localName) {
        final StringBuilder path = new StringBuilder();
        path.append(localName);
        final Set<String> parameters = StreamSupport.stream(pathParams.spliterator(), false)
            .map(p -> p.get("name").asText())
            .collect(Collectors.toSet());

        if (schemaNode instanceof ListSchemaNode) {
            String prefix = "=";
            int discriminator = 1;
            for (final QName listKey : ((ListSchemaNode) schemaNode).getKeyDefinition()) {
                final String keyName = listKey.getLocalName();
                String paramName = keyName;
                while (!parameters.add(paramName)) {
                    paramName = keyName + discriminator;
                    discriminator++;
                }

                final String pathParamIdentifier = prefix + "{" + paramName + "}";
                prefix = ",";

                path.append(pathParamIdentifier);

                final ObjectNode pathParam = JsonNodeFactory.instance.objectNode();
                pathParam.put("name", paramName);

                ((DataNodeContainer) schemaNode).findDataChildByName(listKey).flatMap(DataSchemaNode::getDescription)
                        .ifPresent(desc -> pathParam.put("description", desc));

                final ObjectNode typeParent = getTypeParentNode(pathParam);

                typeParent.put("type", "string");
                pathParam.put("in", "path");
                pathParam.put("required", true);

                pathParams.add(pathParam);
            }
        }
        return path.toString();
    }

    public SortedSet<Module> getSortedModules(final EffectiveModelContext schemaContext) {
        if (schemaContext == null) {
            return Collections.emptySortedSet();
        }

        final SortedSet<Module> sortedModules = new TreeSet<>((module1, module2) -> {
            int result = module1.getName().compareTo(module2.getName());
            if (result == 0) {
                result = Revision.compare(module1.getRevision(), module2.getRevision());
            }
            if (result == 0) {
                result = module1.getNamespace().compareTo(module2.getNamespace());
            }
            return result;
        });
        for (final Module m : schemaContext.getModules()) {
            if (m != null) {
                sortedModules.add(m);
            }
        }
        return sortedModules;
    }

    private static void addOperations(final OperationDefinition operDef, final String moduleName,
            final Optional<String> deviceName, final Map<String, Path> paths, final String parentName,
            final DefinitionNames definitionNames, final String resourcePath, final ArrayNode parentPathParams) {
        final var pathBuilder = new Path.Builder();
        pathBuilder.post(buildPostOperation(operDef, moduleName, deviceName, parentName, definitionNames,
            parentPathParams));
        paths.put(resourcePath, pathBuilder.build());
    }

    protected abstract void appendPathKeyValue(StringBuilder builder, Object value);

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
                    appendPathKeyValue(builder, entry.getValue());
                }
            } else {
                builder.append(name).append('/');
            }
        }
        return builder.toString();
    }
}
